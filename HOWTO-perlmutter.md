# Perlmutter SLURM — Quick HOWTO

Four single-node scripts. Pick one (or two, for monitor + processor). See `README.md` for full details.

| Script | Purpose |
|---|---|
| `perlmutter-ersap-monitor.slurm`          | monitor stack only, on a normal **exclusive** compute node (`j_dpe` + exporter + Prometheus + Grafana) |
| `perlmutter-ersap-monitor-longrun.slurm`  | the same monitor stack, but on a `workflow`-QOS **long-running** node — currently sized for 30 days |
| `perlmutter-ersap-processor.slurm`        | one pipeline container, reports to a **remote** monitor |
| `perlmutter-ersap-allinone.slurm`         | monitor **and** one pipeline on the same node |

None of these require building `ersap-java` — see §0 below.

---

## 0. Getting the scripts (no build required)

The scripts don't need a build of `ersap-java`, and they don't read anything else out of this repo at run time. All they need on the Perlmutter side is:

```
perlmutter-ersap-monitor.slurm
perlmutter-ersap-monitor-longrun.slurm
perlmutter-ersap-processor.slurm      # only if you'll run a pipeline against a remote monitor
perlmutter-ersap-allinone.slurm       # only if you'll run monitor + pipeline together
perlmutter-setup/
```

So it's enough to `git clone` (or `git pull` to update) just this repo onto the login node — no `gradle`/`./gradlew build` step. The actual ERSAP runtime the scripts launch comes from two separate places:

- `ERSAP_HOME` — a pre-built ERSAP install on the login/compute node (used by `monitor*.slurm` and `allinone.slurm` to run `j_dpe` and the `PrometheusExporter`)
- the `podman-hpc` container image (`IMAGE`, default `docker.io/gurjyan/pet-sro:v1`) — used by `processor.slurm` and `allinone.slurm`, and bundles its own ERSAP runtime internally

Neither of those is part of this git checkout.

## 1. One-time setup (login node)

```bash
# Install Prometheus and Grafana binaries under $HOME (see README §Perlmutter)
# Deploy their configs:
bash ~/ersap-java/perlmutter-setup/deploy.sh
```

Edit each `.slurm` file's `#SBATCH --account=` line if your NERSC repo is not `amsc016`.

---

## 2. Monitor only

All three scripts resolve paths (logs, discovery files) from the directory you run `sbatch` from (`SLURM_SUBMIT_DIR`) — always `cd` into your `ersap-java` checkout (or wherever you want `logs/` to live) first, exactly as shown below.

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

## 3. Processor only (monitor already running)

```bash
cd ~/ersap-java && mkdir -p logs
export MONITOR_ENV_FILE=$PWD/logs/monitor-<monitor-JOB_ID>/monitor.env
sbatch perlmutter-ersap-processor.slurm
```

Alternative: `export ERSAP_MONITOR_FE='<ip>%19000_java'` instead of `MONITOR_ENV_FILE`.

Follow: `tail -f logs/processor-<JOB_ID>/pipeline.log`
Cancel: `scancel <JOB_ID>`   (stops the container cleanly)

---

## 4. Monitor + pipeline on one node

```bash
cd ~/ersap-java && mkdir -p logs
sbatch perlmutter-ersap-allinone.slurm
```

Job exits when the pipeline finishes; monitor is torn down automatically.
`cat logs/allinone-<JOB_ID>/monitor-info.txt` for the tunnel command and log paths.

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
