#!/bin/bash
#SBATCH --job-name=ersap-monitor
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=64
#SBATCH --time=08:00:00
#SBATCH --constraint=cpu
#SBATCH --qos=regular
#SBATCH --account=amsc016         # ACCOUNT: change to your NERSC repo if different
#SBATCH --output=logs/monitor-%j.out
#SBATCH --error=logs/monitor-%j.err
#SBATCH --signal=B:TERM@60
# =============================================================================
# ERSAP monitor node — single Perlmutter compute node hosting:
#   1) j_dpe              (monitor FE DPE, ZeroMQ,  --port  MONITOR_PORT)
#   2) PrometheusExporter (Java class, HTTP        :EXPORTER_PORT /metrics)
#   3) prometheus         (HTTP                    :PROM_PORT)
#   4) grafana-server     (HTTP                    :GRAFANA_PORT)
#
# Pipeline (processing) nodes are launched elsewhere. They report to this
# monitor via  ERSAP_MONITOR_FE = "<MONITOR_IP>%<MONITOR_PORT>_java".
#
# Submit from the repo root:
#     mkdir -p logs
#     sbatch perlmutter-ersap-monitor.slurm
#
# Discover the allocated monitor node after submission:
#     squeue -j <job-id> -o "%.18i %.9P %.30j %.8u %.2t %.10M %.6D %R"
#     scontrol show job <job-id>
#     scontrol show hostnames "$(squeue -h -j <job-id> -o '%N')"
#     cat  logs/monitor-<job-id>/monitor-info.txt
#     tail -f logs/monitor-<job-id>.out
#
# Cancel:
#     scancel <job-id>
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURATION — override by exporting before `sbatch`
# -----------------------------------------------------------------------------
: "${ERSAP_HOME:=/global/homes/g/gurjyan/work/ersap_installation/ersap_home}"
: "${PROM_HOME:=$HOME/prometheus}"
: "${GRAFANA_HOME:=$HOME/grafana}"
: "${MONITOR_PORT:=19000}"    # ZeroMQ port for j_dpe (9000 is busy on Perlmutter)
: "${EXPORTER_PORT:=9095}"    # HTTP /metrics
: "${PROM_PORT:=9090}"        # Prometheus HTTP
: "${GRAFANA_PORT:=3000}"     # Grafana HTTP
: "${SESSION:=test}"
: "${STARTUP_TIMEOUT:=90}"    # seconds to wait for each service to become ready
: "${SHUTDOWN_TIMEOUT:=20}"   # graceful-stop budget before SIGKILL

export ERSAP_HOME

# -----------------------------------------------------------------------------
# LOG LAYOUT
# -----------------------------------------------------------------------------
JOB_ID="${SLURM_JOB_ID:-manual-$$}"
#REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${SLURM_SUBMIT_DIR:?Submit with sbatch from the repository root}"
LOG_DIR="$REPO_DIR/logs/monitor-${JOB_ID}"
mkdir -p "$LOG_DIR"

DPE_LOG="$LOG_DIR/j_dpe.log"
EXPORTER_LOG="$LOG_DIR/exporter.log"
PROM_LOG="$LOG_DIR/prometheus.log"
GRAFANA_LOG="$LOG_DIR/grafana.log"
INFO_FILE="$LOG_DIR/monitor-info.txt"
ENV_FILE="$LOG_DIR/monitor.env"
STATE_FILE="$LOG_DIR/state"

log() {
    printf '[%(%Y-%m-%dT%H:%M:%S%z)T] %s\n' -1 "$*"
}

# -----------------------------------------------------------------------------
# HOST DISCOVERY (runs on the allocated compute node, NOT on the submit host)
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
# SHUTDOWN HANDLER — only touches PIDs we started ourselves
# -----------------------------------------------------------------------------
declare -A PIDS=()
SHUTTING_DOWN=0

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
    local timeout="${1:-15}"
    local end=$(( SECONDS + timeout ))
    local name alive

    while (( SECONDS < end )); do
        alive=0
        for name in "${!PIDS[@]}"; do
            if kill -0 "${PIDS[$name]}" 2>/dev/null; then
                alive=1
            fi
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
    stop_children TERM
    if ! wait_children "$SHUTDOWN_TIMEOUT"; then
        log "children did not exit within ${SHUTDOWN_TIMEOUT}s; sending SIGKILL"
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
if (( missing )); then
    log "aborting: install/deploy the missing components (see perlmutter-setup/deploy.sh)"
    exit 1
fi
#mkdir -p "$PROM_HOME/data"
PROM_DATA_DIR="$(mktemp -d "/dev/shm/ersap-prometheus-${SLURM_JOB_ID}.XXXXXX")"
log "Prometheus data directory: $PROM_DATA_DIR"

cd "$REPO_DIR"

# -----------------------------------------------------------------------------
# COMMANDS
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
    #    --storage.tsdb.path="$PROM_HOME/data"
    --storage.tsdb.path="$PROM_DATA_DIR"
    --storage.tsdb.retention.time=7d
    --web.listen-address="0.0.0.0:$PROM_PORT"
)

GRAFANA_CMD=(
    "$GRAFANA_HOME/bin/grafana-server"
    --config="$GRAFANA_HOME/conf/custom.ini"
    --homepath="$GRAFANA_HOME"
)

# -----------------------------------------------------------------------------
# STARTUP  (order matters: exporter needs j_dpe; prometheus scrapes exporter)
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
if [[ "$EXPORTER_UP" != "1" ]]; then
    log "ERROR: exporter not connected to monitor FE (ersap_prometheus_exporter_up=${EXPORTER_UP:-<absent>})"
    shutdown 1
fi

# Final liveness check — no process may have died during startup.
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
    echo "ERSAP Monitor Node — job ${JOB_ID}"
    echo "generated: $(date -Iseconds)"
    echo
    echo "[slurm]"
    echo "SLURM_JOB_ID           = ${SLURM_JOB_ID:-}"
    echo "SLURM_JOB_NAME         = ${SLURM_JOB_NAME:-}"
    echo "SLURM_JOB_NODELIST     = ${NODELIST_RAW}"
    echo "SLURM_NODELIST (expanded) = ${NODELIST_EXPANDED}"
    echo "SLURM_JOB_PARTITION    = ${SLURM_JOB_PARTITION:-}"
    echo "SLURM_JOB_QOS          = ${SLURM_JOB_QOS:-}"
    echo "SLURM_JOB_ACCOUNT      = ${SLURM_JOB_ACCOUNT:-}"
    echo "SLURM_SUBMIT_HOST      = ${SLURM_SUBMIT_HOST:-}   # login/submit host, NOT the monitor"
    echo "SLURM_SUBMIT_DIR       = ${SLURM_SUBMIT_DIR:-}"
    echo
    echo "[host — allocated monitor compute node]"
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
    echo "[configuration]"
    echo "ERSAP_HOME             = ${ERSAP_HOME}"
    echo "PROM_HOME              = ${PROM_HOME}"
    echo "GRAFANA_HOME           = ${GRAFANA_HOME}"
    echo "Prometheus config      = ${PROM_HOME}/ersap.yml"
    echo "Grafana config         = ${GRAFANA_HOME}/conf/custom.ini"
    echo "Session                = ${SESSION}"
    echo
    echo "[processes]"
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
    echo "SLURM stdout           = ${REPO_DIR}/logs/monitor-${JOB_ID}.out"
    echo "SLURM stderr           = ${REPO_DIR}/logs/monitor-${JOB_ID}.err"
    echo "per-service log dir    = ${LOG_DIR}"
    echo "status file            = ${STATE_FILE}"
    echo "env  file              = ${ENV_FILE}"
    echo
    echo "[client — SSH tunnel from your home computer]"
    echo "ssh -N \\"
    echo "  -L ${GRAFANA_PORT}:${MONITOR_HOST}:${GRAFANA_PORT} \\"
    echo "  -L ${PROM_PORT}:${MONITOR_HOST}:${PROM_PORT} \\"
    echo "  ${USER}@perlmutter.nersc.gov"
    echo
    echo "[processing nodes]"
    echo "Set on each pipeline node before launching:"
    echo "  export ERSAP_MONITOR_FE='${ERSAP_MONITOR_FE}'"
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
  Info file:         ${INFO_FILE}
  Env  file:         ${ENV_FILE}
  Status file:       ${STATE_FILE}
================================================================================

BANNER

# -----------------------------------------------------------------------------
# SUPERVISION LOOP — exit if any monitored process dies
# -----------------------------------------------------------------------------
log "entering supervision loop"
while true; do
    for name in "${!PIDS[@]}"; do
        pid="${PIDS[$name]}"
        if ! kill -0 "$pid" 2>/dev/null; then
            log "ERROR: process '${name}' (PID ${pid}) exited unexpectedly"
            case "$name" in
                j_dpe)      tail -n 20 "$DPE_LOG"      2>/dev/null || true ;;
                exporter)   tail -n 20 "$EXPORTER_LOG" 2>/dev/null || true ;;
                prometheus) tail -n 20 "$PROM_LOG"     2>/dev/null || true ;;
                grafana)    tail -n 20 "$GRAFANA_LOG"  2>/dev/null || true ;;
            esac
            shutdown 1
        fi
    done
    # Use `wait -n` so the loop wakes immediately on child exit or signal,
    # not only on the 5 s poll.
    sleep 5 &
    wait $! 2>/dev/null || true
done
