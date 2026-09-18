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
./gradlew deploy          # installs into $ERSAP_HOME
```

---

## Run a pipeline

```bash
export ERSAP_HOME=/path/to/ersap
export ERSAP_USER_DATA=/path/to/user/data
export ERSAP_MONITOR_FE="<monitor-ip>%9000_java"   # enables metric forwarding

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

**1. Start the Monitor Front-End** (dedicated node or separate terminal):

```bash
j_dpe --host <monitor-ip> --port 9000 --session myrun
```

**2. Start the Prometheus exporter** (same node as Monitor FE):

```bash
java -cp "$ERSAP_HOME/lib/*" \
     org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --monitor-host <monitor-ip> --monitor-port 9000 \
     --session '*' --prometheus-port 9095
```

**3. Start Prometheus + Grafana** via Docker Compose:

```bash
# Edit docker/observability/prometheus/prometheus.yml — set target to <monitor-ip>:9095
cd docker/observability
docker compose up -d
open http://localhost:3000    # admin / changeme
```

### Adding an external `/metrics` endpoint

Any component that exposes a Prometheus-format `/metrics` endpoint (custom
exporter, third-party service, another pipeline) can be scraped by the same
Prometheus instance — no code changes to ERSAP required.

The Prometheus configuration file is `docker/observability/prometheus/prometheus.yml`
(Docker Compose setup) or `perlmutter-setup/prometheus/ersap.yml` (Perlmutter).
Both ship with one job that covers the ERSAP PrometheusExporter:

```yaml
scrape_configs:
  - job_name: ersap-monitor
    metrics_path: /metrics
    scrape_interval: 15s
    static_configs:
      - targets: ["localhost:9095"]
```

Append an additional entry under `scrape_configs` for every external endpoint.

**Minimal entry** — only `job_name` and `targets` are required:

```yaml
  - job_name: my-exporter
    static_configs:
      - targets: ["<host>:<port>"]   # e.g. "nid001234:8000" or "localhost:9200"
```

`metrics_path` defaults to `/metrics` and `scrape_interval` inherits the global
value, so nothing else is needed for a plain HTTP endpoint on any port.

**Full annotated entry**:

```yaml
  - job_name: my-exporter

    # Path Prometheus calls on each target (default: /metrics).
    metrics_path: /metrics

    # Per-job overrides; omit to inherit the global values.
    scrape_interval: 15s
    scrape_timeout: 10s

    # Static target list. Multiple groups can carry different labels.
    static_configs:
      - targets:
          - "<host>:<port>"
        labels:
          pipeline: "mypipe"      # any key/value pairs added to every series

    # Only needed if the endpoint requires HTTP Basic Auth.
    # basic_auth:
    #   username: "user"
    #   password: "secret"

    # Only needed for HTTPS endpoints.
    # scheme: https
    # tls_config:
    #   insecure_skip_verify: true
```

`job_name` must be unique across all entries in the file.

**Reload without restarting Prometheus:**

```bash
curl -X POST http://<prometheus-host>:9090/-/reload
```

Verify the new target is **UP** at `http://<prometheus-host>:9090/targets`.
Metrics appear immediately in Grafana against the existing Prometheus datasource;
build a new dashboard or panel for them (the ERSAP overview dashboard shows only
`ersap_*` series).

**Perlmutter — ephemeral Slurm nodes**: if the exporter runs on an allocated node
whose hostname changes per job, use `file_sd_configs` instead of `static_configs`
— see `HOWTO-perlmutter.md` § 4 for the full pattern.

---

## Perlmutter (NERSC) — multi-node Slurm deployment

**One-time setup on the login node** (`$HOME` is the same global filesystem on all nodes):

```bash
# Prometheus
PROM_VER=2.53.0
wget https://github.com/prometheus/prometheus/releases/download/v${PROM_VER}/prometheus-${PROM_VER}.linux-amd64.tar.gz
tar xzf prometheus-${PROM_VER}.linux-amd64.tar.gz && mv prometheus-${PROM_VER}.linux-amd64 $HOME/prometheus
mkdir -p $HOME/prometheus/data

# Grafana
GRAF_VER=11.1.0
wget https://dl.grafana.com/oss/release/grafana-${GRAF_VER}.linux-amd64.tar.gz
tar xzf grafana-${GRAF_VER}.linux-amd64.tar.gz && mv grafana-v${GRAF_VER} $HOME/grafana
mkdir -p $HOME/grafana/{data,logs,plugins}
```

**Deploy config files** (once, from the login node):

```bash
bash ~/ersap-java/perlmutter-setup/deploy.sh
```

This copies the Prometheus scrape config, Grafana `custom.ini`, datasource,
dashboard provisioning, and the ERSAP Overview dashboard into the correct
`$HOME` locations.

**Submit a job.** Four single-node scripts are provided (see
`HOWTO-perlmutter.md` for a quick cheat sheet). None of them require building
`ersap-java` on Perlmutter — they only need `ERSAP_HOME` (built via `./gradlew
deploy`, see Build above) and, for the pipeline scripts, the `podman-hpc`
container image:

| Script | Purpose |
|---|---|
| `perlmutter-ersap-monitor.slurm`          | monitor stack only, on an exclusive `regular`-QOS compute node |
| `perlmutter-ersap-monitor-longrun.slurm`  | monitor stack only, on a `workflow`-QOS/`cron`-constraint node meant for long-running services (currently sized for 30 days; requires NERSC to approve `workflow` QOS for your account) |
| `perlmutter-ersap-processor.slurm`        | one pipeline container reporting to a remote monitor |
| `perlmutter-ersap-allinone.slurm`         | monitor **and** one pipeline on the same node |

Use port 19000 instead of 9000 (9000 is busy on Perlmutter); `j_dpe --port`
and `--monitor-port` must match.

### Monitor-only allocation (one compute node)

Use `perlmutter-ersap-monitor.slurm` (or `perlmutter-ersap-monitor-longrun.slurm`
for a persistent, `workflow`-QOS deployment) when you want the monitor stack
(`j_dpe` + `PrometheusExporter` + Prometheus + Grafana) to live in its own
SLURM job, independent of any processing-node allocation. Pipeline nodes
launched elsewhere point at it via `ERSAP_MONITOR_FE`. Everything below
applies to either script.

**Submit** (from the repo root — both scripts resolve paths from the
directory `sbatch` is run in, and `logs/` must exist before SLURM opens the
job's stdout file):

```bash
mkdir -p logs
sbatch perlmutter-ersap-monitor.slurm
# or: sbatch perlmutter-ersap-monitor-longrun.slurm
```

Edit the `#SBATCH --account=` line first if your NERSC repo is not `amsc016`.
All other settings (`ERSAP_HOME`, ports, session, timeouts) can be overridden
by exporting them before `sbatch`.

**Discover the allocated monitor node** — hostname discovery is a compute-node
operation, not a submit-host one:

```bash
squeue -j <job-id> -o "%.18i %.9P %.30j %.8u %.2t %.10M %.6D %R"
scontrol show job <job-id>
scontrol show hostnames "$(squeue -h -j <job-id> -o '%N')"
cat  logs/monitor-<job-id>/monitor-info.txt
source logs/monitor-<job-id>/monitor.env   # exposes MONITOR_HOST / ERSAP_MONITOR_FE / ports
```

`monitor-info.txt` records the SLURM job id, short hostname, FQDN, expanded
node list, endpoints, PIDs, launch commands, and log paths. `monitor.env` is
the machine-readable subset that pipeline nodes can `source`.

**Verify readiness.** The job only prints the *"ERSAP monitor successfully
started"* banner after every service is listening, `curl http://.../metrics`,
`/-/ready`, and `/api/health` all respond, `ersap_prometheus_exporter_up == 1`
(i.e. the exporter is attached to `j_dpe`), and no child has exited. A
failure at any step aborts the job with a non-zero exit code.

**Follow the logs** (on the login node — `$HOME` is shared with the compute
node):

```bash
tail -f logs/monitor-<job-id>.out
tail -f logs/monitor-<job-id>/j_dpe.log
tail -f logs/monitor-<job-id>/exporter.log
tail -f logs/monitor-<job-id>/prometheus.log
tail -f logs/monitor-<job-id>/grafana.log
```

**Inspect the running node** directly:

```bash
NODE=$(squeue -h -j <job-id> -o '%N')
ssh "$NODE" 'ss -tlnp | grep -E ":(19000|9095|9090|3000)"'
ssh "$NODE" 'ps -o pid,cmd -p $(pgrep -d, -u $USER -f "j_dpe|PrometheusExporter|prometheus|grafana-server")'
```

**Connect from your laptop** (the info file also prints this line):

```bash
ssh -N \
  -L 3000:<monitor-node>:3000 \
  -L 9090:<monitor-node>:9090 \
  <user>@perlmutter.nersc.gov
# Grafana:    http://localhost:3000  (admin / changeme)
# Prometheus: http://localhost:9090
```

**Shut down** — clean cancellation triggers the batch script's `SIGTERM`
trap, which sends `SIGTERM` to every captured child PID, waits up to 20 s,
then `SIGKILL`s stragglers, and writes `STATE=stopped` to the status file:

```bash
scancel <job-id>
```

---

## Docker

```bash
# Build
docker build -t ersap-java -f docker/Dockerfile .

# Run a DPE
docker run --rm -it \
  -p 7771-7775:7771-7775 \
  -v "$PWD/data/input:/usr/local/ersap/data/input" \
  -v "$PWD/data/output:/usr/local/ersap/data/output" \
  -v "$PWD/log:/usr/local/ersap/log" \
  ersap-java j_dpe --host 0.0.0.0 --port 7771 --session myrun
```

See [`docker/README.md`](docker/README.md) for the full build/run walkthrough
(dev-stage builds, running a shell instead, the exporter port, etc.).

---

Contact: [sro@jlab.org](mailto:sro@jlab.org)
