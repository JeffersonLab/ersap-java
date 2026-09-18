#!/bin/bash
#SBATCH --job-name=ersap-monitor
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=2
#SBATCH --mem=8G
#SBATCH --time=30-00:00:00
#SBATCH --constraint=cron
#SBATCH --qos=workflow
#SBATCH --dependency=singleton
#SBATCH --account=amsc016         # ACCOUNT: change to your NERSC repo if different
#SBATCH --output=logs/monitor-%j.out
#SBATCH --error=logs/monitor-%j.err
#SBATCH --signal=B:TERM@60
# =============================================================================
# ERSAP monitor — workflow QOS / cron constraint, up to 30 days.
# Requires NERSC approval for workflow QOS. Lightweight: 2 CPUs / 8 GB.
# --dependency=singleton prevents a second copy starting under the same name.
# Prometheus history persists to disk across restarts (PROM_STORAGE=persistent).
#
# Submit from the repo root:
#     mkdir -p logs && sbatch slurm/monitor-longrun.slurm
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
: "${PROM_STORAGE:=persistent}"  # persist TSDB to $PROM_HOME/data across restarts
: "${MONITOR_TYPE:=longrun}"     # written into monitor-info.txt

export ERSAP_HOME

# shellcheck source=lib/monitor-stack.sh
source "${SLURM_SUBMIT_DIR}/slurm/lib/monitor-stack.sh"
