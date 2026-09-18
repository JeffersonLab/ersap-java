# Perlmutter SLURM — Quick HOWTO

Four single-node scripts. Pick one (or two, for monitor + processor). See `README.md` for full details.

| Script | Purpose |
|---|---|
| `perlmutter-ersap-monitor.slurm`          | monitor stack only, on a normal **exclusive** compute node (`j_dpe` + exporter + Prometheus + Grafana) |
| `perlmutter-ersap-monitor-longrun.slurm`  | the same monitor stack, but on a `workflow`-QOS **long-running** node — currently sized for 30 days |
| `perlmutter-ersap-processor.slurm`        | one pipeline container, reports to a **remote** monitor |
| `perlmutter-ersap-allinone.slurm`         | monitor **and** one pipeline on the same node |

---

## 0. One-time setup (login node)

No build of `ersap-java` needed: `git clone`/`pull` this repo for the scripts above plus `perlmutter-setup/` — nothing else here is read at run time. `ERSAP_HOME` (a pre-built ERSAP install, used by `monitor*.slurm`/`allinone.slurm`) and the `podman-hpc` pipeline image (used by `processor.slurm`/`allinone.slurm`, default `docker.io/gurjyan/pet-sro:v1`) are separate dependencies, not part of this repo.

```bash
# Install Prometheus and Grafana binaries under $HOME (see README §Perlmutter)
# Deploy their configs:
bash ~/ersap-java/perlmutter-setup/deploy.sh
```

Edit each `.slurm` file's `#SBATCH --account=` line if your NERSC repo is not `amsc016`.

---

## 1. Monitor only

Two ways to run the monitor stack, depending on what you need:

- **`perlmutter-ersap-monitor.slurm`** — a normal `regular`-QOS, `cpu`-constraint job that grabs an **exclusive** compute node (64 cpus) for up to 8h by default. Good for ad hoc testing/debugging of the monitor + Grafana/Prometheus stack.
- **`perlmutter-ersap-monitor-longrun.slurm`** — targets NERSC's `workflow` QOS with the `cron` constraint, the mechanism intended for persistent, lightweight, long-running services rather than exclusive compute nodes. It currently requests `--time=30-00:00:00` (30 days) and `--dependency=singleton` (so re-submitting under the same job name won't start a second copy). **Requires NERSC to have approved `workflow` QOS access for your account first.**

```bash
cd ~/ersap-java && mkdir -p logs
sbatch perlmutter-ersap-monitor-longrun.slurm      # note the JOB_ID
OR
sbatch --qos=debug --time=00:30:00 perlmutter-ersap-monitor.slurm
```

Wait for RUNNING, then:

```bash
cat logs/monitor-<JOB_ID>/monitor-info.txt        # discover node + endpoints
tail -f logs/monitor-<JOB_ID>.out                 # readiness banner
```

Tunnel from your laptop (also printed in `monitor-info.txt`):

```bash
ssh -N -L 3000:<monitor-node>:3000 -L 9090:<monitor-node>:9090 \
    <user>@perlmutter.nersc.gov
# Grafana http://localhost:3000  (admin/changeme)
```

Cancel: `scancel <JOB_ID>`

---

## 2. Processor only (monitor already running)

```bash
cd ~/ersap-java && mkdir -p logs
export MONITOR_ENV_FILE=$PWD/logs/monitor-<monitor-JOB_ID>/monitor.env
sbatch perlmutter-ersap-processor.slurm
```

Alternative: `export ERSAP_MONITOR_FE='<ip>%19000_java'` instead of `MONITOR_ENV_FILE`.

Follow: `tail -f logs/processor-<JOB_ID>/pipeline.log`
Cancel: `scancel <JOB_ID>`   (stops the container cleanly)

---

## 3. Monitor + pipeline on one node

```bash
cd ~/ersap-java && mkdir -p logs
sbatch perlmutter-ersap-allinone.slurm
```

Job exits when the pipeline finishes; monitor is torn down automatically.
`cat logs/allinone-<JOB_ID>/monitor-info.txt` for the tunnel command and log paths.

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
Prometheus datasource. The ERSAP overview dashboard only shows `ersap_*` series,
so build a new dashboard or panel for your external metrics.

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

| Var | Default | Applies to |
|---|---|---|
| `ERSAP_HOME`      | `/global/homes/g/gurjyan/work/ersap_installation/ersap_home` | monitor, allinone |
| `MONITOR_PORT`    | `19000` | monitor, allinone, processor |
| `SESSION`         | `test`  | all |
| `IMAGE`           | `docker.io/gurjyan/pet-sro:v1` | processor, allinone |
| `DATA_DIR`        | `/global/cfs/cdirs/amsc016/haidis/ersap-data` | processor, allinone |
| `SERVICES_FILE`   | `'$ERSAP_USER_DATA/config/pet_services.yaml'` (container-resolved) | processor, allinone |
| `PIPELINE_PULL`   | `1` (set `0` to skip `podman-hpc pull`) | processor, allinone |

---

## Cheat sheet

```bash
squeue -u $USER
scontrol show job <JOB_ID>
scontrol show hostnames "$(squeue -h -j <JOB_ID> -o '%N')"
squeue --start -j <JOB_ID>
sprio -j <JOB_ID>
scancel <JOB_ID>
```
