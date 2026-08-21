# ERSAP Grafana dashboard — what the files are and how to edit them

This explains, in plain terms, how ERSAP's metrics get into Grafana and which
file you actually need to touch to change what a dashboard shows.

## The pipeline, in one sentence

ERSAP processes (DPEs) report to a **Monitor Front End**, a
**PrometheusExporter** (Java process shipped in this repo) turns those
reports into a `/metrics` HTTP page, **Prometheus** scrapes that page every
15s and stores the history, and **Grafana** queries Prometheus and draws the
graphs you look at.

```
DPEs → Monitor FE (:9000) → PrometheusExporter (:9095/metrics) → Prometheus (:9090) → Grafana (:3000)
```

Everything you'd touch to change what's visualized lives under
`docker/observability/`.

## The two kinds of files — this is the key thing to understand

There are **two different YAML files that do completely different jobs**,
plus **one JSON file that is the actual dashboard**. It's easy to conflate
them.

1. **`prometheus/prometheus.yml`** — tells Prometheus *where to look for
   data*. It's a "scrape config": one entry saying "the exporter lives at
   `host:9095`, scrape it every 15s." Editing this doesn't change what a
   graph looks like — it changes what data even exists to graph.

2. **`grafana/provisioning/datasources/datasource.yml`** and
   **`grafana/provisioning/dashboards/dashboards.yml`** — Grafana's own
   bootstrap files. They tell Grafana "there's a Prometheus at this URL" and
   "load any dashboard files you find in this folder." Also not the
   dashboard design itself — just plumbing.

3. **`grafana/dashboards/ersap-overview.json`** — **this is the actual
   dashboard**: every panel, every chart, every PromQL query, every title,
   every layout position. It's JSON, not YAML. When people say "the Grafana
   dashboard config," this JSON file is what they mean.

## How the dashboard JSON is structured

Each panel is a block like this (the real "DPE CPU usage" panel from the
file):

```json
{
  "id": 9,
  "title": "DPE CPU usage",
  "type": "timeseries",
  "targets": [
    {
      "expr": "ersap_dpe_cpu_usage_percent{session=~\"$session\", dpe=~\"$dpe\"}",
      "legendFormat": "{{dpe}}"
    }
  ],
  "fieldConfig": {
    "defaults": { "unit": "percent", "min": 0, "max": 100 }
  }
}
```

- `title` — what shows above the panel.
- `type` — `stat` (single big number), `timeseries` (line graph), etc.
- `targets[].expr` — the actual Prometheus query (PromQL) driving the panel.
- `$session` and `$dpe` — dashboard-wide dropdown filters (defined once, near
  the top of the file under `"templating"`), so every panel can be filtered
  without editing each query.

The shipped dashboard has 12 panels: 4 top-row stat tiles (total events,
failure rate, DPEs alive in last 30s, avg execution time) and 8 time-series
charts (throughput, success/failure, error rate, CPU, memory, network, shared
memory).

## How to actually change it — two ways

**Way 1 (recommended): edit visually in Grafana, then export.** Open the
dashboard in the browser, click a panel → **Edit**, change the query or
visualization, click **Save dashboard → Export → Save JSON to file**, and
overwrite `grafana/dashboards/ersap-overview.json` with what you download.
This avoids hand-editing JSON and is how most people maintain these files
day to day.

**Way 2: hand-edit the JSON.** To add a new panel, copy an existing panel
block, give it a new unique `id`, change `title` and the PromQL in `expr`,
and place it via `gridPos` (x/y/w/h — a 24-unit-wide grid). Plain JSON, so
any editor works, but a typo in a PromQL string is the most common way to
end up with an empty panel.

Either way, once the file is saved, Grafana picks it up automatically —
`dashboards.yml` sets `updateIntervalSeconds: 30`, so a
`docker compose restart grafana` isn't even required; just wait ~30s or
refresh the dashboard page.

## What metrics are actually available to plot

Whatever the exporter publishes on `/metrics`:

```bash
curl http://<exporter-host>:9095/metrics
```

Common ones already used in the shipped dashboard: `ersap_dpe_cpu_usage_percent`,
`ersap_dpe_memory_*`, `ersap_service_requests_total`, plus anything a
pipeline's own engine code publishes via `EngineMetricsPublisher`. New engine
metrics show up automatically once that engine runs — no exporter or
Prometheus config change needed. You'd only touch the dashboard JSON to
actually chart them.

## One gotcha worth flagging

The "DPEs seen in last 30s" stat panel only works if the exporter was
started with `--export-timestamps`. Without that flag, a dead DPE's last
known values just sit there forever with no way to tell it's stale.

## See also

* [`Remote_Monitor_Readme.md`](Remote_Monitor_Readme.md) — setup/operating
  guide for this Prometheus + Grafana stack (Docker Compose based).
* [`../../README-PROMETHEUS.md`](../../README-PROMETHEUS.md) — full
  three-node deployment walkthrough, including starting the Monitor FE and
  the exporter.
* [`../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md`](../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) —
  exporter reference: every option, the metric catalogue, filters,
  reconnection behaviour.
