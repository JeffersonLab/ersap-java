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
# ERSAP monitor — exclusive cpu node, up to 8 h.
# Hosts j_dpe + PrometheusExporter + Prometheus + Grafana.
# Pipeline nodes launched elsewhere report via ERSAP_MONITOR_FE.
#
# Submit from the repo root:
#     mkdir -p logs && sbatch slurm/monitor.slurm
# Follow:
#     tail -f logs/monitor-<job-id>.out
#     cat  logs/monitor-<job-id>/monitor-info.txt
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
: "${PROM_STORAGE:=tmpfs}"    # short job: RAM-backed, no persistence across restarts
: "${MONITOR_TYPE:=regular}"  # written into monitor-info.txt

export ERSAP_HOME

# shellcheck source=lib/monitor-stack.sh
source "${SLURM_SUBMIT_DIR}/slurm/lib/monitor-stack.sh"
