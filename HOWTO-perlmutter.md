# Perlmutter SLURM — Quick HOWTO

Four single-node scripts. Pick one (or two, for monitor + processor).

| Script | Purpose |
|---|---|
| `perlmutter-ersap-monitor.slurm`          | monitor stack only, on a normal **exclusive** compute node (`j_dpe` + exporter + Prometheus + Grafana) |
| `perlmutter-ersap-monitor-longrun.slurm`  | the same monitor stack, but on a `workflow`-QOS **long-running** node — currently sized for 30 days |
| `perlmutter-ersap-processor.slurm`        | one pipeline container, reports to a **remote** monitor |
| `perlmutter-ersap-allinone.slurm`         | monitor **and** one pipeline on the same node |

The monitor stack runs `j_dpe` (Monitor FE) + `PrometheusExporter` + Prometheus
+ Grafana. Pipeline nodes point at it via `ERSAP_MONITOR_FE`.

**Port note**: port 9000 is busy on Perlmutter. All scripts use port `19000`
for the Monitor FE. `j_dpe --port` and `--monitor-port` must always match.

---

## 0. One-time setup (login node)

`$HOME` is a global GPFS filesystem, the same on the login node and all compute
nodes, so binaries and configs installed here are immediately available inside
any Slurm job.

### Install Prometheus and Grafana binaries

```bash
# Prometheus
PROM_VER=2.53.0
wget https://github.com/prometheus/prometheus/releases/download/v${PROM_VER}/prometheus-${PROM_VER}.linux-amd64.tar.gz
tar xzf prometheus-${PROM_VER}.linux-amd64.tar.gz
mv prometheus-${PROM_VER}.linux-amd64 $HOME/prometheus
mkdir -p $HOME/prometheus/data

# Grafana
GRAF_VER=11.1.0
wget https://dl.grafana.com/oss/release/grafana-${GRAF_VER}.linux-amd64.tar.gz
tar xzf grafana-${GRAF_VER}.linux-amd64.tar.gz
mv grafana-v${GRAF_VER} $HOME/grafana
mkdir -p $HOME/grafana/{data,logs,plugins}
```

### Clone the repo and deploy configs

```bash
git clone https://github.com/JeffersonLab/ersap-java.git ~/ersap-java
bash ~/ersap-java/perlmutter-setup/deploy.sh
```

`deploy.sh` copies the Prometheus scrape config (`perlmutter-setup/prometheus/ersap.yml`),
Grafana `custom.ini`, datasource, dashboard provisioning, and the ERSAP Overview
dashboard into the correct `$HOME` locations. Re-run it any time you edit those
files.

### Edit the account line

Open each `.slurm` file and change `#SBATCH --account=amsc016` to your NERSC
allocation if it differs.

### Prerequisites not in this repo

- **`ERSAP_HOME`** — a pre-built ERSAP install (`./gradlew deploy` on your
  build machine). Required by `monitor*.slurm` and `allinone.slurm`.
- **`podman-hpc` image** — the pipeline container image (default
  `docker.io/gurjyan/pet-sro:v1`). Required by `processor.slurm` and
  `allinone.slurm`. Pre-pull with `podman-hpc pull <image>` on a login node if
  you want to skip the pull step inside the job.

---

## 1. Monitor only

Two variants — pick based on how long you need the stack to run:

- **`perlmutter-ersap-monitor.slurm`** — `regular` QOS, `cpu` constraint,
  exclusive compute node (64 CPUs), up to 8 h by default. Use for ad hoc
  testing and debugging.
- **`perlmutter-ersap-monitor-longrun.slurm`** — `workflow` QOS, `cron`
  constraint, lightweight long-running node, currently sized for 30 days.
  Uses `--dependency=singleton` so re-submitting under the same job name won't
  start a second copy. **Requires NERSC to have approved `workflow` QOS access
  for your account.**

### Submit

```bash
cd ~/ersap-java && mkdir -p logs

# long-running (preferred for production):
sbatch perlmutter-ersap-monitor-longrun.slurm

# short / debug:
sbatch --qos=debug --time=00:30:00 perlmutter-ersap-monitor.slurm
```

Note the `JOB_ID` printed by `sbatch`.

### Wait for readiness

```bash
# watch the job stdout — the readiness banner prints after all four services
# (j_dpe, PrometheusExporter, Prometheus, Grafana) are confirmed listening:
tail -f logs/monitor-<JOB_ID>.out
```

The banner reads `ERSAP monitor successfully started` and is only printed after
`/metrics`, `/-/ready`, `/api/health` all respond and
`ersap_prometheus_exporter_up == 1`. A failure at any step aborts the job with
a non-zero exit code.

### Discover the node and endpoints

```bash
cat logs/monitor-<JOB_ID>/monitor-info.txt
```

`monitor-info.txt` records: Slurm job id, short hostname, FQDN, expanded node
list, endpoints (`j_dpe`, exporter, Prometheus, Grafana), PIDs, launch
commands, log paths, and the SSH tunnel command.

```bash
# machine-readable subset — source this on processor nodes or in other scripts:
source logs/monitor-<JOB_ID>/monitor.env
# exports: MONITOR_HOST, ERSAP_MONITOR_FE, MONITOR_PORT, PROM_PORT, GRAFANA_PORT
```

### Connect from your laptop

The tunnel command is also printed in `monitor-info.txt`:

```bash
ssh -N \
  -L 3000:<monitor-node>:3000 \
  -L 9090:<monitor-node>:9090 \
  <user>@perlmutter.nersc.gov
# Grafana:    http://localhost:3000  (admin / changeme — change on first login)
# Prometheus: http://localhost:9090
```

### Follow the logs

```bash
tail -f logs/monitor-<JOB_ID>/j_dpe.log
tail -f logs/monitor-<JOB_ID>/exporter.log
tail -f logs/monitor-<JOB_ID>/prometheus.log
tail -f logs/monitor-<JOB_ID>/grafana.log
```

### Inspect the running node directly

```bash
NODE=$(squeue -h -j <JOB_ID> -o '%N')
ssh "$NODE" 'ss -tlnp | grep -E ":(19000|9095|9090|3000)"'
ssh "$NODE" 'ps -o pid,cmd -p $(pgrep -d, -u $USER -f "j_dpe|PrometheusExporter|prometheus|grafana-server")'
```

### Cancel

```bash
scancel <JOB_ID>
```

Clean cancellation triggers the batch script's `SIGTERM` trap, which stops all
child processes gracefully, waits up to 20 s, then `SIGKILL`s stragglers.

---

## 2. Processor only (monitor already running)

`perlmutter-ersap-processor.slurm` runs the pipeline container on a separate
compute node and points it at an already-running monitor stack.

### Submit

```bash
cd ~/ersap-java && mkdir -p logs

# Option A — point at a running monitor job via its env file:
export MONITOR_ENV_FILE=$PWD/logs/monitor-<monitor-JOB_ID>/monitor.env
sbatch perlmutter-ersap-processor.slurm

# Option B — set the monitor address directly:
export ERSAP_MONITOR_FE='<monitor-ip>%19000_java'
sbatch perlmutter-ersap-processor.slurm
```

`MONITOR_ENV_FILE` is sourced by the script at startup; it exports
`ERSAP_MONITOR_FE` (and the other monitor variables) so the pipeline DPE knows
where to send its reports. `ERSAP_MONITOR_FE` set directly has the same effect
and is useful when the monitor is not a job managed by this repo.

### Follow and cancel

```bash
tail -f logs/processor-<JOB_ID>/pipeline.log
scancel <JOB_ID>     # stops the container cleanly via SIGTERM
```

---

## 3. Monitor + pipeline on one node

`perlmutter-ersap-allinone.slurm` runs the full monitor stack and one pipeline
container on a single exclusive compute node. The job exits automatically when
the pipeline finishes; the monitor is torn down as part of the same cleanup
trap.

### Submit

```bash
cd ~/ersap-java && mkdir -p logs
sbatch perlmutter-ersap-allinone.slurm
```

### Readiness and logs

```bash
tail -f logs/allinone-<JOB_ID>.out                    # readiness banner
cat  logs/allinone-<JOB_ID>/monitor-info.txt          # tunnel command, endpoints
tail -f logs/allinone-<JOB_ID>/j_dpe.log
tail -f logs/allinone-<JOB_ID>/exporter.log
tail -f logs/allinone-<JOB_ID>/pipeline.log
```

The SSH tunnel command is the same as for the monitor-only case — substitute
the node from `monitor-info.txt`.

---

## 4. Add an external `/metrics` endpoint to the monitor's Prometheus

Any component that exposes a Prometheus-format `/metrics` endpoint (custom
exporter, third-party service, another pipeline) can be scraped by the
Prometheus running inside the monitor job — no code changes to ERSAP required.

### The file to edit

`perlmutter-setup/prometheus/ersap.yml` is the Prometheus configuration used on
Perlmutter. It ships with one job that covers the ERSAP PrometheusExporter:

```yaml
global:
  scrape_interval:     15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: ersap-monitor          # the ERSAP PrometheusExporter
    metrics_path: /metrics
    scrape_interval: 15s
    static_configs:
      - targets: ["localhost:9095"]
```

Append an additional entry under `scrape_configs` for every external endpoint
you want to scrape.

### Minimal entry — only `job_name` and `targets` are required

```yaml
  - job_name: my-exporter
    static_configs:
      - targets: ["nid001234:8000"]
```

`metrics_path` defaults to `/metrics` and `scrape_interval` inherits the global
`15s`, so nothing else is needed for a plain HTTP endpoint on any port.

### Full annotated entry

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
          - "nid001234:8000"      # Slurm compute node + port
          - "localhost:8000"      # or the same node as the monitor
        labels:
          pipeline: "mypipe"      # any key/value pairs added to every series
          user:     "gurjyan"

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

### Redeploy and reload — no monitor restart needed

```bash
# 1. copy the edited file into the running Prometheus data directory
bash ~/ersap-java/perlmutter-setup/deploy.sh

# 2. tell Prometheus to reload its config (no restart, no gap in data)
curl -X POST http://<monitor-node>:9090/-/reload

# 3. verify the new target appears as UP
# open http://<monitor-node>:9090/targets in a browser (via the SSH tunnel)
```

### Viewing the metrics in Grafana

The new job's metrics are immediately queryable in Grafana against the existing
Prometheus datasource. The ERSAP overview dashboard only shows `ersap_*` series.
To add panels for your external metrics — JSON format, PromQL patterns, unit IDs,
template variables — see [`docker/README.md § Grafana configuration`](docker/README.md#3-grafana-configuration).

### Ephemeral hosts

If the exporter runs on a Slurm-allocated node whose hostname changes per job,
hard-coding it in `ersap.yml` won't scale. Switch that job to `file_sd_configs`
instead: write the target JSON to `$HOME` when the job starts and delete it on
exit, then point `ersap.yml` at that file:

```yaml
  - job_name: my-exporter
    file_sd_configs:
      - files: ["/global/homes/g/gurjyan/my-exporter-targets.json"]
        refresh_interval: 30s
```

The target file format:

```json
[{"targets": ["nid001234:8000"], "labels": {"pipeline": "mypipe"}}]
```

---

## Common overrides (export before `sbatch`)

| Var | Default | Applies to | What it controls |
|---|---|---|---|
| `ERSAP_HOME`      | `/global/homes/g/gurjyan/work/ersap_installation/ersap_home` | monitor, allinone | path to the pre-built ERSAP install |
| `MONITOR_PORT`    | `19000` | monitor, allinone, processor | `j_dpe --port` and `--monitor-port`; must match on all nodes |
| `SESSION`         | `test`  | all | ERSAP session tag applied to DPE reports and user metrics |
| `IMAGE`           | `docker.io/gurjyan/pet-sro:v1` | processor, allinone | `podman-hpc` container image for the pipeline |
| `DATA_DIR`        | `/global/cfs/cdirs/amsc016/haidis/ersap-data` | processor, allinone | host directory mounted into the container as the data root |
| `SERVICES_FILE`   | `'$ERSAP_USER_DATA/config/pet_services.yaml'` (container-resolved) | processor, allinone | services YAML path, resolved inside the container |
| `PIPELINE_PULL`   | `1` | processor, allinone | set `0` to skip `podman-hpc pull` when the image is already cached |
| `MONITOR_ENV_FILE` | — | processor | path to `monitor.env` from a running monitor job; sets `ERSAP_MONITOR_FE` automatically |

---

## Troubleshooting

| Symptom | Check |
|---|---|
| Job stays in PENDING | `squeue --start -j <JOB_ID>` for estimated start time; `sprio -j <JOB_ID>` for priority |
| Readiness banner never prints | `tail logs/monitor-<JOB_ID>.out` — the script logs each startup step; look for the first failure |
| `ersap_prometheus_exporter_up == 0` | The exporter can't reach the Monitor FE. Check `exporter.log`; verify `j_dpe` started on port `19000` |
| Prometheus target DOWN | `curl http://<node>:9095/metrics` from the monitor node to isolate network vs config |
| Grafana empty after tunnel | Confirm the tunnel is up (`ss -tlnp` locally); check the `$prefix` variable in the dashboard matches `--metric-prefix` (default `ersap`) |
| `workflow` QOS job rejected | NERSC must approve `workflow` QOS for your account; use `perlmutter-ersap-monitor.slurm` until then |
| Container fails to start | Check `pipeline.log`; re-run with `PIPELINE_PULL=1` if the image may be stale |

---

## Cheat sheet

```bash
# job status
squeue -u $USER
scontrol show job <JOB_ID>
scontrol show hostnames "$(squeue -h -j <JOB_ID> -o '%N')"
squeue --start -j <JOB_ID>
sprio -j <JOB_ID>
scancel <JOB_ID>

# discover monitor endpoints
cat logs/monitor-<JOB_ID>/monitor-info.txt
source logs/monitor-<JOB_ID>/monitor.env

# follow logs
tail -f logs/monitor-<JOB_ID>.out
tail -f logs/monitor-<JOB_ID>/j_dpe.log
tail -f logs/monitor-<JOB_ID>/exporter.log
tail -f logs/monitor-<JOB_ID>/prometheus.log
tail -f logs/monitor-<JOB_ID>/grafana.log

# inspect the running node
NODE=$(squeue -h -j <JOB_ID> -o '%N')
ssh "$NODE" 'ss -tlnp | grep -E ":(19000|9095|9090|3000)"'
ssh "$NODE" 'ps -o pid,cmd -p $(pgrep -d, -u $USER -f "j_dpe|PrometheusExporter|prometheus|grafana-server")'

# SSH tunnel (also printed in monitor-info.txt)
ssh -N -L 3000:<monitor-node>:3000 -L 9090:<monitor-node>:9090 <user>@perlmutter.nersc.gov

# Prometheus hot-reload after editing ersap.yml
bash ~/ersap-java/perlmutter-setup/deploy.sh
curl -X POST http://<monitor-node>:9090/-/reload
```
