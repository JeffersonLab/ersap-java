# `docker/` — ERSAP containerization and monitoring

Two unrelated things: a `Dockerfile` that packages ERSAP into a runnable image,
and a `docker-compose.yml` that runs the Prometheus + Grafana monitoring stack.

```
docker/
├── Dockerfile              packages ERSAP itself into a runnable image
├── hooks/                  Docker Hub automated-build hooks (build the image above)
└── observability/          Prometheus + Grafana stack that visualizes ERSAP's metrics
```

They do not depend on each other. `observability/` doesn't run any ERSAP code —
it consumes metrics published by a `PrometheusExporter` process running somewhere
— and you don't need the ERSAP image built to run it.

---

## 1. Building and running the ERSAP image

Builds a runnable ERSAP container using a **multi-stage build**:

- **Stage 1 (`build`)** — `eclipse-temurin:17-jdk-jammy`, copies the source in,
  runs `./gradlew build check` then `./gradlew deploy`: the same build/deploy path
  the root [`README.md`](../README.md#build) assumes (`ERSAP_HOME`,
  `./gradlew deploy`).
- **Stage 2 (final image)** — `eclipse-temurin:17-jre-jammy`, just the JRE, no
  build toolchain. Only the built `${ERSAP_HOME}` tree is copied out of stage 1,
  so the JDK/Gradle/source used to build it never end up in the shipped image.

It exposes:

- `7771-7775` — the DPE ports each pipeline node binds.
- `9095` — the PrometheusExporter's `/metrics` port, only relevant if this image
  is used to run the exporter.

Volumes are declared for `data/input`, `data/output`, and `log`, so a container's
I/O isn't trapped inside the container filesystem.

`hooks/build` and `hooks/post_push` are **Docker Hub automated-build hooks** —
Docker Hub calls these scripts (not you, manually) when it auto-builds and pushes
an image on a repo push. `build` runs the two-stage `docker build` (tagging the
`build` stage as a dev/debug image, and the final image separately); `post_push`
pushes that dev-stage image under a derived tag. This is legacy Docker Hub CI
plumbing — unrelated to anything you'd run by hand.

### Building the image locally

The `Dockerfile` does `COPY . .`, so the build context must be the **repo root**,
not `docker/` — run this from the top of the repository:

```bash
docker build -t ersap-java -f docker/Dockerfile .
```

To build only the intermediate JDK/build stage (useful for debugging the build
itself, or as a dev image with the full toolchain still present):

```bash
docker build --target build -t ersap-java-dev -f docker/Dockerfile .
```

### Running a container

The image has no `ENTRYPOINT`/`CMD` — you supply the command, exactly as you
would on a bare-metal install with `ERSAP_HOME` already on `PATH`. For example,
to start a DPE:

```bash
docker run --rm -it \
  -p 7771-7775:7771-7775 \
  -v "$PWD/data/input:/usr/local/ersap/data/input" \
  -v "$PWD/data/output:/usr/local/ersap/data/output" \
  -v "$PWD/log:/usr/local/ersap/log" \
  ersap-java \
  j_dpe --host 0.0.0.0 --port 7771 --session mydemo
```

- `-p 7771-7775:7771-7775` publishes the DPE port range the image `EXPOSE`s.
- The three `-v` mounts map the declared volumes to host directories, so results
  and logs survive after the container exits.
- Swap the trailing command for whatever ERSAP entry point you need —
  `ersap-shell`, `j_dpe`, or the `PrometheusExporter` (add `-p 9095:9095` if
  you run the exporter this way).

To open a shell in the container instead of running ERSAP directly:

```bash
docker run --rm -it --entrypoint bash ersap-java
```

---

## 2. Monitoring stack (Prometheus + Grafana)

### How it works

```
DPEs → Monitor FE (:9000) → PrometheusExporter (:9095/metrics) → Prometheus (:9090) → Grafana (:3000)
```

`PrometheusExporter` subscribes to the Monitor FE over xMsg/ZeroMQ, converts
every DPE report and engine user-metric into Prometheus gauges/counters, and
serves them on `/metrics`. Prometheus scrapes that endpoint on a schedule.
Grafana queries Prometheus and renders the dashboards.

### Files

| Path | What it does |
|---|---|
| `observability/docker-compose.yml` | runs `prom/prometheus:v2.53.0` and `grafana/grafana:11.1.0` with named volumes |
| `observability/prometheus/prometheus.yml` | scrape config — tells Prometheus which hosts to poll and how often |
| `observability/grafana/provisioning/datasources/datasource.yml` | auto-registers the Prometheus data source (UID `prometheus`, URL `http://prometheus:9090`) |
| `observability/grafana/provisioning/dashboards/dashboards.yml` | tells Grafana to load any `.json` from the dashboards folder, re-checked every 30 s |
| `observability/grafana/dashboards/ersap-overview.json` | the ERSAP Overview dashboard (12 panels) |

### Prerequisites

- Docker and Docker Compose on the host running this stack.
- `PrometheusExporter` already running somewhere reachable — see
  [`README.md`](../README.md#observability) for how to start it against a
  Monitor FE. Note its host and `--prometheus-port` (default `9095`).
- Network path from this host to `<exporter-host>:<prometheus-port>`. If they're
  on different networks, that's a firewall/VPN problem to solve first — nothing
  in this stack can work around unreachable targets.
- Start the exporter with `--export-timestamps` for the "DPEs seen in last 30s"
  panel to work (see [What's on the dashboard](#whats-on-the-ersap-overview-dashboard)).

### Setup

1. Copy `observability/` to the monitoring host (`scp -r`, `rsync`, or a sparse
   `git clone`).

2. Edit `prometheus/prometheus.yml` and replace the placeholder target with the
   real `<exporter-host>:<prometheus-port>`:

   ```yaml
   static_configs:
     - targets: ["<exporter-host>:9095"]
       labels:
         session: "prod"    # match --session the DPEs were started with, or remove
   ```

3. Start the stack — nothing to build, both images are pulled from Docker Hub:

   ```bash
   cd observability
   docker compose up -d
   ```

4. Confirm Prometheus is scraping the exporter:

   ```
   http://<host>:9090/targets   # ersap-monitor job should show UP
   ```

   If it shows DOWN, run `curl http://<exporter-host>:9095/metrics` directly
   from the monitoring host first — that isolates a Prometheus config problem
   from a network reachability problem.

5. Open Grafana — the data source and **ERSAP Overview** dashboard are
   auto-provisioned on startup, nothing to import:

   ```
   http://<host>:3000   # admin / changeme — change the password on first login
   ```

### Adding an external `/metrics` endpoint

Any component that exposes a Prometheus-format `/metrics` endpoint can be
scraped alongside the ERSAP exporter — no code changes to ERSAP required.

Edit `prometheus/prometheus.yml` and append a job under `scrape_configs`. The
file ships with one job:

```yaml
scrape_configs:
  - job_name: ersap-monitor
    metrics_path: /metrics
    scrape_interval: 15s
    static_configs:
      - targets: ["monitoring-host.example.org:9095"]
        labels:
          session: "prod"
```

**Minimal entry** — only `job_name` and `targets` are required:

```yaml
  - job_name: my-exporter
    static_configs:
      - targets: ["<host>:<port>"]   # e.g. "10.0.0.5:8000" or "localhost:9200"
```

`metrics_path` defaults to `/metrics` and `scrape_interval` inherits the global
`15s`, so nothing else is needed for a plain HTTP endpoint on any port.

**Full annotated entry**:

```yaml
  - job_name: my-exporter

    metrics_path: /metrics          # default; omit if unchanged
    scrape_interval: 15s            # omit to inherit the global 15s
    scrape_timeout: 10s

    static_configs:
      - targets:
          - "<host>:<port>"
        labels:
          pipeline: "mypipe"        # any labels added to every series from this target

    # Only needed for Basic Auth endpoints:
    # basic_auth:
    #   username: "user"
    #   password: "secret"

    # Only needed for HTTPS endpoints:
    # scheme: https
    # tls_config:
    #   insecure_skip_verify: true
```

`job_name` must be unique across all entries.

**Reload without restarting the stack:**

```bash
curl -X POST http://localhost:9090/-/reload
```

If the reload is rejected, check `docker compose logs prometheus` for a parse
error. Alternatively, `docker compose restart prometheus` also picks up the
change.

Verify the new target is **UP** at `http://localhost:9090/targets`. The new
job's metrics are immediately queryable in Grafana — see
[§ 3 Grafana configuration](#3-grafana-configuration) for how to add panels.

### What's on the ERSAP Overview dashboard

- Total processed events, failure rate, DPEs seen in the last 30 s, average
  execution time (top stat row)
- Processing rate by service, successful vs. failed events
- Error rate by service, average execution time by service
- DPE CPU usage, DPE memory usage
- Network bytes sent/received, shared-memory reads/writes

All panels are filterable by `$session` and `$dpe` dropdowns at the top.

**"DPEs seen in last 30 s" needs `--export-timestamps`.** That panel queries
`ersap_metric_last_update_timestamp_seconds`, which the exporter only emits when
started with `--export-timestamps`. Without that flag, a dead DPE's last known
values sit there indefinitely with no way to tell it's stale.

**Not available yet:** p50/p95/p99 latency. The exporter only sees the periodic
DPE-level report, which carries a cumulative execution-time sum — enough for an
average, not a distribution.

### Stopping and data retention

```bash
docker compose down       # stop, keep Prometheus TSDB and Grafana state volumes
docker compose down -v    # stop and delete volumes
```

Prometheus retains data for 30 days (`--storage.tsdb.retention.time=30d` in
`docker-compose.yml`). Adjust before running long-term.

---

## 3. Grafana configuration

### The three kinds of files — understand this first

**`prometheus/prometheus.yml`** — tells Prometheus *where to scrape*. Editing
this changes what data exists to graph; it does not change what a dashboard
looks like. See [§ Adding an external `/metrics` endpoint](#adding-an-external-metrics-endpoint)
for how to add a new scrape target.

**`grafana/provisioning/`** — Grafana bootstrap plumbing:

| File | What it does |
|---|---|
| `datasources/datasource.yml` | registers the Prometheus data source (UID `prometheus`, URL `http://prometheus:9090`) so dashboards can reference it without a hard-coded numeric ID |
| `dashboards/dashboards.yml` | tells Grafana to load any `.json` it finds in the dashboards folder and re-check every 30 s |

You should not need to edit either file unless you add a second data source or
change the dashboard folder.

**`grafana/dashboards/ersap-overview.json`** — the actual dashboard: every
panel, every PromQL query, every title, every layout position. This is the file
to edit when you want to change what is graphed or add new panels.

### Dashboard layout and panel catalog

The dashboard has 12 panels on a **24-unit-wide grid**. Row 0 is the top.

**Top row — stat tiles (y=0, h=4)**

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 1 | Total processed events | 0 | 6 | `sum(ersap_service_requests_total{session=~"$session", dpe=~"$dpe"})` | count |
| 2 | Failure rate | 6 | 6 | `100 * sum(rate(ersap_service_failures_total[5m])) / clamp_min(sum(rate(ersap_service_requests_total[5m])), 1e-9)` | percent |
| 3 | DPEs seen in last 30 s | 12 | 6 | `count(time() - ersap_metric_last_update_timestamp_seconds{metric="ersap_dpe_cpu_usage_percent",...} < 30)` | count |
| 4 | Average execution time | 18 | 6 | `sum(rate(ersap_service_execution_time_seconds_total[5m])) / clamp_min(sum(rate(ersap_service_requests_total[5m])), 1e-9)` | seconds |

Panel 2 has colour thresholds: green → orange at 1 % → red at 5 %.
Panel 3 requires `--export-timestamps`.

**Row 1 — throughput and success/failure (y=4, h=8)**

| id | Title | x | w | PromQL |
|---|---|---|---|---|
| 5 | Processing rate by service | 0 | 12 | `sum by (dpe, container, service) (rate(ersap_service_requests_total[5m]))` |
| 6 | Successful vs. failed events | 12 | 12 | success: `sum(rate(...requests...)) - sum(rate(...failures...))` / failure: `sum(rate(...failures...))` |

**Row 2 — error rate and execution time per service (y=12, h=8)**

| id | Title | x | w | PromQL |
|---|---|---|---|---|
| 7 | Error rate by service | 0 | 12 | `100 * sum by (...) (rate(failures[5m])) / clamp_min(sum by (...) (rate(requests[5m])), 1e-9)` |
| 8 | Average execution time by service | 12 | 12 | `sum by (...) (rate(exec_time[5m])) / clamp_min(sum by (...) (rate(requests[5m])), 1e-9)` |

**Row 3 — DPE system metrics (y=20, h=8)**

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 9 | DPE CPU usage | 0 | 12 | `ersap_dpe_cpu_usage_percent{session=~"$session", dpe=~"$dpe"}` | percent |
| 10 | DPE memory usage | 12 | 12 | `ersap_dpe_memory_usage_bytes{session=~"$session", dpe=~"$dpe"}` | bytes |

**Row 4 — network and shared memory (y=28, h=8)**

| id | Title | x | w | PromQL | Unit |
|---|---|---|---|---|---|
| 11 | Network bytes sent / received | 0 | 12 | tx: `sum by (dpe,service) (rate(bytes_sent[5m]))` / rx: `sum by (...) (rate(bytes_received[5m]))` | bytes/s |
| 12 | Shared memory reads / writes | 12 | 12 | reads: `sum by (...) (rate(shm_reads[5m]))` / writes: `sum by (...) (rate(shm_writes[5m]))` | ops/s |

### Template variables

Both variables are populated by querying Prometheus at dashboard load time and
support multi-select and an *All* option.

| Variable | PromQL | What it filters |
|---|---|---|
| `$session` | `label_values(ersap_dpe_cpu_usage_percent, session)` | every session label seen in Prometheus |
| `$dpe` | `label_values(ersap_dpe_cpu_usage_percent{session=~"$session"}, dpe)` | DPEs active in the selected session |

Every panel query uses `session=~"$session", dpe=~"$dpe"` as label matchers, so
the dropdowns at the top filter every panel simultaneously.

### How to edit the dashboard

**Way 1 (recommended): edit visually in Grafana, then export**

1. Click a panel title → **Edit**.
2. Change the PromQL in the **Query** tab, or the visualization in the **Panel** tab.
3. Click **Apply**, then **Save dashboard**.
4. To persist to the repo: **Dashboard settings** (gear) → **JSON Model** →
   copy, then overwrite `grafana/dashboards/ersap-overview.json`. Or:
   **Share** → **Export** → **Save to file**.

Grafana re-reads the dashboards folder every 30 s automatically — no restart needed.

**Way 2: hand-edit the JSON**

A panel block (example from the "DPE CPU usage" panel):

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
| `targets[].legendFormat` | legend label; `{{label_name}}` pulls a label value |
| `fieldConfig.defaults.unit` | Grafana unit ID: `short`, `percent`, `bytes`, `s`, `reqps`, `Bps`, `ops` |
| `fieldConfig.defaults.thresholds` | colour thresholds for `stat` panels |

### Adding a new panel

**Step 1 — pick the next id and grid position.**
Current panels occupy y=0–35. Full-width row at the bottom:

```json
"gridPos": {"x": 0, "y": 36, "w": 24, "h": 8}
```

Half-width side by side:

```json
"gridPos": {"x": 0,  "y": 36, "w": 12, "h": 8}
"gridPos": {"x": 12, "y": 36, "w": 12, "h": 8}
```

**Step 2 — write the PromQL query.**
Use the Prometheus expression browser (`http://localhost:9090/graph`) to develop
and test before pasting. Include `$session` and `$dpe` matchers for ERSAP metrics:

```
rate(your_counter_total{session=~"$session", dpe=~"$dpe"}[5m])
```

**Step 3 — add the panel block to the JSON.** Copy an existing panel of the right
`type`, update `id`, `title`, `gridPos`, `expr`, `legendFormat`, and `unit`, then
add it to the `panels` array. Example — event rate from a user engine metric:

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
  "fieldConfig": {"defaults": {"unit": "hertz"}}
}
```

**Step 4 — save.** Grafana picks it up within 30 s. To reload immediately:
**Dashboard settings → JSON Model → paste updated JSON → Save**.

### Adding panels for external metrics

If you added an external scrape target (see
[§ Adding an external `/metrics` endpoint](#adding-an-external-metrics-endpoint)),
its metrics are immediately available in Prometheus. Wire them into a new panel
exactly as above. External metrics typically do not carry `session` or `dpe`
labels — omit those matchers:

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
  "fieldConfig": {"defaults": {"unit": "hertz"}}
}
```

If the external job carries labels you want to filter by, add a template variable
(see below).

### Adding a template variable

**Via the UI**: **Dashboard settings** → **Variables** → **Add variable** →
type `Query`, data source `Prometheus`, query:

```
label_values(your_metric_name, pipeline)
```

Enable **Multi-value** and **Include All option**. Click **Apply**, then export
the JSON to persist.

**Via JSON**: add an entry to `templating.list`:

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

Then use `pipeline=~"$pipeline"` in any panel `expr`.

### PromQL patterns

| Pattern | When to use it |
|---|---|
| `metric{label=~"$var"}` | filter by a template variable; `=~` handles multi-select and *All* |
| `rate(counter[5m])` | per-second rate of a counter over a 5-minute window |
| `sum by (label) (...)` | aggregate across series, keeping one label as a dimension |
| `sum(...) / clamp_min(sum(...), 1e-9)` | safe division — avoids divide-by-zero when denominator is 0 |
| `100 * failures / requests` | percentage |
| `time() - timestamp_metric < 30` | "seen in the last 30 s" liveness check |
| `count(...)` | number of series matching a selector |

### Grafana unit IDs

| ID | Displayed as |
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

- [`../README.md`](../README.md#observability) — starting the Monitor FE and
  the PrometheusExporter.
- [`../HOWTO-perlmutter.md`](../HOWTO-perlmutter.md) — Perlmutter Slurm
  deployment and adding external targets to `ersap.yml`.
- [`../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md`](../src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) —
  PrometheusExporter reference: every option, the full metric catalogue,
  filters, reconnection, alert rules.
