# Perlmutter SLURM — Quick HOWTO

Three single-node scripts. Pick one. See `README.md` for full details.

| Script | Purpose |
|---|---|
| `perlmutter-ersap-monitor.slurm`   | monitor stack only (`j_dpe` + exporter + Prometheus + Grafana) |
| `perlmutter-ersap-processor.slurm` | one pipeline container, reports to a **remote** monitor |
| `perlmutter-ersap-allinone.slurm`  | monitor **and** one pipeline on the same node |

---

## 0. One-time setup (login node)

```bash
# Install Prometheus and Grafana binaries under $HOME (see README §Perlmutter)
# Deploy their configs:
bash ~/ersap-java/perlmutter-setup/deploy.sh
```

Edit each `.slurm` file's `#SBATCH --account=` line if your NERSC repo is not `amsc016`.

---

## 1. Monitor only

```bash
cd ~/ersap-java && mkdir -p logs
sbatch perlmutter-ersap-monitor.slurm      # note the JOB_ID
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
