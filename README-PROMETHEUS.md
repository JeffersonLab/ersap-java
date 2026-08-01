# ERSAP monitoring stack — three-node deployment guide

Step-by-step procedure to run two ERSAP pipelines on two nodes, publish their metrics to a
Monitor Front End on a third node, and view them in Grafana.

```
  Node A  ────────┐
  pipelineA       │  dpeReport + userMetrics
                  ├──────▶  Node C : Monitor FE (:9000)
  Node B  ────────┘                      │
  pipelineB                              ▼  subscribe
                                 PrometheusExporter (:9095/metrics)
                                         │  scrape
                                         ▼
                                   Prometheus (:9090)
                                         │  query
                                         ▼
                                    Grafana (:3000)  ◀── your browser
```

---

## 0. Values you must replace

Every command below uses these placeholders. Substitute your own before running anything.

| Placeholder | Example used in this guide | What it is |
|---|---|---|
| `<NODE_A_IP>` | `10.11.1.10` | node running ERSAP pipeline 1 |
| `<NODE_B_IP>` | `10.11.1.11` | node running ERSAP pipeline 2 |
| `<NODE_C_IP>` | `10.11.1.12` | node running Monitor FE, exporter, Prometheus, Grafana |
| `<ERSAP_HOME>` | `/opt/ersap` | ERSAP installation (`./gradlew deploy` target) |
| `<ERSAP_USER_DATA>` | `/data/ersap` | per-user ERSAP data/config directory |
| `<PIPELINE_A_YAML>` | `$ERSAP_USER_DATA/config/pipeline-a.yml` | services file for pipeline 1 |
| `<PIPELINE_B_YAML>` | `$ERSAP_USER_DATA/config/pipeline-b.yml` | services file for pipeline 2 |
| `<FILE_LIST>` | `$ERSAP_USER_DATA/config/files.txt` | input file list |

> **Use IP addresses, not hostnames.** ERSAP canonical DPE names only accept dotted-decimal
> addresses. `hostname -I` (Linux) or `ipconfig getifaddr en0` (macOS) gives you the right value.
> Do **not** use `localhost` — ERSAP resolves it to the machine's network IP, which may not
> match what other nodes expect.

**Sessions must be alphanumeric.** `ersap-shell` validates `session` against `[0-9A-Za-z]+`,
so `pipelineA` is valid and `pipeline-a` is not. This guide uses `pipelineA` and `pipelineB`;
the distinct sessions are what let you separate the two pipelines in Grafana.

---

## 1. Prerequisites

### All three nodes

| Requirement | Notes |
|---|---|
| Java 17 | the project toolchain |
| ERSAP installed | `./gradlew deploy` with `ERSAP_HOME` set; or copy the built `lib/` tree |
| Clocks in sync | NTP/chrony — Prometheus correlates by timestamp across nodes |

### Node C only

| Requirement | Notes |
|---|---|
| Prometheus ≥ 2.x | https://prometheus.io/download/ |
| Grafana ≥ 9 | https://grafana.com/get |

### Files from this repository

| File | Used on | Purpose |
|---|---|---|
| `src/main/java/org/jlab/epsci/ersap/util/prometheus/prometheus/prometheus-example.yml` | Node C | scrape config |
| `src/main/java/org/jlab/epsci/ersap/util/prometheus/grafana/ersap-monitor-dashboard.json` | Node C | Grafana dashboard |
| `src/main/java/org/jlab/epsci/ersap/util/prometheus/config/prometheus-exporter.example.properties` | Node C | optional exporter config file |
| `src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md` | — | full exporter reference |

### Ports

Verified by inspecting what the processes actually bind.

| Port | Node | Bound by | Who connects |
|---|---|---|---|
| **9000/tcp** | C | Monitor FE proxy — publisher socket | **Nodes A and B — must be open inbound** |
| 9001/tcp | C | Monitor FE proxy — subscriber socket | the exporter (local unless you move it) |
| 9002/tcp | C | Monitor FE proxy — control | local |
| 9004/tcp | C | Monitor FE registrar (`9000 + 4`) | local |
| 9095/tcp | C | Prometheus exporter `/metrics` | Prometheus (local) |
| 9090/tcp | C | Prometheus UI/API | your browser |
| 3000/tcp | C | Grafana UI | your browser |
| 7771–7775/tcp | A, B | each pipeline's own DPE | local to each node |

A Monitor FE started with `--port 9000` binds **9000, 9001, 9002 and 9004**.

**Minimum firewall rule — on Node C:**

```bash
# Node C — allow the two pipeline nodes to publish metrics
sudo firewall-cmd --permanent --add-rich-rule \
  'rule family="ipv4" source address="10.11.1.10/32" port port="9000" protocol="tcp" accept'
sudo firewall-cmd --permanent --add-rich-rule \
  'rule family="ipv4" source address="10.11.1.11/32" port port="9000" protocol="tcp" accept'
sudo firewall-cmd --reload

# ufw equivalent
sudo ufw allow from 10.11.1.10 to any port 9000 proto tcp
sudo ufw allow from 10.11.1.11 to any port 9000 proto tcp
```

Only **9000/tcp** must be reachable from Nodes A and B: the pipelines are publishers and
connect to the proxy's publisher socket. Open 9001 as well only if you run the exporter on
a different node than the Monitor FE. Ports 9090 and 3000 need to be reachable from
wherever you browse.

**Verify network access before going further** (from A and B):

```bash
# Node A and Node B
nc -zv 10.11.1.12 9000
```

Expected: `Connection to 10.11.1.12 9000 port [tcp/*] succeeded!`
If this fails, nothing downstream will work — fix routing or the firewall first.

---

## 2. Startup order

Start in this order. Each step's verification must pass before you continue.

| # | Node | Component |
|---|---|---|
| 1 | **C** | Monitor Front End |
| 2 | **C** | Prometheus exporter |
| 3 | **C** | Prometheus |
| 4 | **C** | Grafana |
| 5 | **A** | pipeline 1 |
| 6 | **B** | pipeline 2 |

The Monitor FE must exist before the pipelines start, or their first reports are lost. The
exporter and the pipelines are otherwise order-independent — the exporter reconnects on its
own and never has to be restarted because a pipeline was.

---

## 3. Node C — start the Monitor Front End

The Monitor FE is a standalone front-end DPE that acts purely as a message broker for
monitoring traffic. It is a front end because no `--fe-host` is given.

```bash
# ===== NODE C =====
export ERSAP_HOME=/opt/ersap
export PATH="$ERSAP_HOME/bin:$PATH"

j_dpe --host 10.11.1.12 --port 9000 --session mon
```

> **`--port 9000` is mandatory.** `DataRingAddress` — used by the exporter and by
> `TestMonitor` — always connects to `MONITOR_PORT = 9000`. Omit it and the proxy starts on
> 7771 and the exporter sees nothing.

Expected output:

```
==========================================
               ERSAP FE/DPE
==========================================
 Name             = 10.11.1.12%9000_java
 Session          = mon
 Lang             = Java
 Proxy Host       = 10.11.1.12
 Proxy Port       = 9000
==========================================
```

**Verify** (Node C, second terminal):

```bash
ss -lntp | grep -E ':900[0-9]'
```

Expected: listeners on `9000`, `9001`, `9002` and `9004`.

Leave this terminal running, or start it under systemd/`tmux`.

---

## 4. Node C — start the Prometheus exporter

The exporter subscribes to the Monitor FE with the same mechanism `TestMonitor` uses —
`MonitorOrchestrator` over a `DataRingAddress` — on the `dpeReport` and `userMetrics` topics.

`--session '*'` subscribes to **every** session, which is what lets one exporter serve both
pipelines. Quote the star so the shell does not expand it.

```bash
# ===== NODE C =====
export ERSAP_HOME=/opt/ersap

java -cp "$ERSAP_HOME/lib/*" \
     org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --monitor-host 10.11.1.12 \
     --monitor-port 9000 \
     --session '*' \
     --prometheus-host 0.0.0.0 \
     --prometheus-port 9095 \
     --metric-prefix ersap
```

Expected startup log:

```
INFO MetricsHttpServer - serving Prometheus metrics on http://0.0.0.0:9095/metrics
INFO MonitorFeSubscriber - connecting to Monitor FE at 10.11.1.12:9000 (session=*)
Subscribed to all DPE reports
Subscribed to all user engine metrics
INFO MonitorFeSubscriber - subscribed to Monitor FE dpeReport and userMetrics topics
INFO PrometheusExporter - ERSAP Prometheus exporter started [...]
```

**Verify the endpoint** (Node C):

```bash
curl -s http://localhost:9095/health
curl -s http://localhost:9095/metrics | grep ersap_prometheus_exporter_up
```

Expected:

```
{"status":"ok","running":true,"monitorFeSubscribed":true}
ersap_prometheus_exporter_up 1.0
```

At this point only exporter self-metrics exist — no pipeline is running yet, so
`ersap_prometheus_exporter_registered_metrics` is `0`. That is correct.

<details>
<summary>Alternatives: config file, single session, filtering</summary>

```bash
# from a properties file (see config/prometheus-exporter.example.properties)
java -cp "$ERSAP_HOME/lib/*" org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --config /etc/ersap/prometheus-exporter.properties

# one exporter per pipeline, on separate ports, instead of one with --session '*'
... PrometheusExporter --session pipelineA --prometheus-port 9095
... PrometheusExporter --session pipelineB --prometheus-port 9096

# drop the Monitor FE's own self-reports (see the note in section 8)
... PrometheusExporter --session '*' --exclude 'ersap_container_*'
```

Run `PrometheusExporter --help` for every option, or see the exporter
[README](src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md).
</details>

### Optional: run it as a service

```bash
# ===== NODE C =====
sudo tee /etc/systemd/system/ersap-prometheus-exporter.service >/dev/null <<'EOF'
[Unit]
Description=ERSAP Monitor FE Prometheus exporter
After=network-online.target

[Service]
Type=simple
User=ersap
Environment=ERSAP_HOME=/opt/ersap
ExecStart=/usr/bin/java -cp /opt/ersap/lib/* \
  org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
  --monitor-host 10.11.1.12 --monitor-port 9000 --session * \
  --prometheus-port 9095
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now ersap-prometheus-exporter
sudo systemctl status ersap-prometheus-exporter
```

---

## 5. Node C — start Prometheus

```bash
# ===== NODE C =====
sudo mkdir -p /etc/prometheus
sudo tee /etc/prometheus/prometheus.yml >/dev/null <<'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "ersap-monitor"
    metrics_path: /metrics
    static_configs:
      - targets:
          - "localhost:9095"
EOF

prometheus --config.file=/etc/prometheus/prometheus.yml \
           --storage.tsdb.path=/var/lib/prometheus \
           --web.listen-address=0.0.0.0:9090
```

The repository ships a richer version of this file, including suggested alert rules:
`src/main/java/org/jlab/epsci/ersap/util/prometheus/prometheus/prometheus-example.yml`.

Do not scrape faster than the DPE report period (10 s by default) — you would only re-read
the same samples.

**Verify** (Node C):

```bash
# the target must be UP
curl -s 'http://localhost:9090/api/v1/targets' \
  | grep -o '"health":"[a-z]*"'

# and the exporter must be reporting itself as connected
curl -s 'http://localhost:9090/api/v1/query?query=ersap_prometheus_exporter_up' \
  | grep -o '"value":\[[^]]*\]'
```

Expected: `"health":"up"` and a value of `"1"`.

---

## 6. Node C — start Grafana

```bash
# ===== NODE C =====
sudo systemctl enable --now grafana-server
sudo systemctl status grafana-server
```

**Browser URL:**

```
http://10.11.1.12:3000
```

Default login is `admin` / `admin`; Grafana asks you to change it on first sign-in.

### 6a. Add the Prometheus data source

**Configuration → Data sources → Add data source → Prometheus**, set the URL to
`http://localhost:9090`, then **Save & test**. Expected: *Successfully queried the
Prometheus API.*

Or provision it:

```bash
# ===== NODE C =====
sudo tee /etc/grafana/provisioning/datasources/prometheus.yml >/dev/null <<'EOF'
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://localhost:9090
    isDefault: true
EOF
sudo systemctl restart grafana-server
```

### 6b. Import the ERSAP dashboard

**Option 1 — the UI.** Copy
`src/main/java/org/jlab/epsci/ersap/util/prometheus/grafana/ersap-monitor-dashboard.json`
to the machine you browse from, then in Grafana go to **Dashboards → New → Import → Upload
dashboard JSON file**, pick the file, select your Prometheus data source when prompted, and
click **Import**.

The dashboard declares a `datasource` template variable rather than a hard-coded UID, so it
imports cleanly into any Grafana instance.

**Option 2 — provisioning.**

```bash
# ===== NODE C =====
sudo mkdir -p /var/lib/grafana/dashboards
sudo cp /path/to/ersap-java/src/main/java/org/jlab/epsci/ersap/util/prometheus/grafana/ersap-monitor-dashboard.json \
        /var/lib/grafana/dashboards/

sudo tee /etc/grafana/provisioning/dashboards/ersap.yml >/dev/null <<'EOF'
apiVersion: 1
providers:
  - name: 'ersap'
    orgId: 1
    folder: 'ERSAP'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
EOF

sudo systemctl restart grafana-server
```

**Verify:** the dashboard **ERSAP Monitor FE** appears under **Dashboards**. Open it — the
*Exporter connection status* panel should already read **Connected**, and *Registered
metrics* should read **0** until the pipelines start.

---

## 7. Node A — start pipeline 1

Point the pipeline at Node C with `set monHost`. That is the reliable way: `ersap-shell`
builds the canonical name `10.11.1.12%9000_java` for you, so the port is always right.

```bash
# ===== NODE A =====
export ERSAP_HOME=/opt/ersap
export ERSAP_USER_DATA=/data/ersap
export PATH="$ERSAP_HOME/bin:$PATH"

ersap-shell
```

Inside the shell:

```
ersap> set monHost      10.11.1.12
ersap> set session      pipelineA
ersap> set servicesFile $ERSAP_USER_DATA/config/pipeline-a.yml
ersap> set fileList     $ERSAP_USER_DATA/config/files.txt
ersap> set inputDir     $ERSAP_USER_DATA/data/input
ersap> set outputDir    $ERSAP_USER_DATA/data/output
ersap> run local
```

`set monHost` makes `run local` inject `ERSAP_MONITOR_FE=10.11.1.12%9000_java` into the
environment of the DPE it spawns. Every DPE report and every engine metric is then
duplicated to Node C.

Confirm your settings before running:

```
ersap> get monHost
ersap> get session
```

**Verify on Node A** — the DPE log must contain the monitoring line:

```bash
# ===== NODE A =====
grep "Using monitoring front-end" $ERSAP_USER_DATA/log/*.log
```

Expected: `Using monitoring front-end 10.11.1.12%9000_java`

If that line is absent, the DPE is not publishing to Node C — recheck `set monHost`.

<details>
<summary>Alternative: a manually started DPE</summary>

If you start the DPE yourself rather than through `run local`, export the variable
explicitly — and write the `%9000` yourself, since nothing fills it in for you:

```bash
# ===== NODE A =====
export ERSAP_MONITOR_FE="10.11.1.12%9000_java"
j_dpe --host 10.11.1.10 --port 7771 --session pipelineA
```

`ERSAP_MONITOR_FE` must be exported **before** the process starts. Setting it after
`ersap-shell` is running has no effect on already-spawned DPEs.
</details>

---

## 8. Node B — start pipeline 2

Identical, with Node B's own address, its own services file, and a **different session**.

```bash
# ===== NODE B =====
export ERSAP_HOME=/opt/ersap
export ERSAP_USER_DATA=/data/ersap
export PATH="$ERSAP_HOME/bin:$PATH"

ersap-shell
```

```
ersap> set monHost      10.11.1.12
ersap> set session      pipelineB
ersap> set servicesFile $ERSAP_USER_DATA/config/pipeline-b.yml
ersap> set fileList     $ERSAP_USER_DATA/config/files.txt
ersap> set inputDir     $ERSAP_USER_DATA/data/input
ersap> set outputDir    $ERSAP_USER_DATA/data/output
ersap> run local
```

```bash
# ===== NODE B =====
grep "Using monitoring front-end" $ERSAP_USER_DATA/log/*.log
```

Expected: `Using monitoring front-end 10.11.1.12%9000_java`

> The two sessions do not have to differ — the `host` and `dpe` labels already separate the
> nodes. Distinct sessions simply give you a cleaner, more meaningful dashboard filter.

---

## 9. Verify metrics from **both** pipelines

Wait one DPE report period (~10 s), then, on Node C:

```bash
# ===== NODE C =====
# 1. both sessions are present
curl -s http://localhost:9095/metrics | grep '^ersap_dpe_cpu_usage_percent'
```

Expected — one line per DPE, with distinct `host` and `session` labels:

```
ersap_dpe_cpu_usage_percent{component="dpe",source="dpe_report",dpe="10.11.1.10_java",host="10.11.1.10",lang="java",session="pipelineA",} 38.4
ersap_dpe_cpu_usage_percent{component="dpe",source="dpe_report",dpe="10.11.1.11_java",host="10.11.1.11",lang="java",session="pipelineB",} 41.2
ersap_dpe_cpu_usage_percent{component="dpe",source="dpe_report",dpe="10.11.1.12%9000_java",host="10.11.1.12",lang="java",session="mon",} 2.1
```

> The third line is the **Monitor FE reporting on itself**. That is expected — it is a DPE
> too. Ignore it, filter it out in Grafana with the `session` variable, or keep it to watch
> the broker's own health.

```bash
# ===== NODE C =====
# 2. exactly two pipeline sessions are publishing
curl -s http://localhost:9095/metrics \
  | grep '^ersap_dpe_cores' | grep -o 'session="[^"]*"' | sort -u

# 3. per-service counters exist
curl -s http://localhost:9095/metrics | grep '^ersap_service_requests_total' | head

# 4. engine metrics from EngineMetricsPublisher
curl -s http://localhost:9095/metrics | grep '^ersap_user_'

# 5. the exporter is healthy and has discovered metrics
curl -s http://localhost:9095/metrics \
  | grep -E 'exporter_(up|messages_received_total|registered_metrics|metric_parse_errors_total)'
```

Expected for (2): `session="mon"`, `session="pipelineA"`, `session="pipelineB"`.
Expected for (5): `up` is `1`, `messages_received_total{source="dpe_report"}` is climbing,
`registered_metrics` is well above 0, and `metric_parse_errors_total` is `0`.

**Cross-check against `TestMonitor`.** The exporter and `TestMonitor` read the same messages
from the same proxy, so `TestMonitor` is the fastest way to tell whether a problem is in the
exporter or upstream of it:

```bash
# ===== NODE C =====
java -cp "$ERSAP_HOME/lib/*:$ERSAP_HOME/services/*" \
     org.jlab.epsci.ersap.examples.TestMonitor 10.11.1.12 pipelineA
```

If `TestMonitor` prints nothing either, the pipelines are not reaching the Monitor FE and
the exporter is not at fault.

Then confirm Prometheus has ingested it:

```bash
# ===== NODE C =====
curl -s --get 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=count by (session) (ersap_dpe_cores)'
```

Expected: a result entry for each of `pipelineA`, `pipelineB` (and `mon`).

---

## 10. Display both pipelines in Grafana

1. Open `http://10.11.1.12:3000` and go to **Dashboards → ERSAP Monitor FE**.
2. Set the variables in the top bar:
   * **Data source** — your Prometheus.
   * **Metric prefix** — `ersap` (must match `--metric-prefix`; leave as-is unless you changed it).
   * **Session** — open the picker and select **pipelineA** and **pipelineB** (deselect
     `mon` to hide the Monitor FE's own metrics).
   * **Host** — leave on **All**, or pick `10.11.1.10` and `10.11.1.11`.
   * **Component / Container / Engine / Service** — leave on **All** to start.
3. Pick what to plot in the **Metric** variable. It is populated by querying Prometheus, so
   it lists every metric the exporter has actually published — including engine metrics that
   did not exist when the dashboard was written. Good starting selections:
   * `ersap_dpe_cpu_usage_percent` — CPU of both pipeline nodes;
   * `ersap_user_event_rate_hz` — engine throughput, if your engines publish it;
   * `ersap_service_requests_total` — then read it in the **rate of change** panel.
4. Read the panels:
   * **Selected metrics over time** — raw values; one series per pipeline.
   * **Selected metrics, current value** — latest value per series.
   * **Selected metrics, rate of change** — use this for anything ending in `_total`;
     counters are cumulative, so their raw graph only ever climbs.
   * The **Exporter health** row shows connection status, message rate, processing errors,
     time since the last message, and how many metrics are registered.
5. Set the time range to **Last 15 minutes** and the refresh to **30s** (top right).

You should now see two distinct series per metric, one per pipeline, separated by the
`session` and `host` labels.

To confirm the two pipelines are genuinely distinguishable, use **Explore** with:

```promql
ersap_dpe_cpu_usage_percent{session=~"pipelineA|pipelineB"}
```

Expected: two series, labelled with the two different hosts and sessions.

---

## 11. Troubleshooting

Work top-down — each row assumes the ones above it passed.

| Symptom | Cause | Fix |
|---|---|---|
| `nc -zv <NODE_C_IP> 9000` fails from A or B | firewall or routing | Open **9000/tcp** inbound on Node C from both pipeline nodes (section 1). Confirm with `ss -lntp \| grep 9000` on C that something is actually listening. |
| Monitor FE log shows `Proxy Port = 7771` | `--port 9000` was omitted | Restart it with `--port 9000`. The exporter only ever connects to 9000. |
| `ersap_prometheus_exporter_up` is `0` | exporter cannot subscribe | Read the exporter log — it prints the address it tried. Check the Monitor FE is running and that `--monitor-host`/`--monitor-port` match it. The exporter retries every `--reconnect-interval` seconds for ever; you do not need to restart it. |
| `up` is `1` but `messages_received_total` stays `0` | nothing is publishing, or the session filter excludes everything | Most common cause by far. Check `--session`: the default is `test`, so if your pipelines use `pipelineA`/`pipelineB` you must pass `--session '*'` or the exact name. Then confirm the pipelines log `Using monitoring front-end`. |
| Pipeline log has no `Using monitoring front-end` line | `monHost` / `ERSAP_MONITOR_FE` not set when the DPE started | Set it **before** starting, then restart the pipeline. In `ersap-shell` use `set monHost <NODE_C_IP>` and confirm with `get monHost`. |
| `ERSAP_MONITOR_FE` set but still nothing | wrong port in the canonical name | It must be `<NODE_C_IP>%9000_java`. Without `%9000` it defaults to 7771 and reports go nowhere. `set monHost` avoids this entirely. |
| Only one pipeline appears | session filter, or both pipelines share a session | With `--session '*'` both always arrive. If you filtered to one session, that is expected. If both use the same session they still both appear, separated by the `host` label. |
| `ersap_dpe_*` present but no `ersap_user_*` | engines are not publishing user metrics | Engine code must call `EngineMetricsPublisher.publish(key, value)`, and its DPE must have `ERSAP_MONITOR_FE` set — the publisher is a no-op otherwise. |
| `metric_parse_errors_total` climbing | an engine publishes non-numeric values | Non-numeric strings, arrays and nulls are not exportable. Run the exporter with `--log-level debug` to see which keys. |
| A metric is missing from `/metrics` | include/exclude filter | Check `ersap_prometheus_exporter_metrics_dropped_total{reason="filtered"}`. Filters match the **sanitized** name, prefix included. |
| Exporter exits with `Address already in use` | port 9095 taken | `ss -lntp \| grep 9095`, then pick another `--prometheus-port`. |
| Prometheus target is `down` | Prometheus cannot reach the exporter | `curl http://localhost:9095/metrics` on Node C. The exporter binds `0.0.0.0` by default; check the target address in `prometheus.yml`. |
| Grafana panels empty, dashboard imports fine | variable mismatch | Check **Metric prefix** matches `--metric-prefix`, and that **Session**/**Host** are not narrowed to values that no longer exist. Re-select **All**. |
| Grafana: "Datasource ${datasource} not found" | no data source chosen at import | Re-import and select a Prometheus data source, or set the **Data source** variable in the top bar. |
| Everything works, graphs are flat lines | you are graphing counters raw | Use the **rate of change** panel for `*_total` metrics. |

**One-line health sweep** (Node C):

```bash
# ===== NODE C =====
echo "--- exporter ---";   curl -s http://localhost:9095/health; echo
echo "--- up ---";         curl -s http://localhost:9095/metrics | grep '^ersap_prometheus_exporter_up'
echo "--- messages ---";   curl -s http://localhost:9095/metrics | grep '^ersap_prometheus_exporter_messages_received_total'
echo "--- sessions ---";   curl -s http://localhost:9095/metrics | grep '^ersap_dpe_cores' | grep -o 'session="[^"]*"' | sort -u
echo "--- prom target ---"; curl -s http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
```

---

## 12. Shutdown

Reverse the startup order: stop the producers first, the broker last.

```bash
# ===== NODE A =====  (1) stop pipeline 1
# in ersap-shell:
ersap> exit
# or, if it was started manually:
$ERSAP_HOME/bin/kill-dpes
```

```bash
# ===== NODE B =====  (2) stop pipeline 2
ersap> exit
# or
$ERSAP_HOME/bin/kill-dpes
```

```bash
# ===== NODE C =====  (3) Grafana, (4) Prometheus
sudo systemctl stop grafana-server
sudo systemctl stop prometheus     # or Ctrl-C in its terminal
```

```bash
# ===== NODE C =====  (5) the exporter — Ctrl-C, or:
sudo systemctl stop ersap-prometheus-exporter
# started by hand:
pkill -f 'prometheus.PrometheusExporter'
```

The exporter installs a shutdown hook, so `SIGINT` and `SIGTERM` both unsubscribe, close the
xMsg resources and stop the HTTP server. A clean stop logs:

```
INFO PrometheusExporter - shutting down the ERSAP Prometheus exporter
INFO MonitorFeSubscriber - stopping Monitor FE subscriber
INFO MonitorFeSubscriber - Monitor FE subscriber stopped
INFO MetricsHttpServer - stopping the Prometheus HTTP endpoint
INFO PrometheusExporter - ERSAP Prometheus exporter stopped
```

```bash
# ===== NODE C =====  (6) the Monitor Front End, last
# Ctrl-C in its terminal, or:
pkill -f 'org.jlab.epsci.ersap.sys.Dpe'
```

> `kill-dpes` sends **SIGKILL** to every Java DPE owned by the current user, so it is a
> forceful stop with no graceful shutdown — prefer `exit` from `ersap-shell` when a run is in
> progress. Do not run it on Node C while the Monitor FE is meant to stay up: it would match
> and kill the Monitor FE too.

**Verify everything stopped** (each node):

```bash
pgrep -fl 'ersap.sys.Dpe|prometheus.PrometheusExporter' || echo "all stopped"
```

Stopping the exporter or the Monitor FE does not affect the pipelines — they publish
fire-and-forget over ZeroMQ and keep processing data regardless. Prometheus retains the
history it already scraped.

---

## Quick reference

| Node | Command |
|---|---|
| **C** | `j_dpe --host <NODE_C_IP> --port 9000 --session mon` |
| **C** | `java -cp "$ERSAP_HOME/lib/*" org.jlab.epsci.ersap.util.prometheus.PrometheusExporter --monitor-host <NODE_C_IP> --monitor-port 9000 --session '*' --prometheus-port 9095` |
| **C** | `prometheus --config.file=/etc/prometheus/prometheus.yml` |
| **C** | `sudo systemctl start grafana-server` |
| **A** | `ersap-shell` → `set monHost <NODE_C_IP>` / `set session pipelineA` / `run local` |
| **B** | `ersap-shell` → `set monHost <NODE_C_IP>` / `set session pipelineB` / `run local` |
| Browser | `http://<NODE_C_IP>:3000` → **Dashboards → ERSAP Monitor FE** |

**See also**

* [Exporter reference](src/main/java/org/jlab/epsci/ersap/util/prometheus/README.md) — every
  option, metric catalogue, name sanitization, filters, reconnection behaviour.
* [ObservabilityTest.md](ObservabilityTest.md) — the two-node `TestMonitor` walkthrough this
  guide generalizes.
