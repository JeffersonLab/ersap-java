#!/bin/bash
#SBATCH --job-name=ersap-allinone
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=128
#SBATCH --time=04:00:00
#SBATCH --constraint=cpu
#SBATCH --qos=regular
#SBATCH --account=amsc016          # ACCOUNT: change to your NERSC repo if different
#SBATCH --output=logs/allinone-%j.out
#SBATCH --error=logs/allinone-%j.err
#SBATCH --signal=B:TERM@60
# =============================================================================
# ERSAP monitor + pipeline on a single Perlmutter compute node.
#
# Runs on ONE compute node:
#   Monitor stack (background):
#     1) j_dpe              (monitor FE DPE, ZeroMQ,  --port MONITOR_PORT)
#     2) PrometheusExporter (HTTP :EXPORTER_PORT /metrics)
#     3) prometheus         (HTTP :PROM_PORT)
#     4) grafana-server     (HTTP :GRAFANA_PORT)
#   Pipeline (foreground, primary workload):
#     5) podman-hpc container running `ersap-shell` -> `run local`,
#        reporting metrics to the local monitor via ERSAP_MONITOR_FE.
#
# Job exit code is the pipeline's exit code (0 on success). Monitor
# subprocesses are stopped cleanly when the pipeline finishes or the job
# is cancelled.
#
# Submit from the repo root:
#     mkdir -p logs && sbatch slurm/allinone.slurm
# Follow:
#     tail -f logs/allinone-<job-id>.out
#     cat  logs/allinone-<job-id>/monitor-info.txt
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURATION — override by exporting before `sbatch`
# -----------------------------------------------------------------------------
# Monitor
: "${ERSAP_HOME:=/global/homes/g/gurjyan/work/ersap_installation/ersap_home}"
: "${PROM_HOME:=$HOME/prometheus}"
: "${GRAFANA_HOME:=$HOME/grafana}"
: "${MONITOR_PORT:=19000}"    # ZeroMQ; 9000 is busy on Perlmutter
: "${EXPORTER_PORT:=9095}"
: "${PROM_PORT:=9090}"
: "${GRAFANA_PORT:=3000}"
: "${SESSION:=test}"
: "${STARTUP_TIMEOUT:=90}"
: "${SHUTDOWN_TIMEOUT:=20}"

# Pipeline
: "${IMAGE:=docker.io/gurjyan/pet-sro:v1}"
: "${DATA_DIR:=/global/cfs/cdirs/amsc016/haidis/ersap-data}"
: "${INPUT_DIR:=$DATA_DIR/input}"
: "${OUTPUT_DIR:=$DATA_DIR/output}"
# SERVICES_FILE is resolved INSIDE the container against the container's
# ERSAP_USER_DATA (set by env.sh). Single-quote by default to defer expansion.
: "${SERVICES_FILE:='$ERSAP_USER_DATA/config/pet_services.yaml'}"
: "${PODMAN:=podman-hpc}"
: "${PIPELINE_PULL:=1}"        # 0 to skip `podman-hpc pull`

export ERSAP_HOME

# -----------------------------------------------------------------------------
# LOG LAYOUT
# -----------------------------------------------------------------------------
JOB_ID="${SLURM_JOB_ID:-manual-$$}"
REPO_DIR="${SLURM_SUBMIT_DIR:?Submit with sbatch from the repository root}"
LOG_DIR="$REPO_DIR/logs/allinone-${JOB_ID}"
mkdir -p "$LOG_DIR"

DPE_LOG="$LOG_DIR/j_dpe.log"
EXPORTER_LOG="$LOG_DIR/exporter.log"
PROM_LOG="$LOG_DIR/prometheus.log"
GRAFANA_LOG="$LOG_DIR/grafana.log"
PIPELINE_LOG="$LOG_DIR/pipeline.log"
INFO_FILE="$LOG_DIR/monitor-info.txt"
ENV_FILE="$LOG_DIR/monitor.env"
STATE_FILE="$LOG_DIR/state"

CONTAINER_NAME="ersap-pipeline-${JOB_ID}"

log() {
    printf '[%(%Y-%m-%dT%H:%M:%S%z)T] %s\n' -1 "$*"
}

# -----------------------------------------------------------------------------
# HOST DISCOVERY (compute-node, not submit-host)
# -----------------------------------------------------------------------------
MONITOR_HOST="$(hostname -s)"
MONITOR_HOST_FQDN="$(hostname -f 2>/dev/null || hostname)"
MONITOR_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
if [[ -z "$MONITOR_IP" ]]; then
    MONITOR_IP="$(hostname -i 2>/dev/null | awk '{print $1}')"
fi
if [[ -z "$MONITOR_IP" || "$MONITOR_IP" == 127.* ]]; then
    log "ERROR: could not determine a routable IP for $(hostname)"
    exit 1
fi
export MONITOR_IP MONITOR_PORT
export ERSAP_MONITOR_FE="${MONITOR_IP}%${MONITOR_PORT}_java"

NODELIST_RAW="${SLURM_JOB_NODELIST:-${SLURM_NODELIST:-}}"
NODELIST_EXPANDED=""
if [[ -n "$NODELIST_RAW" ]] && command -v scontrol >/dev/null 2>&1; then
    NODELIST_EXPANDED="$(scontrol show hostnames "$NODELIST_RAW" | paste -sd ' ' -)"
fi

# -----------------------------------------------------------------------------
# SHUTDOWN HANDLER — pipeline first (podman-hpc stop), then monitor children
# -----------------------------------------------------------------------------
declare -A PIDS=()
PIPELINE_PID=""
SHUTTING_DOWN=0

stop_pipeline() {
    if [[ -z "$PIPELINE_PID" ]]; then return 0; fi
    if kill -0 "$PIPELINE_PID" 2>/dev/null; then
        log "stopping pipeline container ${CONTAINER_NAME}"
        "$PODMAN" stop --time 10 "$CONTAINER_NAME" >>"$PIPELINE_LOG" 2>&1 || true
    fi
    wait "$PIPELINE_PID" 2>/dev/null || true
}

stop_children() {
    local sig="${1:-TERM}" name pid
    for name in "${!PIDS[@]}"; do
        pid="${PIDS[$name]}"
        if kill -0 "$pid" 2>/dev/null; then
            log "sending SIG${sig} to ${name} (PID ${pid})"
            kill "-${sig}" "$pid" 2>/dev/null || true
        fi
    done
}

wait_children() {
    local timeout="${1:-15}" end=$(( SECONDS + timeout )) name alive
    while (( SECONDS < end )); do
        alive=0
        for name in "${!PIDS[@]}"; do
            if kill -0 "${PIDS[$name]}" 2>/dev/null; then alive=1; fi
        done
        (( alive == 0 )) && return 0
        sleep 1
    done
    return 1
}

shutdown() {
    local rc="${1:-0}"
    if (( SHUTTING_DOWN )); then return; fi
    SHUTTING_DOWN=1
    log "shutting down (exit ${rc})"
    stop_pipeline
    stop_children TERM
    if ! wait_children "$SHUTDOWN_TIMEOUT"; then
        log "monitor children did not exit within ${SHUTDOWN_TIMEOUT}s; sending SIGKILL"
        stop_children KILL
        wait_children 5 || true
    fi
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
# READINESS HELPERS
# -----------------------------------------------------------------------------
port_listening() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -H -tln 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}\$"
    else
        (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null
    fi
}

http_ok() {
    curl -sfL --max-time 3 -o /dev/null "$1"
}

wait_for() {
    local name="$1" pid="$2" timeout="$3"; shift 3
    local end=$(( SECONDS + timeout ))
    while (( SECONDS < end )); do
        if ! kill -0 "$pid" 2>/dev/null; then
            log "ERROR: ${name} exited during startup — see log"
            return 1
        fi
        if "$@" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    log "ERROR: ${name} did not become ready within ${timeout}s"
    return 1
}

# -----------------------------------------------------------------------------
# PRE-FLIGHT
# -----------------------------------------------------------------------------
missing=0
check_path() {
    local kind="$1" path="$2"
    case "$kind" in
        exec)  [[ -x "$path" ]] || { log "ERROR: missing executable: $path"; missing=1; } ;;
        dir)   [[ -d "$path" ]] || { log "ERROR: missing directory:  $path"; missing=1; } ;;
        file)  [[ -f "$path" ]] || { log "ERROR: missing file:       $path"; missing=1; } ;;
    esac
}
check_path exec "$ERSAP_HOME/bin/j_dpe"
check_path dir  "$ERSAP_HOME/lib"
check_path exec "$PROM_HOME/prometheus"
check_path file "$PROM_HOME/ersap.yml"
check_path exec "$GRAFANA_HOME/bin/grafana-server"
check_path file "$GRAFANA_HOME/conf/custom.ini"
check_path dir  "$DATA_DIR"
if ! command -v "$PODMAN" >/dev/null 2>&1; then
    log "ERROR: '$PODMAN' not found in PATH"; missing=1
fi
if (( missing )); then
    log "aborting: install/deploy the missing components (see perlmutter-setup/deploy.sh)"
    exit 1
fi
mkdir -p "$PROM_HOME/data"

cd "$REPO_DIR"

# -----------------------------------------------------------------------------
# MONITOR COMMANDS
# -----------------------------------------------------------------------------
DPE_CMD=(
    "$ERSAP_HOME/bin/j_dpe"
    --host    "$MONITOR_IP"
    --port    "$MONITOR_PORT"
    --session "$SESSION"
)

EXPORTER_CMD=(
    java -cp "$ERSAP_HOME/lib/*"
    org.jlab.epsci.ersap.util.prometheus.PrometheusExporter
    --monitor-host    "$MONITOR_IP"
    --monitor-port    "$MONITOR_PORT"
    --session         "$SESSION"
    --prometheus-host 0.0.0.0
    --prometheus-port "$EXPORTER_PORT"
    --metric-prefix   ersap
    --log-level       info
)

PROM_CMD=(
    "$PROM_HOME/prometheus"
    --config.file="$PROM_HOME/ersap.yml"
    --storage.tsdb.path="$PROM_HOME/data"
    --storage.tsdb.retention.time=7d
    --web.listen-address="0.0.0.0:$PROM_PORT"
)

GRAFANA_CMD=(
    "$GRAFANA_HOME/bin/grafana-server"
    --config="$GRAFANA_HOME/conf/custom.ini"
    --homepath="$GRAFANA_HOME"
)

# -----------------------------------------------------------------------------
# MONITOR STARTUP (order matters)
# -----------------------------------------------------------------------------
log "job ${JOB_ID} starting on ${MONITOR_HOST_FQDN} (${MONITOR_IP})"
log "SLURM_JOB_NODELIST = ${NODELIST_RAW}"

log "starting monitor FE DPE on ${MONITOR_IP}:${MONITOR_PORT}"
"${DPE_CMD[@]}" >"$DPE_LOG" 2>&1 &
PIDS[j_dpe]=$!
wait_for "j_dpe" "${PIDS[j_dpe]}" "$STARTUP_TIMEOUT" \
    port_listening "$MONITOR_PORT" || shutdown 1

log "starting PrometheusExporter on :${EXPORTER_PORT}"
"${EXPORTER_CMD[@]}" >"$EXPORTER_LOG" 2>&1 &
PIDS[exporter]=$!
wait_for "exporter" "${PIDS[exporter]}" "$STARTUP_TIMEOUT" \
    http_ok "http://127.0.0.1:${EXPORTER_PORT}/metrics" || shutdown 1

log "starting Prometheus on :${PROM_PORT}"
"${PROM_CMD[@]}" >"$PROM_LOG" 2>&1 &
PIDS[prometheus]=$!
wait_for "prometheus" "${PIDS[prometheus]}" "$STARTUP_TIMEOUT" \
    http_ok "http://127.0.0.1:${PROM_PORT}/-/ready" || shutdown 1

log "starting Grafana on :${GRAFANA_PORT}"
"${GRAFANA_CMD[@]}" >"$GRAFANA_LOG" 2>&1 &
PIDS[grafana]=$!
wait_for "grafana" "${PIDS[grafana]}" "$STARTUP_TIMEOUT" \
    http_ok "http://127.0.0.1:${GRAFANA_PORT}/api/health" || shutdown 1

# Confirm exporter has actually attached to j_dpe (metric value must be 1).
EXPORTER_UP="$(curl -sf --max-time 5 "http://127.0.0.1:${EXPORTER_PORT}/metrics" 2>/dev/null \
    | awk '/^ersap_prometheus_exporter_up([ {]|$)/{print $NF; exit}' || true)"
if ! awk -v value="$EXPORTER_UP" 'BEGIN { exit !(value != "" && value + 0 == 1) }'; then
    log "ERROR: exporter not connected to monitor FE (ersap_prometheus_exporter_up=${EXPORTER_UP:-<absent>})"
    shutdown 1
fi

for name in "${!PIDS[@]}"; do
    if ! kill -0 "${PIDS[$name]}" 2>/dev/null; then
        log "ERROR: ${name} exited before readiness completed"
        shutdown 1
    fi
done

# -----------------------------------------------------------------------------
# DISCOVERY FILES
# -----------------------------------------------------------------------------
{
    echo "ERSAP Monitor + Pipeline — job ${JOB_ID}"
    echo "generated: $(date -Iseconds)"
    echo
    echo "[slurm]"
    echo "SLURM_JOB_ID              = ${SLURM_JOB_ID:-}"
    echo "SLURM_JOB_NAME            = ${SLURM_JOB_NAME:-}"
    echo "SLURM_JOB_NODELIST        = ${NODELIST_RAW}"
    echo "SLURM_NODELIST (expanded) = ${NODELIST_EXPANDED}"
    echo "SLURM_JOB_PARTITION       = ${SLURM_JOB_PARTITION:-}"
    echo "SLURM_JOB_QOS             = ${SLURM_JOB_QOS:-}"
    echo "SLURM_JOB_ACCOUNT         = ${SLURM_JOB_ACCOUNT:-}"
    echo "SLURM_SUBMIT_HOST         = ${SLURM_SUBMIT_HOST:-}   # login/submit host, NOT the compute node"
    echo "SLURM_SUBMIT_DIR          = ${SLURM_SUBMIT_DIR:-}"
    echo
    echo "[host — allocated compute node]"
    echo "hostname (short)       = ${MONITOR_HOST}"
    echo "hostname -f (FQDN)     = ${MONITOR_HOST_FQDN}"
    echo "primary IP             = ${MONITOR_IP}"
    echo
    echo "[runtime]"
    echo "user                   = ${USER}"
    echo "cwd                    = $(pwd)"
    echo "start time             = $(date -Iseconds)"
    echo
    echo "[endpoints]"
    echo "j_dpe (ZeroMQ)         = ${MONITOR_IP}:${MONITOR_PORT}"
    echo "ERSAP_MONITOR_FE       = ${ERSAP_MONITOR_FE}"
    echo "PrometheusExporter     = http://${MONITOR_HOST}:${EXPORTER_PORT}/metrics"
    echo "Prometheus             = http://${MONITOR_HOST}:${PROM_PORT}"
    echo "Grafana                = http://${MONITOR_HOST}:${GRAFANA_PORT}"
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
    echo "[monitor processes]"
    echo "j_dpe        PID ${PIDS[j_dpe]}"
    echo "  cmd:  ${DPE_CMD[*]}"
    echo "  log:  ${DPE_LOG}"
    echo "exporter     PID ${PIDS[exporter]}"
    echo "  cmd:  ${EXPORTER_CMD[*]}"
    echo "  log:  ${EXPORTER_LOG}"
    echo "prometheus   PID ${PIDS[prometheus]}"
    echo "  cmd:  ${PROM_CMD[*]}"
    echo "  log:  ${PROM_LOG}"
    echo "grafana      PID ${PIDS[grafana]}"
    echo "  cmd:  ${GRAFANA_CMD[*]}"
    echo "  log:  ${GRAFANA_LOG}"
    echo
    echo "[logs]"
    echo "SLURM stdout           = ${REPO_DIR}/logs/allinone-${JOB_ID}.out"
    echo "SLURM stderr           = ${REPO_DIR}/logs/allinone-${JOB_ID}.err"
    echo "per-service log dir    = ${LOG_DIR}"
    echo "pipeline log           = ${PIPELINE_LOG}"
    echo "status file            = ${STATE_FILE}"
    echo "env  file              = ${ENV_FILE}"
    echo
    echo "[client — SSH tunnel from your home computer]"
    echo "ssh -N \\"
    echo "  -L ${GRAFANA_PORT}:${MONITOR_HOST}:${GRAFANA_PORT} \\"
    echo "  -L ${PROM_PORT}:${MONITOR_HOST}:${PROM_PORT} \\"
    echo "  ${USER}@perlmutter.nersc.gov"
} > "$INFO_FILE"

cat > "$ENV_FILE" <<EOF
MONITOR_JOB_ID=${JOB_ID}
MONITOR_HOST=${MONITOR_HOST}
MONITOR_HOST_FQDN=${MONITOR_HOST_FQDN}
MONITOR_IP=${MONITOR_IP}
MONITOR_PORT=${MONITOR_PORT}
EXPORTER_PORT=${EXPORTER_PORT}
PROM_PORT=${PROM_PORT}
GRAFANA_PORT=${GRAFANA_PORT}
ERSAP_MONITOR_FE=${ERSAP_MONITOR_FE}
MONITOR_LOG_DIR=${LOG_DIR}
PIPELINE_CONTAINER=${CONTAINER_NAME}
EOF

printf 'STATE=running\nSTART=%s\nJOB_ID=%s\n' \
    "$(date -Iseconds)" "$JOB_ID" > "$STATE_FILE"

cat <<BANNER

================================================================================
  ERSAP monitor successfully started
  Job ID:            ${JOB_ID}
  Monitor node:      ${MONITOR_HOST_FQDN}  (${MONITOR_IP})
  Monitor endpoint:  ${ERSAP_MONITOR_FE}
  Prometheus:        http://${MONITOR_HOST}:${PROM_PORT}
  Grafana:           http://${MONITOR_HOST}:${GRAFANA_PORT}   (admin/changeme)
  Processes:         j_dpe=${PIDS[j_dpe]}  exporter=${PIDS[exporter]}  prometheus=${PIDS[prometheus]}  grafana=${PIDS[grafana]}
  Log directory:     ${LOG_DIR}
================================================================================

BANNER

# -----------------------------------------------------------------------------
# PIPELINE STARTUP  (podman-hpc container, --network=host, reports to local monitor)
# -----------------------------------------------------------------------------
if [[ "$PIPELINE_PULL" == "1" ]]; then
    log "pulling image ${IMAGE}"
    if ! "$PODMAN" pull "$IMAGE" >>"$PIPELINE_LOG" 2>&1; then
        log "ERROR: 'podman-hpc pull ${IMAGE}' failed — see ${PIPELINE_LOG}"
        shutdown 1
    fi
fi

log "starting pipeline container ${CONTAINER_NAME} (image ${IMAGE})"
# Inner heredoc feeds commands to ersap-shell. SERVICES_FILE is single-quoted
# by default so `$ERSAP_USER_DATA` is resolved inside the container.
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

log "pipeline PID ${PIPELINE_PID}; log ${PIPELINE_LOG}"

cat <<BANNER

================================================================================
  ERSAP pipeline running
  Job ID:            ${JOB_ID}
  Compute node:      ${MONITOR_HOST_FQDN}
  Container:         ${CONTAINER_NAME}
  Image:             ${IMAGE}
  Reports to:        ${ERSAP_MONITOR_FE}
  Pipeline log:      ${PIPELINE_LOG}
  Info file:         ${INFO_FILE}
================================================================================

BANNER

# -----------------------------------------------------------------------------
# SUPERVISION LOOP
#   Pipeline exit         -> shutdown with the pipeline's exit code
#   Monitor child dies    -> shutdown 1 (infra failure)
# -----------------------------------------------------------------------------
log "entering supervision loop"
while true; do
    if ! kill -0 "$PIPELINE_PID" 2>/dev/null; then
        set +e
        wait "$PIPELINE_PID"
        rc=$?
        set -e
        log "pipeline exited with status ${rc}"
        shutdown "$rc"
    fi
    for name in "${!PIDS[@]}"; do
        pid="${PIDS[$name]}"
        if ! kill -0 "$pid" 2>/dev/null; then
            log "ERROR: monitor process '${name}' (PID ${pid}) exited unexpectedly"
            case "$name" in
                j_dpe)      tail -n 20 "$DPE_LOG"      2>/dev/null || true ;;
                exporter)   tail -n 20 "$EXPORTER_LOG" 2>/dev/null || true ;;
                prometheus) tail -n 20 "$PROM_LOG"     2>/dev/null || true ;;
                grafana)    tail -n 20 "$GRAFANA_LOG"  2>/dev/null || true ;;
            esac
            shutdown 1
        fi
    done
    # Poll every 5 s; `wait` allows signal traps to fire immediately.
    sleep 5 &
    wait $! 2>/dev/null || true
done
