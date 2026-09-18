# ERSAP Java

Micro-services framework for distributed scientific data-stream processing.
Engines written in Java, C++, or Python are wired into pipelines via a YAML
services file and driven by an orchestrator over ZeroMQ.

---

## Build

Requires Java 17+.

```bash
git clone https://github.com/JeffersonLab/ersap-java.git
cd ersap-java
./gradlew deploy          # compile, test, checkstyle, SpotBugs, install into $ERSAP_HOME
```

`ERSAP_HOME` must be set before running `deploy`. To run only the tests without
installing:

```bash
./gradlew test            # unit and integration tests
./gradlew check           # tests + checkstyle + SpotBugs
```

---

## Run a pipeline

```bash
export ERSAP_HOME=/path/to/ersap
export ERSAP_USER_DATA=/path/to/user/data
export ERSAP_MONITOR_FE="<monitor-ip>%9000_java"   # enables metric forwarding to the Monitor FE

ersap-shell
```

Inside the shell:

```
set session      myrun
set servicesFile $ERSAP_USER_DATA/config/pipeline.yaml
set inputDir     $ERSAP_USER_DATA/data/input
set outputDir    $ERSAP_USER_DATA/data/output
run local
```

| Command | What it does |
|---|---|
| `set session` | names this run; DPE reports and user metrics are tagged with it |
| `set servicesFile` | path to the YAML file that declares I/O services, the processing chain, and per-service configuration |
| `set inputDir` / `set outputDir` | directories the reader and writer services use |
| `run local` | deploys all services on this node and starts processing |

`ERSAP_MONITOR_FE` must be set to `<monitor-ip>%<port>_java` (e.g.
`10.0.0.1%9000_java`) for the DPE to forward its reports. Without it the
pipeline runs normally but no metrics are published.

---

## Environment variables in YAML config

Both the application/services YAML (loaded by the orchestrator) and the
per-engine service specification YAML support environment-variable
substitution. Any `${VAR}` occurrence in the file is replaced with the value
of `VAR` from the process environment before the YAML is parsed. Use
`${VAR:-default}` to provide a fallback when the variable is unset (an unset
variable with no default expands to the empty string).

Example:

```yaml
io-services:
  reader:
    class: ${READER_CLASS:-org.jlab.clas12.ana.ReaderService}
    name: ReaderService
  writer:
    class: org.jlab.clas12.ana.WriterService
    name: WriterService
services:
  - class: org.jlab.clas12.rec.ServiceB
    name: ServiceB
mime-types:
  - binary/data-hipo
configuration:
  output_dir: ${ERSAP_USER_DATA}/out
  threads:    ${NUM_THREADS:-4}
```

Substitution is textual and happens before YAML parsing, so quote values that
may contain YAML-special characters (colons, `#`, leading `-`), e.g.
`path: "${SOME_PATH}"`.

---

## Observability

The observability stack has three components:

```
ERSAP DPEs ──dpeReport──▶  Monitor FE  ──▶  PrometheusExporter  ──▶  /metrics:9095
           ──userMetrics──▶  (:9000)                                       │
                                                                            ▼
                                                                       Prometheus
                                                                            │
                                                                            ▼
                                                                          Grafana
```

**1. Start the Monitor Front-End** (dedicated node or separate terminal):

The Monitor FE is a plain `j_dpe` process that acts as a collection proxy —
all other DPEs with `ERSAP_MONITOR_FE` set forward their reports to it.

```bash
j_dpe --host <monitor-ip> --port 9000 --session myrun
```

**2. Start the Prometheus exporter** (same node as Monitor FE):

`PrometheusExporter` subscribes to the Monitor FE over xMsg/ZeroMQ, converts
every DPE report and engine user-metric into Prometheus gauges and counters,
and serves them on `http://0.0.0.0:9095/metrics`.

```bash
java -cp "$ERSAP_HOME/lib/*" \
     org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --monitor-host <monitor-ip> --monitor-port 9000 \
     --session '*' --prometheus-port 9095
```

If `$ERSAP_MONITOR_FE` is already exported, no arguments are needed — the
exporter reads the host and port from the environment:

```bash
export ERSAP_MONITOR_FE="<monitor-ip>%9000_java"
java -cp "$ERSAP_HOME/lib/*" org.jlab.epsci.ersap.util.prometheus.PrometheusExporter
```

**3. Start Prometheus + Grafana** via Docker Compose:

```bash
# Edit docker/observability/prometheus/prometheus.yml — set target to <monitor-ip>:9095
cd docker/observability
docker compose up -d
open http://localhost:3000    # admin / changeme
```

The **ERSAP Overview** dashboard is auto-provisioned on startup. It shows 12
panels: total processed events, failure rate, DPEs alive, average execution
time, processing rate by service, success/failure, error rate, execution time
per service, CPU usage, memory usage, network bytes, and shared-memory
reads/writes. All panels are filterable by session and DPE via dropdowns.

Engine-published user metrics (`EngineMetricsPublisher.publish(key, value)`)
appear automatically as `ersap_user_<key>` series — no dashboard change needed
to chart them once they arrive.

### Adding an external `/metrics` endpoint

Any component that exposes a Prometheus-format `/metrics` endpoint can be
scraped alongside the ERSAP exporter — no code changes to ERSAP required.
Edit the Prometheus scrape config and append a new job under `scrape_configs`;
see [`docker/README.md`](docker/README.md#adding-an-external-metrics-endpoint)
for the full annotated reference (Docker Compose) or
[`HOWTO-perlmutter.md`](HOWTO-perlmutter.md#4-add-an-external-metrics-endpoint-to-the-monitors-prometheus)
for the Perlmutter (`ersap.yml`) variant.

---

## Perlmutter (NERSC) — multi-node Slurm deployment

### Scripts

All Slurm scripts live under `slurm/`. The two monitor scripts share a common
bash library (`slurm/lib/monitor-stack.sh`) that handles startup, supervision,
shutdown, and discovery file generation — each wrapper is ~50 lines of SBATCH
directives and configuration.

```
slurm/
├── monitor.slurm           regular QOS, exclusive cpu node, up to 8 h
├── monitor-longrun.slurm   workflow QOS, cron constraint, up to 30 days
├── processor.slurm         pipeline container, points at a remote monitor
├── allinone.slurm          monitor stack + pipeline on one node
└── lib/
    └── monitor-stack.sh    shared monitor logic (sourced, not run directly)
```

| Script | QOS | Use when |
|---|---|---|
| `slurm/monitor.slurm` | `regular` | ad hoc testing, short runs |
| `slurm/monitor-longrun.slurm` | `workflow` | production, persistent metrics (requires NERSC approval) |
| `slurm/processor.slurm` | `regular` | pipeline node pointing at a running monitor |
| `slurm/allinone.slurm` | `regular` | monitor + pipeline on one node |

**Port note**: port 9000 is busy on Perlmutter. All scripts use `19000` for
the Monitor FE — `j_dpe --port` and `--monitor-port` must always match.

### One-time setup (login node)

```bash
# Install Prometheus and Grafana under $HOME (shared with all compute nodes)
PROM_VER=2.53.0
wget https://github.com/prometheus/prometheus/releases/download/v${PROM_VER}/prometheus-${PROM_VER}.linux-amd64.tar.gz
tar xzf prometheus-${PROM_VER}.linux-amd64.tar.gz && mv prometheus-${PROM_VER}.linux-amd64 $HOME/prometheus
mkdir -p $HOME/prometheus/data

GRAF_VER=11.1.0
wget https://dl.grafana.com/oss/release/grafana-${GRAF_VER}.linux-amd64.tar.gz
tar xzf grafana-${GRAF_VER}.linux-amd64.tar.gz && mv grafana-v${GRAF_VER} $HOME/grafana
mkdir -p $HOME/grafana/{data,logs,plugins}

# Deploy Prometheus scrape config, Grafana config, and ERSAP Overview dashboard
bash ~/ersap-java/perlmutter-setup/deploy.sh

# Edit account line in each script if your NERSC repo is not amsc016
```

### Submit and operate

```bash
cd ~/ersap-java && mkdir -p logs

# Monitor only (pick one):
sbatch slurm/monitor-longrun.slurm          # production
sbatch --qos=debug --time=00:30:00 slurm/monitor.slurm  # debug

# Processor (monitor already running):
export MONITOR_ENV_FILE=$PWD/logs/monitor-<JOB_ID>/monitor.env
sbatch slurm/processor.slurm

# Both on one node:
sbatch slurm/allinone.slurm
```

After submission — discover the node and connect:

```bash
cat logs/monitor-<JOB_ID>/monitor-info.txt   # endpoints, PIDs, tunnel command
source logs/monitor-<JOB_ID>/monitor.env     # sets ERSAP_MONITOR_FE, ports

ssh -N -L 3000:<monitor-node>:3000 -L 9090:<monitor-node>:9090 \
    <user>@perlmutter.nersc.gov
# Grafana: http://localhost:3000  (admin / changeme)
```

The job prints `ERSAP monitor successfully started` only after all four
services (`j_dpe`, `PrometheusExporter`, Prometheus, Grafana) pass their
readiness checks. A failure at any step aborts the job.

All other settings (`ERSAP_HOME`, `SESSION`, `IMAGE`, ports) can be overridden
by exporting them before `sbatch` — see `HOWTO-perlmutter.md` for the full
overrides table, troubleshooting guide, and `monitor-stack.sh` phase reference.

---

## Docker

```bash
# Build (context must be the repo root)
docker build -t ersap-java -f docker/Dockerfile .

# Run a DPE
docker run --rm -it \
  -p 7771-7775:7771-7775 \
  -v "$PWD/data/input:/usr/local/ersap/data/input" \
  -v "$PWD/data/output:/usr/local/ersap/data/output" \
  -v "$PWD/log:/usr/local/ersap/log" \
  ersap-java j_dpe --host 0.0.0.0 --port 7771 --session myrun
```

See [`docker/README.md`](docker/README.md) for the full build/run walkthrough:
multi-stage build details, dev-stage image, running a shell instead of a
command, running the PrometheusExporter from the image, and the Docker Compose
observability stack.

---

## Further reading

| Document | What it covers |
|---|---|
| [`docker/README.md`](docker/README.md) | Building the ERSAP image, running containers, the complete Docker Compose monitoring stack, Grafana panel configuration |
| [`src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md`](src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) | PrometheusExporter reference: every option, the full metric catalogue, labels, filters, reconnection, alert rules |
| [`HOWTO-perlmutter.md`](HOWTO-perlmutter.md) | Perlmutter operations: `slurm/` structure, `monitor-stack.sh` phase reference, overrides table, troubleshooting, external scrape targets, `file_sd_configs` for ephemeral nodes |

---

Contact: [sro@jlab.org](mailto:sro@jlab.org)
