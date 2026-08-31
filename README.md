# ERSAP Java

Micro-services framework for distributed scientific data-stream processing.
Engines written in Java, C++, or Python are wired into pipelines via a YAML
services file and driven by an orchestrator over ZeroMQ.

---

## Build

Requires Java 14+.

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

---

## Perlmutter (NERSC) — multi-node Slurm deployment

```bash
# Edit the USER CONFIGURATION block at the top of the script, then:
sbatch perlmutter-ersap.sbatch
```

The job log prints `SLURM_NODELIST` and the SSH tunnel command needed to open
Grafana from your home computer. Use port 19000 instead of 9000 (9000 is busy
on Perlmutter); `j_dpe --port` and `--monitor-port` must match.

---

## Docker

```bash
# Build
docker build -t ersap-java -f docker/Dockerfile .

# Run a DPE
docker run --rm -it --network=host \
  -v "$PWD/data:/usr/local/ersap/data" \
  ersap-java j_dpe --host 0.0.0.0 --port 7771 --session myrun
```

---

Contact: [sro@jlab.org](mailto:sro@jlab.org)
