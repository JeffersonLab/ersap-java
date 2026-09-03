#!/bin/bash
#SBATCH --job-name=ersap-processor
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=128
#SBATCH --time=04:00:00
#SBATCH --constraint=cpu
#SBATCH --qos=regular
#SBATCH --account=amsc016          # ACCOUNT: change to your NERSC repo if different
#SBATCH --output=logs/processor-%j.out
#SBATCH --error=logs/processor-%j.err
#SBATCH --signal=B:TERM@60
# =============================================================================
# ERSAP processing pipeline on a single Perlmutter compute node.
#
# Runs one podman-hpc container that starts ersap-shell -> `run local`
# (local FE DPE + workers). Metrics are reported to an *already-running*
# monitor via ERSAP_MONITOR_FE.
#
# Point at the monitor in ONE of two ways before `sbatch`:
#   A) Source-based (recommended if you used perlmutter-ersap-monitor.slurm):
#        export MONITOR_ENV_FILE=$PWD/logs/monitor-<monitor-jobid>/monitor.env
#        sbatch perlmutter-ersap-processor.slurm
#   B) Direct:
#        export ERSAP_MONITOR_FE='<monitor-ip>%19000_java'
#        sbatch perlmutter-ersap-processor.slurm
#
# Submit from the repo root:
#     mkdir -p logs
#     sbatch perlmutter-ersap-processor.slurm
#
# Job exit code = pipeline exit code.
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURATION — override by exporting before `sbatch`
# -----------------------------------------------------------------------------
: "${MONITOR_ENV_FILE:=}"     # optional: sourced first to populate MONITOR_IP/PORT/ERSAP_MONITOR_FE
: "${ERSAP_MONITOR_FE:=}"     # required if MONITOR_ENV_FILE is unset
: "${MONITOR_IP:=}"           # optional; used for the reachability probe
: "${MONITOR_PORT:=19000}"    # matches monitor's j_dpe --port
: "${SESSION:=test}"

: "${IMAGE:=docker.io/gurjyan/pet-sro:v1}"
: "${DATA_DIR:=/global/cfs/cdirs/amsc016/haidis/ersap-data}"
: "${INPUT_DIR:=$DATA_DIR/input}"
: "${OUTPUT_DIR:=$DATA_DIR/output}"
# SERVICES_FILE is resolved INSIDE the container (single-quoted by default so
# $ERSAP_USER_DATA is expanded there, not on the host).
: "${SERVICES_FILE:='$ERSAP_USER_DATA/config/pet_services.yaml'}"
: "${PODMAN:=podman-hpc}"
: "${PIPELINE_PULL:=1}"       # 0 to skip `podman-hpc pull`
: "${MONITOR_TIMEOUT:=30}"    # seconds to wait for monitor reachability
: "${SHUTDOWN_TIMEOUT:=15}"   # graceful-stop budget for the container

# -----------------------------------------------------------------------------
# LOG LAYOUT
# -----------------------------------------------------------------------------
JOB_ID="${SLURM_JOB_ID:-manual-$$}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$REPO_DIR/logs/processor-${JOB_ID}"
mkdir -p "$LOG_DIR"

PIPELINE_LOG="$LOG_DIR/pipeline.log"
INFO_FILE="$LOG_DIR/processor-info.txt"
STATE_FILE="$LOG_DIR/state"

CONTAINER_NAME="ersap-pipeline-${JOB_ID}"

log() {
    printf '[%(%Y-%m-%dT%H:%M:%S%z)T] %s\n' -1 "$*"
}

# -----------------------------------------------------------------------------
# HOST DISCOVERY (compute-node)
# -----------------------------------------------------------------------------
PROC_HOST="$(hostname -s)"
PROC_HOST_FQDN="$(hostname -f 2>/dev/null || hostname)"
PROC_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
if [[ -z "$PROC_IP" ]]; then
    PROC_IP="$(hostname -i 2>/dev/null | awk '{print $1}')"
fi

NODELIST_RAW="${SLURM_JOB_NODELIST:-${SLURM_NODELIST:-}}"

# -----------------------------------------------------------------------------
# RESOLVE MONITOR ENDPOINT
# -----------------------------------------------------------------------------
if [[ -n "$MONITOR_ENV_FILE" ]]; then
    if [[ ! -r "$MONITOR_ENV_FILE" ]]; then
        log "ERROR: MONITOR_ENV_FILE not readable: $MONITOR_ENV_FILE"
        exit 1
    fi
    log "sourcing monitor descriptor: $MONITOR_ENV_FILE"
    # shellcheck disable=SC1090
    set -a; . "$MONITOR_ENV_FILE"; set +a
fi

if [[ -z "$ERSAP_MONITOR_FE" ]]; then
    log "ERROR: ERSAP_MONITOR_FE is not set."
    log "  Either export MONITOR_ENV_FILE=<path to monitor.env>"
    log "  or         export ERSAP_MONITOR_FE='<ip>%<port>_java'  before 'sbatch'."
    exit 1
fi

# Best-effort parse of ERSAP_MONITOR_FE=<ip>%<port>_java if MONITOR_IP/PORT
# were not supplied.
if [[ -z "$MONITOR_IP" ]] && [[ "$ERSAP_MONITOR_FE" =~ ^([^%]+)%([0-9]+)_ ]]; then
    MONITOR_IP="${BASH_REMATCH[1]}"
    MONITOR_PORT="${BASH_REMATCH[2]}"
fi

export ERSAP_MONITOR_FE

# -----------------------------------------------------------------------------
# SHUTDOWN HANDLER — pipeline container only
# -----------------------------------------------------------------------------
PIPELINE_PID=""
SHUTTING_DOWN=0

stop_pipeline() {
    if [[ -z "$PIPELINE_PID" ]]; then return 0; fi
    if kill -0 "$PIPELINE_PID" 2>/dev/null; then
        log "stopping pipeline container ${CONTAINER_NAME}"
        "$PODMAN" stop --time "$SHUTDOWN_TIMEOUT" "$CONTAINER_NAME" \
            >>"$PIPELINE_LOG" 2>&1 || true
    fi
    wait "$PIPELINE_PID" 2>/dev/null || true
}

shutdown() {
    local rc="${1:-0}"
    if (( SHUTTING_DOWN )); then return; fi
    SHUTTING_DOWN=1
    log "shutting down (exit ${rc})"
    stop_pipeline
    printf 'STATE=stopped\nEXIT=%s\nSTOP=%s\n' \
        "$rc" "$(date -Iseconds)" > "$STATE_FILE"
    log "shutdown complete"
    exit "$rc"
}

on_signal() {
    log "caught signal $1"
    shutdown 130
}

trap 'on_signal SIGINT'  INT
trap 'on_signal SIGTERM' TERM
trap 'on_signal SIGHUP'  HUP

# -----------------------------------------------------------------------------
# PRE-FLIGHT
# -----------------------------------------------------------------------------
missing=0
if [[ ! -d "$DATA_DIR" ]]; then
    log "ERROR: DATA_DIR not present on compute node: $DATA_DIR"; missing=1
fi
if ! command -v "$PODMAN" >/dev/null 2>&1; then
    log "ERROR: '$PODMAN' not found in PATH"; missing=1
fi
if (( missing )); then exit 1; fi

# Reachability probe: try to open a TCP connection to the monitor.
if [[ -n "$MONITOR_IP" ]]; then
    log "probing monitor at ${MONITOR_IP}:${MONITOR_PORT} (timeout ${MONITOR_TIMEOUT}s)"
    end=$(( SECONDS + MONITOR_TIMEOUT ))
    reachable=0
    while (( SECONDS < end )); do
        if (exec 3<>"/dev/tcp/${MONITOR_IP}/${MONITOR_PORT}") 2>/dev/null; then
            exec 3<&- 3>&-
            reachable=1
            break
        fi
        sleep 2
    done
    if (( reachable == 0 )); then
        log "ERROR: monitor ${MONITOR_IP}:${MONITOR_PORT} not reachable — is the monitor job running?"
        exit 1
    fi
    log "monitor reachable"
else
    log "WARN: MONITOR_IP unknown; skipping reachability probe"
fi

cd "$REPO_DIR"

# -----------------------------------------------------------------------------
# DISCOVERY FILES
# -----------------------------------------------------------------------------
{
    echo "ERSAP Processor — job ${JOB_ID}"
    echo "generated: $(date -Iseconds)"
    echo
    echo "[slurm]"
    echo "SLURM_JOB_ID           = ${SLURM_JOB_ID:-}"
    echo "SLURM_JOB_NAME         = ${SLURM_JOB_NAME:-}"
    echo "SLURM_JOB_NODELIST     = ${NODELIST_RAW}"
    echo "SLURM_JOB_PARTITION    = ${SLURM_JOB_PARTITION:-}"
    echo "SLURM_JOB_QOS          = ${SLURM_JOB_QOS:-}"
    echo "SLURM_JOB_ACCOUNT      = ${SLURM_JOB_ACCOUNT:-}"
    echo "SLURM_SUBMIT_HOST      = ${SLURM_SUBMIT_HOST:-}   # login/submit host, NOT the compute node"
    echo "SLURM_SUBMIT_DIR       = ${SLURM_SUBMIT_DIR:-}"
    echo
    echo "[host — allocated processor node]"
    echo "hostname (short)       = ${PROC_HOST}"
    echo "hostname -f (FQDN)     = ${PROC_HOST_FQDN}"
    echo "primary IP             = ${PROC_IP}"
    echo
    echo "[runtime]"
    echo "user                   = ${USER}"
    echo "cwd                    = $(pwd)"
    echo "start time             = $(date -Iseconds)"
    echo
    echo "[monitor endpoint — remote]"
    echo "MONITOR_ENV_FILE       = ${MONITOR_ENV_FILE:-<unset>}"
    echo "ERSAP_MONITOR_FE       = ${ERSAP_MONITOR_FE}"
    echo "MONITOR_IP             = ${MONITOR_IP:-<unknown>}"
    echo "MONITOR_PORT           = ${MONITOR_PORT}"
    echo
    echo "[pipeline]"
    echo "container name         = ${CONTAINER_NAME}"
    echo "image                  = ${IMAGE}"
    echo "data mount             = ${DATA_DIR}:${DATA_DIR}"
    echo "servicesFile           = ${SERVICES_FILE}"
    echo "inputDir               = ${INPUT_DIR}"
    echo "outputDir              = ${OUTPUT_DIR}"
    echo "session                = ${SESSION}"
    echo
    echo "[logs]"
    echo "SLURM stdout           = ${REPO_DIR}/logs/processor-${JOB_ID}.out"
    echo "SLURM stderr           = ${REPO_DIR}/logs/processor-${JOB_ID}.err"
    echo "pipeline log           = ${PIPELINE_LOG}"
    echo "status file            = ${STATE_FILE}"
} > "$INFO_FILE"

printf 'STATE=running\nSTART=%s\nJOB_ID=%s\n' \
    "$(date -Iseconds)" "$JOB_ID" > "$STATE_FILE"

# -----------------------------------------------------------------------------
# START PIPELINE
# -----------------------------------------------------------------------------
if [[ "$PIPELINE_PULL" == "1" ]]; then
    log "pulling image ${IMAGE}"
    if ! "$PODMAN" pull "$IMAGE" >>"$PIPELINE_LOG" 2>&1; then
        log "ERROR: 'podman-hpc pull ${IMAGE}' failed — see ${PIPELINE_LOG}"
        exit 1
    fi
fi

log "starting pipeline container ${CONTAINER_NAME} on ${PROC_HOST}"
log "reporting to ERSAP_MONITOR_FE=${ERSAP_MONITOR_FE}"

(
    "$PODMAN" run --rm \
        --name       "$CONTAINER_NAME" \
        --network=host \
        --group-add  keep-groups \
        -v           "$DATA_DIR:$DATA_DIR" \
        -e           "ERSAP_MONITOR_FE=$ERSAP_MONITOR_FE" \
        "$IMAGE" \
        /bin/bash -c "
            set -e
            source env.sh
            \"\$ERSAP_HOME/bin/ersap-shell\" <<ERSAP_EOF
set session $SESSION
set servicesFile $SERVICES_FILE
set inputDir $INPUT_DIR
set outputDir $OUTPUT_DIR
run local
ERSAP_EOF
        "
) >>"$PIPELINE_LOG" 2>&1 &
PIPELINE_PID=$!

cat <<BANNER

================================================================================
  ERSAP processor running
  Job ID:            ${JOB_ID}
  Processor node:    ${PROC_HOST_FQDN}  (${PROC_IP})
  Container:         ${CONTAINER_NAME}
  Image:             ${IMAGE}
  Reports to:        ${ERSAP_MONITOR_FE}
  Pipeline log:      ${PIPELINE_LOG}
  Info file:         ${INFO_FILE}
================================================================================

BANNER

# -----------------------------------------------------------------------------
# WAIT FOR PIPELINE — propagate its exit code as the job exit code.
# `wait` is signal-interruptible so the trap fires on scancel.
# -----------------------------------------------------------------------------
set +e
wait "$PIPELINE_PID"
rc=$?
set -e
log "pipeline exited with status ${rc}"
shutdown "$rc"
