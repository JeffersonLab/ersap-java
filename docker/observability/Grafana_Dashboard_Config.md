# ERSAP Grafana dashboard — configuration guide

## The pipeline, in one sentence

ERSAP processes (DPEs) report to a **Monitor Front End**, a
**PrometheusExporter** (Java process shipped in this repo) turns those
reports into a `/metrics` HTTP page, **Prometheus** scrapes that page every
15 s and stores the history, and **Grafana** queries Prometheus and draws the
graphs you look at.

```
DPEs → Monitor FE (:9000) → PrometheusExporter (:9095/metrics) → Prometheus (:9090) → Grafana (:3000)
```

Everything you'd touch to change what's visualized lives under
`docker/observability/`.

---

## The three kinds of files — understand this first

There are **two different YAML files that do completely different jobs**,
plus **one JSON file that is the actual dashboard**.

### 1. `prometheus/prometheus.yml` — tells Prometheus where to scrape

A "scrape config": one or more entries saying which hosts to poll, how often,
and which labels to attach. Editing this changes what data even exists to
graph. It does not change what a dashboard looks like.

See `Remote_Monitor_Readme.md` § "Adding an external `/metrics` endpoint" for
how to add a new scrape target to this file.

### 2. `grafana/provisioning/` — Grafana bootstrap plumbing

Two files, neither of which is the dashboard design itself:

| File | What it does |
|---|---|
| `datasources/datasource.yml` | registers the Prometheus data source (UID `prometheus`, URL `http://prometheus:9090`) so dashboards can reference it without a hard-coded numeric ID |
| `dashboards/dashboards.yml` | tells Grafana to load any `.json` file it finds under `/var/lib/grafana/dashboards` (mounted from `grafana/dashboards/`) and re-check every 30 s |

You should not need to edit either file unless you add a second data source or
change the dashboard folder.

### 3. `grafana/dashboards/ersap-overview.json` — the actual dashboard

Every panel, every chart, every PromQL query, every title, every layout
position. This is the file to edit when you want to change what is graphed or
add new panels.

---

## Dashboard layout and panel catalog

The dashboard has 12 panels on a **24-unit-wide grid**. Row 0 is the top.

### Top row — stat tiles (y=0, h=4)

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 1 | Total processed events | 0 | 6 | `sum(ersap_service_requests_total{session=~"$session", dpe=~"$dpe"})` | count |
| 2 | Failure rate | 6 | 6 | `100 * sum(rate(ersap_service_failures_total[5m])) / clamp_min(sum(rate(ersap_service_requests_total[5m])), 1e-9)` | percent |
| 3 | DPEs seen in last 30 s | 12 | 6 | `count(time() - ersap_metric_last_update_timestamp_seconds{metric="ersap_dpe_cpu_usage_percent",...} < 30)` | count |
| 4 | Average execution time | 18 | 6 | `sum(rate(ersap_service_execution_time_seconds_total[5m])) / clamp_min(sum(rate(ersap_service_requests_total[5m])), 1e-9)` | seconds |

Panel 2 has colour thresholds: green → orange at 1 % → red at 5 %.
Panel 3 requires the exporter to be started with `--export-timestamps` (see
`Remote_Monitor_Readme.md` § "What's on the dashboard").

### Row 1 — throughput and success/failure (y=4, h=8)

| id | Title | x | w | PromQL |
|---|---|---|---|---|
| 5 | Processing rate by service | 0 | 12 | `sum by (dpe, container, service) (rate(ersap_service_requests_total[5m]))` |
| 6 | Successful vs. failed events | 12 | 12 | success: `sum(rate(...requests...)) - sum(rate(...failures...))` / failure: `sum(rate(...failures...))` |

### Row 2 — error rate and execution time per service (y=12, h=8)

| id | Title | x | w | PromQL |
|---|---|---|---|---|
| 7 | Error rate by service | 0 | 12 | `100 * sum by (...) (rate(failures[5m])) / clamp_min(sum by (...) (rate(requests[5m])), 1e-9)` |
| 8 | Average execution time by service | 12 | 12 | `sum by (...) (rate(exec_time[5m])) / clamp_min(sum by (...) (rate(requests[5m])), 1e-9)` |

### Row 3 — DPE system metrics (y=20, h=8)

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 9 | DPE CPU usage | 0 | 12 | `ersap_dpe_cpu_usage_percent{session=~"$session", dpe=~"$dpe"}` | percent |
| 10 | DPE memory usage | 12 | 12 | `ersap_dpe_memory_usage_bytes{session=~"$session", dpe=~"$dpe"}` | bytes |

### Row 4 — network and shared memory (y=28, h=8)

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 11 | Network bytes sent / received | 0 | 12 | tx: `sum by (dpe,service) (rate(bytes_sent[5m]))` / rx: `sum by (...) (rate(bytes_received[5m]))` | bytes/s |
| 12 | Shared memory reads / writes | 12 | 12 | reads: `sum by (...) (rate(shm_reads[5m]))` / writes: `sum by (...) (rate(shm_writes[5m]))` | ops/s |

---

## Template variables

Both variables are defined in the `templating` section of the JSON. They are
populated by querying Prometheus at dashboard load time (`refresh: 2`) and both
support multi-select and an *All* option.

| Variable | PromQL | What it filters |
|---|---|---|
| `$session` | `label_values(ersap_dpe_cpu_usage_percent, session)` | every session label seen in Prometheus |
| `$dpe` | `label_values(ersap_dpe_cpu_usage_percent{session=~"$session"}, dpe)` | DPEs active in the selected session |

Every panel query uses `session=~"$session", dpe=~"$dpe"` as label matchers,
so the dropdowns at the top of the dashboard filter every panel simultaneously.

---

## How to edit the dashboard — two ways

### Way 1 (recommended): edit visually in Grafana, then export

1. Open the dashboard in the browser.
2. Click a panel title → **Edit**.
3. Change the PromQL in the **Query** tab, or the visualization in the
   **Panel** tab.
4. Click **Apply** (top right).
5. Click **Save dashboard** (disk icon, top right) → **Save**.
6. To persist the change to the repo: **Dashboard settings** (gear icon) →
   **JSON Model** → copy, then overwrite `grafana/dashboards/ersap-overview.json`.
   Alternatively: **Share** → **Export** → **Save to file**.

Grafana re-reads the file from disk every 30 s (`updateIntervalSeconds: 30` in
`dashboards/dashboards.yml`), so a `docker compose restart grafana` is not
required after overwriting the JSON — just wait ~30 s or reload the browser tab.

### Way 2: hand-edit the JSON

Open `grafana/dashboards/ersap-overview.json` in any editor. A panel block
looks like this (abridged from the real "DPE CPU usage" panel):

```json
{
  "id": 9,
  "title": "DPE CPU usage",
  "type": "timeseries",
  "gridPos": {"x": 0, "y": 20, "w": 12, "h": 8},
  "targets": [
    {
      "expr": "ersap_dpe_cpu_usage_percent{session=~\"$session\", dpe=~\"$dpe\"}",
      "legendFormat": "{{dpe}}"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "percent", "min": 0, "max": 100}
  }
}
```

Key fields:

| Field | Meaning |
|---|---|
| `id` | unique integer; pick the next available number |
| `type` | `timeseries` (line chart), `stat` (single big number), `bargauge`, `table`, etc. |
| `gridPos` | position and size on the 24-unit-wide grid; panels must not overlap |
| `targets[].expr` | PromQL query driving the panel |
| `targets[].legendFormat` | legend label; use `{{label_name}}` to pull a label value |
| `fieldConfig.defaults.unit` | Grafana unit ID: `short`, `percent`, `bytes`, `s`, `reqps`, `Bps`, `ops` |
| `fieldConfig.defaults.thresholds` | colour thresholds for `stat` panels |

---

## Adding a new panel

### Step 1 — pick the next panel id and a grid position

The current panels occupy rows y=0–35. To add a full-width row at the bottom:

```json
"gridPos": {"x": 0, "y": 36, "w": 24, "h": 8}
```

Or half-width side by side:

```json
"gridPos": {"x": 0,  "y": 36, "w": 12, "h": 8}   // left
"gridPos": {"x": 12, "y": 36, "w": 12, "h": 8}   // right
```

### Step 2 — write the PromQL query

Use the Prometheus expression browser (`http://localhost:9090/graph`) to
develop and test the query before pasting it into the JSON. Always include the
`$session` and `$dpe` matchers so the dashboard-level dropdowns filter the new
panel too:

```
your_metric_name{session=~"$session", dpe=~"$dpe"}
```

For a rate over a counter:

```
rate(your_counter_total{session=~"$session", dpe=~"$dpe"}[5m])
```

For a per-service breakdown:

```
sum by (dpe, service) (rate(your_counter_total{session=~"$session", dpe=~"$dpe"}[5m]))
```

### Step 3 — add the panel block to the JSON

Copy an existing panel block of the right `type`, update `id`, `title`,
`gridPos`, `expr`, `legendFormat`, and `unit`, then add it to the `panels`
array. Example — a new timeseries panel for event rate from a user engine metric:

```json
{
  "id": 13,
  "title": "Event rate (user engine)",
  "type": "timeseries",
  "gridPos": {"x": 0, "y": 36, "w": 24, "h": 8},
  "targets": [
    {
      "expr": "ersap_user_event_rate_hz{session=~\"$session\", dpe=~\"$dpe\"}",
      "legendFormat": "{{engine}} @ {{dpe}}"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "hertz"}
  }
}
```

### Step 4 — save and reload

Save the JSON file. Grafana picks it up within 30 s automatically. To reload
immediately: **Dashboard settings → JSON Model → paste updated JSON → Save**.

---

## Adding panels for external metrics

If you added an external scrape target to `prometheus/prometheus.yml` (see
`Remote_Monitor_Readme.md` § "Adding an external `/metrics` endpoint"), its
metrics are immediately available in Prometheus. Wire them into a new panel
exactly as above — use the metric names from that job in the `expr` field.

Example — a panel for a hypothetical external exporter publishing
`sagips_event_rate_hz`:

```json
{
  "id": 14,
  "title": "SAGIPS event rate",
  "type": "timeseries",
  "gridPos": {"x": 0, "y": 36, "w": 24, "h": 8},
  "targets": [
    {
      "expr": "sagips_event_rate_hz",
      "legendFormat": "{{instance}}"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "hertz"}
  }
}
```

External metrics typically do not carry `session` or `dpe` labels, so omit
those matchers. If the external job does carry labels you want to filter by,
add a template variable for them the same way `$session` and `$dpe` are defined
in the `templating` section.

---

## Adding a template variable

To add a new dropdown filter — for example a `$pipeline` variable populated
from a label on an external metric:

1. **Via the UI**: **Dashboard settings** (gear icon) → **Variables** → **Add
   variable** → type `Query`, set the data source to `Prometheus`, and enter:
   ```
   label_values(your_metric_name, pipeline)
   ```
   Enable **Multi-value** and **Include All option**. Click **Apply**.
   Export the JSON to persist it.

2. **Via JSON**: add an entry to `templating.list`:

```json
{
  "name": "pipeline",
  "type": "query",
  "datasource": {"type": "prometheus", "uid": "prometheus"},
  "query": "label_values(your_metric_name, pipeline)",
  "refresh": 2,
  "includeAll": true,
  "multi": true,
  "allValue": ".*"
}
```

Then use `pipeline=~"$pipeline"` in the `expr` of any panel you want filtered
by it.

---

## PromQL patterns used in this dashboard

| Pattern | When to use it |
|---|---|
| `metric{label=~"$var"}` | filter by a template variable; `=~` handles multi-select and *All* |
| `rate(counter[5m])` | per-second rate of a counter over a 5-minute window |
| `sum by (label) (...)` | aggregate across series, keeping one label as a dimension |
| `sum(...) / clamp_min(sum(...), 1e-9)` | safe division — avoids divide-by-zero when the denominator is 0 |
| `100 * failures / requests` | percentage |
| `time() - timestamp_metric < 30` | "seen in the last 30 s" liveness check |
| `count(...)` | number of series matching a selector |

---

## Useful unit IDs

| Grafana unit ID | Displayed as |
|---|---|
| `short` | plain number with SI suffix |
| `percent` | `%` |
| `bytes` | `B`, `KiB`, `MiB`, … |
| `s` | seconds |
| `ms` | milliseconds |
| `reqps` | req/s |
| `Bps` | bytes/s |
| `ops` | ops/s |
| `hertz` | Hz |

Full list: **Panel edit → Field tab → Standard options → Unit** dropdown.

---

## See also

* [`Remote_Monitor_Readme.md`](Remote_Monitor_Readme.md) — setup/operating
  guide for this stack, including adding external scrape targets to Prometheus.
* [`../../README.md`](../../README.md#observability) — starting the Monitor FE
  and the exporter.
* [`../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md`](../../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) —
  exporter reference: every option, the full metric catalogue, filters,
  reconnection behaviour.
