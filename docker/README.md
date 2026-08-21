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

- **Stage 1 (`build`)** — `openjdk:8-jdk-slim`, copies the source in, runs
  `./gradlew build check` then `./gradlew deploy`: the same build/deploy path
  the root [`README-PROMETHEUS.md`](../README-PROMETHEUS.md) deployment
  guide assumes (`ERSAP_HOME`, `./gradlew deploy`).
- **Stage 2 (final image)** — `openjdk:8-jre-slim`, just the JRE, no build
  toolchain. Only the built `${ERSAP_HOME}` tree is copied out of stage 1, so
  the JDK/Gradle/source used to build it never end up in the shipped image.

It exposes:

- `7771-7775` — the DPE ports each pipeline node binds (matches the "Ports"
  table in `README-PROMETHEUS.md`).
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

### Running the stack

Nothing to build — it's two off-the-shelf images
(`prom/prometheus:v2.53.0`, `grafana/grafana:11.1.0`) wired together by
compose. From this directory:

```bash
cd docker/observability

# point prometheus/prometheus.yml at your real PrometheusExporter host:port first
docker compose up -d

# check the exporter target is UP
open http://localhost:9090/targets

# Grafana — admin / changeme (change it on first login)
open http://localhost:3000
```

The Prometheus data source and the **ERSAP Overview** dashboard are
auto-provisioned on startup — nothing to import by hand. See
`Remote_Monitor_Readme.md` linked below for the full setup/edit/prerequisite
walkthrough.

```bash
docker compose down       # stop, keep the Prometheus/Grafana data volumes
docker compose down -v    # stop and delete them
```

See:

- [`observability/Remote_Monitor_Readme.md`](observability/Remote_Monitor_Readme.md)
  — setup/operating guide for this stack.
- [`observability/Grafana_Dashboard_Config.md`](observability/Grafana_Dashboard_Config.md)
  — what the Prometheus/Grafana YAML files and the dashboard JSON actually
  do, and how to edit the dashboard.
- [`../README-PROMETHEUS.md`](../README-PROMETHEUS.md) — full three-node
  deployment walkthrough, including starting the Monitor FE and the
  exporter that feeds this stack.
