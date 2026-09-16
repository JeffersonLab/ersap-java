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

Edit `perlmutter-setup/prometheus/ersap.yml` and append a job under
`scrape_configs`:

```yaml
  - job_name: sagips-exporter
    metrics_path: /metrics        # omit if default
    scrape_interval: 15s          # omit to inherit global
    static_configs:
      - targets: ["<host>:<port>"]   # e.g. "nid001234:9200" or "localhost:9200"
        labels:
          pipeline: "mypipe"         # any labels you want on every series
          user:     "$USER"
```

Only `job_name` and `targets` are required. Add `basic_auth`, `scheme: https`,
or `tls_config` only if the endpoint needs them.

Redeploy and reload — no monitor restart needed:

```bash
bash ~/ersap-java/perlmutter-setup/deploy.sh          # copies edited ersap.yml into $HOME/prometheus/
curl -X POST http://<monitor-node>:9090/-/reload      # hot-reload Prometheus
```

Verify at `http://<monitor-node>:9090/targets` — the new job should be **UP**.
Metrics are then queryable in Grafana against the existing Prometheus
datasource (build a new dashboard/panel; the ERSAP overview dashboard only
shows `ersap_*` series).

**Ephemeral hosts**: if the exporter runs on a Slurm-allocated node whose
hostname changes per job, hard-coding it in `ersap.yml` won't scale — switch
that job to `file_sd_configs` instead (target JSON written to `$HOME` on
job start, deleted on exit).

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
