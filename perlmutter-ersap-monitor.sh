#!/usr/bin/env bash
# =============================================================================
# ERSAP Pipeline Monitoring on Perlmutter (NERSC)
# Verified against ERSAP source: ErsapConstants, PrometheusExporterConfig,
# DpeOptionsParser, RunCommand, MonitorMetricParser, ExporterSelfMetrics
#
# TOPOLOGY
#   Home ──SSH tunnel──► perlmutter.nersc.gov (login, port-forward only)
#                                │
#                    ┌───────────┴────────────┐
#                    │                        │
#             Monitor Node             Processing Nodes (1-N)
#             nidMMMMMM                nidXXXXXX ... nidYYYYYY
#             │                        │
#             ├── j_dpe :19000         └── container (--network=host)
#             │   (monitor FE DPE)         ├── FE DPE  (run local)
#             ├── PrometheusExporter        └── worker DPEs
#             │   :9095                         report to monitor FE
#             ├── prometheus :9090              via ERSAP_MONITOR_FE
#             └── grafana :3000
#
# Each processing node runs one pipeline (FE DPE + workers via run local).
# All pipeline FE DPEs report to the single monitor FE DPE via ERSAP_MONITOR_FE.
# PrometheusExporter subscribes to the monitor FE and serves /metrics.
# Prometheus scrapes the exporter. Grafana visualises Prometheus.
# =============================================================================


# =============================================================================
# SHARED — adjust these once before starting
# =============================================================================

ERSAP_HOME=/global/homes/g/gurjyan/work/ersap_installation/ersap_home
ERSAP_USER_DATA=/global/homes/g/gurjyan/work/ersap_user_data
MONITOR_PORT=19000          # 9000 is busy on Perlmutter; any free high port works
NERSC_USER=gurjyan
SLURM_ACCOUNT=amsc016
DATA_DIR=/global/cfs/cdirs/amsc016/haidis/ersap-data
SERVICES_FILE='$ERSAP_USER_DATA/config/pet_services.yaml'   # resolved inside container


# =============================================================================
# STEP 1 — LOGIN NODE: allocate Slurm nodes
# =============================================================================
# Request 1 monitor node + N processing nodes in one allocation.
# Adjust --nodes, --time, and -A to your needs.

salloc --nodes=3 --ntasks-per-node=8 \
       --time=04:00:00 -q interactive -C cpu -A "$SLURM_ACCOUNT"

# After allocation, record all node names:
echo "All nodes: $SLURM_NODELIST"           # e.g. nid[001234-001236]

# Assign roles — substitute real hostnames from $SLURM_NODELIST
export MONITOR_NODE=nid001234               # <-- set this
export PROC_NODES=(nid001235 nid001236)     # <-- set these


# =============================================================================
# STEP 2 — MONITOR NODE: open tmux and get IP
# =============================================================================

ssh "$MONITOR_NODE"

tmux new -s ersap-monitor
# Extra windows: Ctrl-b c  |  Navigate: Ctrl-b n / Ctrl-b p

# Run in every monitor-node window:
export ERSAP_HOME=/global/homes/g/gurjyan/work/ersap_installation/ersap_home
export MONITOR_PORT=19000
export MONITOR_IP=$(hostname -i | awk '{print $1}')
export ERSAP_MONITOR_FE="${MONITOR_IP}%${MONITOR_PORT}_java"
echo "Monitor node: $(hostname)  IP: $MONITOR_IP"
echo "ERSAP_MONITOR_FE: $ERSAP_MONITOR_FE"


# =============================================================================
# STEP 3 — MONITOR NODE, Window 1: monitor FE DPE
# =============================================================================
# Verified: --host, --port, --session in DpeOptionsParser.java
# Verified: MONITOR_PORT constant is 9000; 19000 used here as 9000 is busy

$ERSAP_HOME/bin/j_dpe \
  --host    "$MONITOR_IP" \
  --port    "$MONITOR_PORT" \
  --session test


# =============================================================================
# STEP 4 — MONITOR NODE, Window 2: Prometheus exporter
# =============================================================================
# Verified: all flags in PrometheusExporterConfig.java
# --monitor-port must equal --port used in Step 3
# --session test   : exact session; use '*' for all sessions

java -cp "$ERSAP_HOME/lib/*" \
  org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
  --monitor-host    "$MONITOR_IP" \
  --monitor-port    "$MONITOR_PORT" \
  --session         test \
  --prometheus-host 0.0.0.0 \
  --prometheus-port 9095 \
  --metric-prefix   ersap \
  --log-level       info


# =============================================================================
# STEP 5 — MONITOR NODE, Window 3: Prometheus (user-writable, no sudo)
# =============================================================================

# --- Install once (skip if already installed) ---
PROM_VER=2.53.0
wget -q -P "$HOME" \
  https://github.com/prometheus/prometheus/releases/download/v${PROM_VER}/prometheus-${PROM_VER}.linux-amd64.tar.gz
tar -C "$HOME" -xzf "$HOME/prometheus-${PROM_VER}.linux-amd64.tar.gz"
mv "$HOME/prometheus-${PROM_VER}.linux-amd64" "$HOME/prometheus"
mkdir -p "$HOME/prometheus/data"

cat > "$HOME/prometheus/ersap.yml" <<'EOF'
global:
  scrape_interval:     15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "ersap-monitor"
    metrics_path: /metrics
    static_configs:
      - targets: ["localhost:9095"]
EOF

# --- Start ---
"$HOME/prometheus/prometheus" \
  --config.file="$HOME/prometheus/ersap.yml" \
  --storage.tsdb.path="$HOME/prometheus/data" \
  --storage.tsdb.retention.time=7d \
  --web.listen-address=0.0.0.0:9090


# =============================================================================
# STEP 6 — MONITOR NODE, Window 4: Grafana (user-writable, no sudo)
# =============================================================================

# --- Install once (skip if already installed) ---
GRAF_VER=11.1.0
wget -q -P "$HOME" \
  https://dl.grafana.com/oss/release/grafana-${GRAF_VER}.linux-amd64.tar.gz
tar -C "$HOME" -xzf "$HOME/grafana-${GRAF_VER}.linux-amd64.tar.gz"
mv "$HOME/grafana-v${GRAF_VER}" "$HOME/grafana"
mkdir -p "$HOME/grafana/{data,logs,plugins}"

cat > "$HOME/grafana/conf/custom.ini" <<EOF
[paths]
data    = $HOME/grafana/data
logs    = $HOME/grafana/logs
plugins = $HOME/grafana/plugins

[server]
http_addr = 0.0.0.0
http_port = 3000

[security]
admin_user     = admin
admin_password = changeme
EOF

# --- Start ---
"$HOME/grafana/bin/grafana-server" \
  --config="$HOME/grafana/conf/custom.ini" \
  --homepath="$HOME/grafana"

# --- Add Prometheus datasource (once, ~10 s after Grafana starts) ---
curl -s -u admin:changeme \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:3000/api/datasources \
  -d '{
    "name":      "Prometheus",
    "type":      "prometheus",
    "url":       "http://localhost:9090",
    "access":    "proxy",
    "isDefault": true
  }'

# --- Import the ERSAP overview dashboard (adjust path as needed) ---
DASH_JSON="$HOME/ersap-java/docker/observability/grafana/dashboards/ersap-overview.json"
curl -s -u admin:changeme \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:3000/api/dashboards/import \
  -d "{\"dashboard\": $(cat "$DASH_JSON"), \"overwrite\": true, \"folderId\": 0}"


# =============================================================================
# STEP 7 — PROCESSING NODES (repeat for each node): run the pipeline container
# =============================================================================
# SSH to each processing node and run the following.
# ERSAP_MONITOR_FE tells the pipeline FE DPE (started by run local) where
# to report metrics. Format: "IP%PORT_lang" (verified: canonical DPE name).
# --network=host lets the container reach the monitor node over NERSC fabric.
# Fixed typo: LGIN_NODE_IP -> LOGIN_NODE_IP

# From the monitor node or login node:
ssh nid001235   # substitute actual processing node hostname

export MONITOR_IP=<MONITOR_NODE_IP>         # IP recorded in Step 2
export MONITOR_PORT=19000
export ERSAP_MONITOR_FE="${MONITOR_IP}%${MONITOR_PORT}_java"

# Pull the image once per node
podman-hpc pull docker.io/gurjyan/pet-sro:v1

podman-hpc run -it \
  --network=host \
  --group-add keep-groups \
  --entrypoint /bin/bash \
  -v /global/cfs/cdirs/amsc016/haidis/ersap-data:/global/cfs/cdirs/amsc016/haidis/ersap-data \
  -e ERSAP_MONITOR_FE \
  docker.io/gurjyan/pet-sro:v1

# --- Inside the container ---
source env.sh

"$ERSAP_HOME/bin/ersap-shell"

# --- Inside ersap-shell ---
# set servicesFile $ERSAP_USER_DATA/config/pet_services.yaml
# set inputDir  /global/cfs/cdirs/amsc016/haidis/ersap-data/input
# set outputDir /global/cfs/cdirs/amsc016/haidis/ersap-data/output
# run local


# =============================================================================
# STEP 8 — HOME COMPUTER: open SSH tunnel to monitor node
# =============================================================================
# The login node forwards traffic over NERSC's internal fabric to the monitor node.
# Replace nid001234 with your actual monitor node hostname.

ssh -N \
  -L 3000:nid001234:3000 \
  -L 9090:nid001234:9090 \
  gurjyan@perlmutter.nersc.gov

# Open in browser:
#   Grafana:    http://localhost:3000   (admin / changeme)
#   Prometheus: http://localhost:9090


# =============================================================================
# STEP 9 — Validate each link (run on monitor node)
# =============================================================================

# Monitor FE DPE is listening
ss -tlnp | grep "$MONITOR_PORT"

# Exporter is up and connected to monitor FE (should print 1)
curl -s http://localhost:9095/metrics | grep 'ersap_prometheus_exporter_up'

# Prometheus is scraping the exporter successfully
curl -s 'http://localhost:9090/api/v1/targets' \
  | python3 -m json.tool | grep -A3 '"health"'

# DPE metrics are flowing (non-empty result means at least one pipeline reported)
curl -sg 'http://localhost:9090/api/v1/query?query=ersap_dpe_cpu_usage_percent' \
  | python3 -m json.tool | grep '"value"'

# Grafana is healthy
curl -s -u admin:changeme http://localhost:3000/api/health


# =============================================================================
# STEP 10 — PromQL queries (Grafana or Prometheus UI)
# =============================================================================
# All metric names verified from MonitorMetricParser.java and ExporterSelfMetrics.java.
# Use session="test" to scope to one session, or drop the filter for all sessions.

# CPU usage per DPE
#   ersap_dpe_cpu_usage_percent{session="test"}

# Memory used per DPE (GB)
#   ersap_dpe_memory_usage_bytes{session="test"} / 1e9

# Request rate per service (per second)
#   rate(ersap_service_requests_total{session="test"}[1m])

# Failure rate per service
#   rate(ersap_service_failures_total{session="test"}[1m])

# Mean execution time per request (seconds)
#   rate(ersap_service_execution_time_seconds_total{session="test"}[1m])
#   /
#   rate(ersap_service_requests_total{session="test"}[1m])

# Total network throughput across all services (bytes/s)
#   sum(rate(ersap_service_bytes_received_total{session="test"}[1m]))

# Exporter connectivity — 1 = connected to monitor FE, 0 = disconnected
#   ersap_prometheus_exporter_up

# Engine user metrics (key names depend on your engine implementation)
#   ersap_user_event_rate{session="test"}


# =============================================================================
# CLEANUP
# =============================================================================

# On monitor node — stop monitoring services
pkill -f "j_dpe"
pkill -f "PrometheusExporter"
pkill -f "prometheus"
pkill -f "grafana-server"

# On each processing node — stop containers
podman-hpc stop $(podman-hpc ps -q)

# Or release everything by cancelling the Slurm allocation:
# scancel $SLURM_JOB_ID
