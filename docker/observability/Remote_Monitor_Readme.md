# ERSAP Remote Monitoring (Prometheus + Grafana)

This directory holds a Prometheus + Grafana stack that visualizes the metrics
exposed by `org.jlab.epsci.ersap.util.prometheus.PrometheusExporter`. It is
meant to run on a separate, remote node — it does not need to be co-located
with any DPE.

```
Application DPEs → xMsg dpeReport/userMetrics → Monitor FE → PrometheusExporter (:9095/metrics) → Prometheus → Grafana
```

The exporter itself, its full option reference, and a complete three-node
deployment walkthrough (pipeline nodes + Monitor FE + exporter) live outside
this directory:

* [`../../README-PROMETHEUS.md`](../../README-PROMETHEUS.md) — step-by-step
  deployment guide, including how to start the Monitor FE and the exporter.
* [`../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md`](../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) —
  exporter reference: every option, the metric catalogue, filters, reconnection
  behaviour.

This directory only covers the Prometheus + Grafana half of the stack — it
assumes the exporter is already running somewhere.

## Contents

```
docker-compose.yml                              Prometheus + Grafana, named volumes
prometheus/prometheus.yml                       scrape config, points at PrometheusExporter
grafana/provisioning/datasources/datasource.yml auto-registers the Prometheus datasource
grafana/provisioning/dashboards/dashboards.yml  tells Grafana to load dashboards from disk
grafana/dashboards/ersap-overview.json          the ERSAP Overview dashboard
```

## Prerequisites

- Docker and Docker Compose on the remote node.
- `PrometheusExporter` already running somewhere reachable from that node —
  see `README-PROMETHEUS.md` at the repository root for how to start it
  against a Monitor FE. Note its host and `--prometheus-port` (default
  `9095`).
- Network path from this node to `<exporter host>:<prometheus-port>`. If
  they're on different networks, that's a firewall/VPN problem to solve
  first — nothing in this stack can work around unreachable targets.
- The exporter must be started with `--export-timestamps` for the "DPEs seen
  in last 30s" panel to work (see below).

## Setup

1. Copy this directory to the remote node (`scp -r`, `rsync`, a sparse
   `git clone`, etc.).

2. Edit `prometheus/prometheus.yml` and replace the placeholder target:

   ```yaml
   - targets: ["monitoring-host.example.org:9095"]
     labels:
       session: "prod"
   ```

   with the real `<exporter host>:<prometheus-port>`, and set (or remove) the
   `session` label to match whatever `--session` the DPEs were started with.

3. From this directory, start the stack:

   ```bash
   docker compose up -d
   ```

4. Confirm Prometheus is actually scraping the exporter:

   ```
   http://<remote-node>:9090/targets
   ```

   The `ersap-monitor` job should show state `UP`. If it's `DOWN`, `curl
   http://<exporter host>:<prometheus-port>/metrics` directly from the
   remote node first — that isolates whether it's a Prometheus config
   problem or a network reachability problem.

5. Open Grafana:

   ```
   http://<remote-node>:3000
   ```

   Log in with `admin` / `changeme` and **change the password immediately**
   — it's a plaintext default in `docker-compose.yml`, fine for a quick
   local trial, not for anything left running.

   The datasource and the **ERSAP Overview** dashboard (folder: `ERSAP`) are
   already provisioned — nothing to import manually.

## What's on the dashboard

- Total processed events, failure rate, DPEs seen in the last 30s, average
  execution time (top stat row)
- Processing rate by service, success vs. failure, error rate by service,
  average execution time by service
- DPE CPU usage, DPE memory usage
- Network bytes sent/received, shared-memory reads/writes

All panels are filterable by the `$session` and `$dpe` dashboard variables.

**"DPEs seen in last 30s" needs `--export-timestamps`.** That panel queries
`ersap_metric_last_update_timestamp_seconds{metric="ersap_dpe_cpu_usage_percent",...}`,
which the exporter only emits when started with `--export-timestamps`. Without
that flag the exporter keeps re-serving the last known value for a DPE
indefinitely (there is no per-series expiry), so this is the only reliable way
to tell a live DPE from one that stopped reporting.

**Not available yet:** p50/p95/p99 latency. The exporter only sees the
periodic DPE-level report, which carries a cumulative execution-time sum —
enough for an average, not a distribution. Getting percentiles would mean
also subscribing to the per-event `done`/`ring` report channels, which is a
separate, higher-volume data path not implemented by the exporter.

## Stopping / cleaning up

```bash
docker compose down       # stop, keep volumes (Prometheus TSDB + Grafana state)
docker compose down -v    # stop and delete volumes
```

## Retention

Prometheus is started with `--storage.tsdb.retention.time=30d`
(`docker-compose.yml`). Adjust to taste before running long-term.
