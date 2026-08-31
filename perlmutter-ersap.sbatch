#!/bin/bash
#SBATCH --job-name=ersap-pipeline
#SBATCH --nodes=3                  # 1 monitor + 2 processing; adjust N+1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=64
#SBATCH --time=04:00:00
#SBATCH -q regular
#SBATCH -C cpu
#SBATCH -A amsc016
#SBATCH --output=ersap_%j.out
#SBATCH --error=ersap_%j.err
# Submit: sbatch perlmutter-ersap.sbatch
# Monitor: squeue -u gurjyan
# Cancel:  scancel $SLURM_JOB_ID

set -euo pipefail

# =============================================================================
# USER CONFIGURATION
# =============================================================================

ERSAP_HOME=/global/homes/g/gurjyan/work/ersap_installation/ersap_home
ERSAP_USER_DATA=/global/homes/g/gurjyan/work/ersap_user_data
DATA_DIR=/global/cfs/cdirs/amsc016/haidis/ersap-data
SERVICES_FILE="$ERSAP_USER_DATA/config/pet_services.yaml"
INPUT_DIR="$DATA_DIR/input"
OUTPUT_DIR="$DATA_DIR/output"
SESSION=test
MONITOR_PORT=19000        # 9000 is busy on Perlmutter
IMAGE=docker.io/gurjyan/pet-sro:v1


# =============================================================================
# NODE ASSIGNMENT
# Slurm runs this script on the first allocated node — that becomes the
# monitor node. Remaining nodes are processing nodes.
# =============================================================================

mapfile -t ALL_NODES < <(scontrol show hostnames "$SLURM_NODELIST")
MONITOR_NODE=${ALL_NODES[0]}
PROC_NODES=("${ALL_NODES[@]:1}")
MONITOR_IP=$(hostname -i | awk '{print $1}')
export ERSAP_MONITOR_FE="${MONITOR_IP}%${MONITOR_PORT}_java"

echo "============================================================"
echo "Job:              $SLURM_JOB_ID"
echo "SLURM_NODELIST:   $SLURM_NODELIST"
echo "Monitor node:     $MONITOR_NODE  ($MONITOR_IP)"
echo "Proc nodes:       ${PROC_NODES[*]}"
echo "ERSAP_MONITOR_FE: $ERSAP_MONITOR_FE"
echo "============================================================"


# =============================================================================
# STEP 1 — MONITOR NODE: start monitor FE DPE
# Verified: --host, --port, --session in DpeOptionsParser.java
# Verified: MONITOR_PORT default is ErsapConstants.MONITOR_PORT=9000;
#           19000 used here because 9000 is busy on Perlmutter
# =============================================================================

echo "[monitor] Starting monitor FE DPE on $MONITOR_IP:$MONITOR_PORT ..."

"$ERSAP_HOME/bin/j_dpe" \
    --host    "$MONITOR_IP" \
    --port    "$MONITOR_PORT" \
    --session "$SESSION" \
    &
DPE_PID=$!
sleep 10


# =============================================================================
# STEP 2 — MONITOR NODE: start Prometheus exporter
# Verified: all flags in PrometheusExporterConfig.java
# --monitor-port must match --port above
# =============================================================================

echo "[monitor] Starting Prometheus exporter on :9095 ..."

java -cp "$ERSAP_HOME/lib/*" \
    org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
    --monitor-host    "$MONITOR_IP" \
    --monitor-port    "$MONITOR_PORT" \
    --session         "$SESSION" \
    --prometheus-host 0.0.0.0 \
    --prometheus-port 9095 \
    --metric-prefix   ersap \
    --log-level       info \
    &
EXPORTER_PID=$!
sleep 5


# =============================================================================
# STEP 3 — MONITOR NODE: start Prometheus
# $HOME/prometheus must be installed on the login node before submitting.
# It is visible here because $HOME is the same global filesystem on all nodes.
# =============================================================================

echo "[monitor] Starting Prometheus on :9090 ..."

"$HOME/prometheus/prometheus" \
    --config.file="$HOME/prometheus/ersap.yml" \
    --storage.tsdb.path="$HOME/prometheus/data" \
    --storage.tsdb.retention.time=7d \
    --web.listen-address=0.0.0.0:9090 \
    &
PROM_PID=$!
sleep 5


# =============================================================================
# STEP 4 — MONITOR NODE: start Grafana
# $HOME/grafana must be installed on the login node before submitting.
# =============================================================================

echo "[monitor] Starting Grafana on :3000 ..."

"$HOME/grafana/bin/grafana-server" \
    --config="$HOME/grafana/conf/custom.ini" \
    --homepath="$HOME/grafana" \
    &
GRAFANA_PID=$!
sleep 10


# =============================================================================
# STEP 5 — MONITOR NODE: validate monitoring stack is ready
# =============================================================================

echo "[monitor] Validating monitoring stack ..."

# Exporter must be connected to monitor FE (value should be 1)
EXPORTER_UP=$(curl -sg http://localhost:9095/metrics \
    | grep '^ersap_prometheus_exporter_up' | awk '{print $2}')

if [[ "$EXPORTER_UP" != "1" ]]; then
    echo "ERROR: Prometheus exporter is not connected to monitor FE (got: $EXPORTER_UP)."
    echo "       Check that j_dpe started cleanly and MONITOR_PORT $MONITOR_PORT is free."
    exit 1
fi
echo "[monitor] Exporter connected. Prometheus and Grafana are up."
echo ""
echo "============================================================"
echo "  SSH TUNNEL — run this on your home computer:"
echo ""
echo "  ssh -N \\"
echo "    -L 3000:${MONITOR_NODE}:3000 \\"
echo "    -L 9090:${MONITOR_NODE}:9090 \\"
echo "    gurjyan@perlmutter.nersc.gov"
echo ""
echo "  Then open:"
echo "    Grafana:    http://localhost:3000  (admin / changeme)"
echo "    Prometheus: http://localhost:9090"
echo "============================================================"
echo ""


# =============================================================================
# STEP 6 — PROCESSING NODES: start one pipeline per node in parallel
# Each node runs the container with run local, which starts a local FE DPE
# and worker DPEs. ERSAP_MONITOR_FE tells them where to report metrics.
# Verified: --network=host lets the container reach MONITOR_IP over NERSC fabric.
# =============================================================================

echo "[processing] Launching pipelines on ${PROC_NODES[*]} ..."

# Write the per-node processing script to a temp file so srun can source it.
PROC_SCRIPT=$(mktemp /tmp/ersap_proc_XXXXXX.sh)
cat > "$PROC_SCRIPT" <<PROC
#!/bin/bash
set -euo pipefail
export ERSAP_MONITOR_FE="${ERSAP_MONITOR_FE}"
NODE_IP=\$(hostname -i | awk '{print \$1}')
echo "[\$(hostname)] Processing node starting. IP: \$NODE_IP"
echo "[\$(hostname)] ERSAP_MONITOR_FE: \$ERSAP_MONITOR_FE"

# Pull image if not already cached
podman-hpc pull $IMAGE

podman-hpc run --rm \\
    --network=host \\
    --group-add keep-groups \\
    -v $DATA_DIR:$DATA_DIR \\
    -e ERSAP_MONITOR_FE \\
    $IMAGE \\
    /bin/bash -c '
        source env.sh
        "\$ERSAP_HOME/bin/ersap-shell" <<EOF
set servicesFile $SERVICES_FILE
set inputDir $INPUT_DIR
set outputDir $OUTPUT_DIR
run local
EOF
    '
echo "[\$(hostname)] Pipeline finished."
PROC

chmod +x "$PROC_SCRIPT"

# Launch one srun per processing node, all in parallel
PROC_PIDS=()
for NODE in "${PROC_NODES[@]}"; do
    echo "[processing] Starting pipeline on $NODE ..."
    srun --nodes=1 --ntasks=1 --nodelist="$NODE" \
         bash "$PROC_SCRIPT" \
         &
    PROC_PIDS+=($!)
done

# Wait for all pipelines to complete
echo "[processing] Waiting for all pipelines to finish ..."
for PID in "${PROC_PIDS[@]}"; do
    wait "$PID"
done
echo "[processing] All pipelines finished."


# =============================================================================
# CLEANUP — stop monitor services gracefully
# =============================================================================

echo "[monitor] Shutting down monitoring services ..."
kill "$GRAFANA_PID"  2>/dev/null || true
kill "$PROM_PID"     2>/dev/null || true
kill "$EXPORTER_PID" 2>/dev/null || true
kill "$DPE_PID"      2>/dev/null || true
rm -f "$PROC_SCRIPT"

echo "[done] Job $SLURM_JOB_ID complete."
