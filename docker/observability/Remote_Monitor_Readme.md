# ERSAP Remote Monitoring (Prometheus + Grafana)

This directory holds a Prometheus + Grafana stack that visualizes the metrics
exposed by `PrometheusReporter` (`j_pr`). It is meant to run on a separate,
remote node — it does not need to be co-located with any DPE.

```
Application DPEs → xMsg dpeReport → j_pr (:9200/metrics) → Prometheus → Grafana
```

## Contents

```
docker-compose.yml                              Prometheus + Grafana, named volumes
prometheus/prometheus.yml                       scrape config, points at j_pr
grafana/provisioning/datasources/datasource.yml auto-registers the Prometheus datasource
grafana/provisioning/dashboards/dashboards.yml  tells Grafana to load dashboards from disk
grafana/dashboards/ersap-overview.json          the ERSAP Overview dashboard
```

## Prerequisites

- Docker and Docker Compose on the remote node.
- `j_pr` already running somewhere reachable from that node (see the main
  repo README / `scripts/unix/j_pr` for how to start it against a DPE
  front-end). Note its host and `--metrics-port` (default `9200`).
- Network path from this node to `<j_pr host>:<metrics-port>`. If they're on
  different networks, that's a firewall/VPN problem to solve first — nothing
  in this stack can work around unreachable targets.

## Setup

1. Copy this directory to the remote node (`scp -r`, `rsync`, a sparse
   `git clone`, etc.).

2. Edit `prometheus/prometheus.yml` and replace the placeholder target:

   ```yaml
   - targets: ["monitoring-host.example.org:9200"]
     labels:
       session: "prod"
   ```

   with the real `<j_pr host>:<metrics-port>`, and set (or remove) the
   `session` label to match whatever `--session` the DPEs were started with.

3. From this directory, start the stack:

   ```bash
   docker compose up -d
   ```

4. Confirm Prometheus is actually scraping `j_pr`:

   ```
   http://<remote-node>:9090/targets
   ```

   The `ersap-prometheus-reporter` job should show state `UP`. If it's
   `DOWN`, `curl http://<j_pr host>:<metrics-port>/metrics` directly from the
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

**Not available yet:** p50/p95/p99 latency. `j_pr` only sees the periodic
DPE-level report, which carries a cumulative execution-time sum — enough for
an average, not a distribution. Getting percentiles would mean also
subscribing to the per-event `done`/`ring` report channels, which is a
separate, higher-volume data path not implemented here.

## Stopping / cleaning up

```bash
docker compose down       # stop, keep volumes (Prometheus TSDB + Grafana state)
docker compose down -v    # stop and delete volumes
```

## Retention

Prometheus is started with `--storage.tsdb.retention.time=30d`
(`docker-compose.yml`). Adjust to taste before running long-term.
