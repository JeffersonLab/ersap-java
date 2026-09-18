# `docker/` — ERSAP's containerization layer

This directory holds two distinct, unrelated things that both happen to be
"containerize something related to ERSAP," for different audiences:

```
docker/
├── Dockerfile              packages ERSAP itself into a runnable image
├── hooks/                  Docker Hub automated-build hooks (build the image above)
└── observability/          Prometheus + Grafana stack that visualizes ERSAP's metrics
```

They do not depend on each other. `observability/` doesn't run any ERSAP
code — it just consumes metrics published by a `PrometheusExporter` process
running somewhere — and you don't need the ERSAP image built to run it.

## 1. `Dockerfile` + `hooks/` — packaging ERSAP itself into an image

Builds a runnable ERSAP container using a **multi-stage build**:

- **Stage 1 (`build`)** — `eclipse-temurin:17-jdk-jammy`, copies the source in, runs
  `./gradlew build check` then `./gradlew deploy`: the same build/deploy path
  the root [`README.md`](../README.md#build) assumes (`ERSAP_HOME`,
  `./gradlew deploy`).
- **Stage 2 (final image)** — `eclipse-temurin:17-jre-jammy`, just the JRE, no build
  toolchain. Only the built `${ERSAP_HOME}` tree is copied out of stage 1, so
  the JDK/Gradle/source used to build it never end up in the shipped image.

It exposes:

- `7771-7775` — the DPE ports each pipeline node binds.
- `9095` — the PrometheusExporter's `/metrics` port, only relevant if this
  image is used to run the exporter.

Volumes are declared for `data/input`, `data/output`, and `log`, so a
container's I/O isn't trapped inside the container filesystem.

`hooks/build` and `hooks/post_push` are **Docker Hub automated-build hooks**
— Docker Hub calls these scripts (not you, manually) when it auto-builds and
pushes an image on a repo push. `build` runs the two-stage `docker build`
(tagging the `build` stage as a dev/debug image, and the final image
separately); `post_push` pushes that dev-stage image under a derived tag.
This is legacy Docker Hub CI plumbing — unrelated to anything you'd run by
hand.

### Building the image locally

The `Dockerfile` does `COPY . .`, so the build context must be the **repo
root**, not `docker/` — run this from the top of the repository:

```bash
docker build -t ersap-java -f docker/Dockerfile .
```

That's the same thing `hooks/build` does for Docker Hub (`docker build
--tag $IMAGE_NAME -f Dockerfile ..`, run from inside `docker/`, which is
equivalent since `..` from there is the repo root).

To build only the intermediate JDK/build stage (useful for debugging the
build itself, or as a dev image with the full toolchain still present):

```bash
docker build --target build -t ersap-java-dev -f docker/Dockerfile .
```

### Running a container

The image has no `ENTRYPOINT`/`CMD` — you supply the command, exactly as you
would on a bare-metal install with `ERSAP_HOME` already on `PATH`. For
example, to start a DPE:

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
- The three `-v` mounts map the declared volumes (`data/input`,
  `data/output`, `log`) to host directories, so results and logs survive
  after the container exits.
- Swap the trailing command for whatever ERSAP entry point you need —
  `ersap-shell`, `j_dpe`, or the `PrometheusExporter` (add `-p
  9095:9095` if you run the exporter this way).

To open a shell in the container instead of running ERSAP directly:

```bash
docker run --rm -it --entrypoint bash ersap-java
```

## 2. `observability/` — the monitoring stack

A `docker-compose.yml` that runs **Prometheus + Grafana** (not ERSAP itself)
to visualize metrics coming from a `PrometheusExporter` process running
somewhere reachable.

```
DPEs → Monitor FE (:9000) → PrometheusExporter (:9095/metrics) → Prometheus (:9090) → Grafana (:3000)
```

### Files

| Path | What it does |
|---|---|
| `docker-compose.yml` | runs `prom/prometheus:v2.53.0` and `grafana/grafana:11.1.0` with named volumes |
| `prometheus/prometheus.yml` | scrape config — tells Prometheus which hosts to poll |
| `grafana/provisioning/datasources/datasource.yml` | auto-registers the Prometheus data source |
| `grafana/provisioning/dashboards/dashboards.yml` | tells Grafana to load dashboards from disk, re-checked every 30 s |
| `grafana/dashboards/ersap-overview.json` | the ERSAP Overview dashboard (12 panels) |

### Setup and startup

1. Edit `prometheus/prometheus.yml` and replace the placeholder target with
   the real host and port of the `PrometheusExporter` (default port `9095`):

   ```yaml
   static_configs:
     - targets: ["<exporter-host>:9095"]
       labels:
         session: "prod"    # match --session the DPEs were started with, or remove
   ```

2. Start the stack (nothing to build — both images are pulled from Docker Hub):

   ```bash
   cd docker/observability
   docker compose up -d
   ```

3. Verify Prometheus is scraping the exporter:

   ```
   http://localhost:9090/targets   # ersap-monitor job should show UP
   ```

   If it shows DOWN, run `curl http://<exporter-host>:9095/metrics` directly
   from the Docker host to isolate a config problem from a network problem.

4. Open Grafana — the data source and the **ERSAP Overview** dashboard are
   auto-provisioned on startup, nothing to import:

   ```
   http://localhost:3000   # admin / changeme — change the password on first login
   ```

### Stopping and data retention

```bash
docker compose down       # stop, keep Prometheus TSDB and Grafana state volumes
docker compose down -v    # stop and delete volumes
```

Prometheus retains data for 30 days (`--storage.tsdb.retention.time=30d` in
`docker-compose.yml`). Adjust before running long-term.

### Adding an external `/metrics` endpoint

Any component that exposes a Prometheus-format `/metrics` endpoint can be
scraped alongside the ERSAP exporter — no code changes required.

Edit `prometheus/prometheus.yml` and append a job under `scrape_configs`.
The file ships with one job:

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

Verify the new target is **UP** at `http://localhost:9090/targets`.

### Adding a Grafana panel for external metrics

Once Prometheus is scraping a new job, its metrics are immediately queryable.
To visualize them, add a panel to
`grafana/dashboards/ersap-overview.json` — or create a new dashboard JSON file
in `grafana/dashboards/` (Grafana loads every `.json` in that directory).

Grafana re-reads the dashboards folder every 30 s automatically. A minimal new
panel block in the JSON:

```json
{
  "id": 13,
  "title": "My external metric",
  "type": "timeseries",
  "gridPos": {"x": 0, "y": 36, "w": 24, "h": 8},
  "targets": [
    {
      "expr": "my_metric_name{label=~\"value\"}",
      "legendFormat": "{{instance}}"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "short"}
  }
}
```

See [`observability/Grafana_Dashboard_Config.md`](observability/Grafana_Dashboard_Config.md)
for the full panel catalog, PromQL patterns, grid layout reference, and how to
add template variables.

### Further reading

- [`observability/Remote_Monitor_Readme.md`](observability/Remote_Monitor_Readme.md)
  — full setup/operating guide: prerequisites, step-by-step startup, what's on
  the dashboard, stopping and cleanup.
- [`observability/Grafana_Dashboard_Config.md`](observability/Grafana_Dashboard_Config.md)
  — panel catalog with every PromQL query, how to add and edit panels, PromQL
  patterns, Grafana unit IDs.
- [`../README.md`](../README.md#observability) — starting the Monitor FE and
  the exporter that feeds this stack.
