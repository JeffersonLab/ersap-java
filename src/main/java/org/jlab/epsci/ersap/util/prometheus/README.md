# ERSAP Monitor FE Prometheus exporter

Subscribes to an ERSAP **Monitor FE**, converts every numeric value it broadcasts into a
Prometheus metric, and serves them on `http://0.0.0.0:9095/metrics`.

It is the machine-readable counterpart of
[`TestMonitor`](../../../../../../examples/TestMonitor.java): the same two subscriptions,
the same messages, printed into a Prometheus registry instead of stdout.

```
   ERSAP DPEs ──dpeReport──▶  Monitor FE  ──▶ PrometheusExporter ──▶ /metrics ──▶ Prometheus ──▶ Grafana
              ──userMetrics──▶   proxy
```

---

## Contents

| Path | What it is |
|---|---|
| `PrometheusExporter.java` | main application and lifecycle |
| `PrometheusExporterConfig.java` | option parsing, environment, properties file |
| `MonitorConnection.java` / `ErsapMonitorConnection.java` | the Monitor FE subscription seam and its production implementation |
| `MonitorFeSubscriber.java` | supervision: subscribe, watch, resubscribe |
| `MonitorMetric.java` / `MetricType.java` | transport-independent metric sample |
| `MonitorMetricParser.java` | Monitor FE messages → metric samples |
| `MetricNameSanitizer.java` | source names → valid Prometheus names |
| `MetricFilter.java` | include/exclude filtering |
| `PrometheusMetricRegistry.java` | dynamic, thread-safe metric registration |
| `ExporterSelfMetrics.java` | exporter health metrics |
| `MetricsHttpServer.java` | `/metrics` and `/health` |
| `config/prometheus-exporter.example.properties` | annotated example configuration |
| `prometheus/prometheus-example.yml` | example scrape config and alert rules |
| `grafana/ersap-monitor-dashboard.json` | importable Grafana dashboard |

---

## How `TestMonitor.java` informed this implementation

`TestMonitor` is the reference consumer of the Monitor FE. Everything below was read out
of it and the classes it uses, not invented.

**Address.** `TestMonitor` builds `new DataRingAddress(monFeHost)`. `DataRingAddress`
hard-codes `ErsapConstants.MONITOR_PORT = 9000` in that constructor, which is why the
Monitor FE DPE must be started with `j_dpe --port 9000`. `MonitorOrchestrator` also honours
`$ERSAP_MONITOR_FE` (a canonical DPE name such as `10.1.1.10%9000_java`) when no address is
given. The exporter keeps both: `--monitor-host` / `--monitor-port` default to the host and
port of `$ERSAP_MONITOR_FE`, falling back to `localhost:9000`.

**Transport.** xMsg (`org.jlab.coda:xmsg`) over ZeroMQ, wrapped by `BaseOrchestrator`.
`MonitorOrchestrator` is the public facade and is what the exporter uses — no xMsg call is
made directly.

**Subscriptions.** `TestMonitor` starts exactly two, and so does the exporter:

| Topic | Built by | Payload |
|---|---|---|
| `dpeReport:<session>` | `ErsapSubscriptions.dpeReport(session)` | DPE system report |
| `userMetrics:<session>` | `ErsapSubscriptions.userMetrics(session)` | engine-published key/values |

`ErsapConstants.DPE_REPORT = "dpeReport"` and `ErsapConstants.USER_METRICS = "userMetrics"`.
The session is a topic filter, not a label the exporter invents.

**Decoding.** Both are already decoded by the ERSAP base library, so the exporter never
parses the wire format itself:

* `dpeReport` — a JSON document with two top-level keys, `DPERegistration` and `DPERuntime`
  (`ErsapConstants.REGISTRATION_KEY` / `RUNTIME_KEY`). `ErsapSubscriptions.DpeReportSubscription`
  turns them into `DpeRegistrationData` and `DpeRuntimeData`, each holding nested
  `ContainerRuntimeData` and `ServiceRuntimeData`. The exporter receives these objects
  through `DpeReportHandler`, the same interface `TestMonitor` implements.
* `userMetrics` — a flat JSON object serialized by `ServiceEngine.sendUserMetrics` from
  whatever an engine passed to `EngineMetricsPublisher.publish(key, value)`.
  `MonitorOrchestrator.dispatchUserMetrics` splits the topic
  (`userMetrics:<session>:<engine>`, carried in the message description) and hands over a
  `Map<String, Object>` through `UserMetricsHandler`. Keys and value types are chosen by
  engine authors and are **unknown until the first message arrives** — this is what forces
  the dynamic registration described below.

**Failure handling.** `TestMonitor` does none: it subscribes once and calls
`Thread.currentThread().join()`. `MonitorOrchestrator.dispatchUserMetrics` logs and swallows
decoding errors. The exporter adds supervision and reconnection on top; see
[Reconnection behaviour](#reconnection-behaviour).

### Monitor FE message format

`dpeReport`, abridged (see the full fixture in
`src/test/resources/org/jlab/epsci/ersap/util/prometheus/dpe-report.json`):

```json
{
  "DPERegistration": {
    "name": "10.1.1.10_java", "language": "java", "session": "test",
    "start_time": "2021-06-20 12:30:00", "n_cores": 8, "memory_size": 1908932608,
    "containers": [ { "name": "10.1.1.10_java:user", "services": [ ... ] } ]
  },
  "DPERuntime": {
    "name": "10.1.1.10_java", "snapshot_time": "2021-06-20 12:35:00",
    "cpu_usage": 38.4, "memory_usage": 631222786, "load": 1.72,
    "containers": [ {
      "name": "10.1.1.10_java:user", "n_requests": 30000,
      "services": [ {
        "name": "10.1.1.10_java:user:EventRateMonitor",
        "n_requests": 15000, "n_failures": 3, "shm_reads": 15000, "shm_writes": 15000,
        "bytes_recv": 0, "bytes_sent": 0, "exec_time": 312000
      } ]
    } ]
  }
}
```

Notes taken from the data classes:

* `cpu_usage` is already a percentage (`DpeRuntimeData.cpuUsage()`), not a 0–1 fraction.
* `load` is negative when unavailable; `cpu_usage` is `NaN` when unavailable.
* `exec_time` is in **microseconds**; the exporter divides by 1e6.
* timestamps are `LocalDateTime` in `yyyy-MM-dd HH:mm:ss`, in the DPE's local time zone.
* `n_requests`, `n_failures`, `shm_*`, `bytes_*` and `exec_time` are cumulative since the
  service was deployed.

`userMetrics`, exactly what `EventRateMonitor` publishes:

```json
{"event_rate_hz":3127.4,"events_in_window":3127,"total_events":45831}
```

---

## Prerequisites

* Java 17 (the project toolchain).
* A running Monitor FE DPE, started on **port 9000**:
  `j_dpe --host <monfe-ip> --port 9000 --session test`.
* DPEs configured to duplicate their reports to it, via
  `export ERSAP_MONITOR_FE="<monfe-ip>%9000_java"`.

`ObservabilityTest.md` at the repository root walks through the whole setup.

## Dependencies

None were added. The three Prometheus artifacts were already declared in `build.gradle`
and were previously unused:

```groovy
prometheus_client     : 'io.prometheus:simpleclient:0.16.0'
prometheus_httpserver : 'io.prometheus:simpleclient_httpserver:0.16.0'
prometheus_hotspot    : 'io.prometheus:simpleclient_hotspot:0.16.0'
```

Option parsing reuses `net.sf.jopt-simple:jopt-simple`, as `MonitorOrchestrator` and the
other ERSAP entry points do. Logging reuses `org.jlab.epsci.ersap.util.logging`.

## Building

```bash
./gradlew build          # compile, tests, checkstyle, spotbugs
./gradlew test           # tests only
./gradlew deploy         # install into $ERSAP_HOME
```

## Running

```bash
java -cp "$ERSAP_HOME/lib/*" \
     org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --monitor-host 10.1.1.10 \
     --monitor-port 9000 \
     --session test \
     --prometheus-port 9095 \
     --metric-prefix ersap
```

With `$ERSAP_MONITOR_FE` already exported, no arguments are needed:

```bash
export ERSAP_MONITOR_FE="10.1.1.10%9000_java"
java -cp "$ERSAP_HOME/lib/*" org.jlab.epsci.ersap.util.prometheus.PrometheusExporter
```

More examples:

```bash
# every session, only the engine metrics, tagged with a cluster label
... PrometheusExporter --session '*' --include 'ersap_user_*' --label cluster=jlab

# one engine only, quieter, on a different port
... PrometheusExporter --engine '10.1.1.10_java:user:EventRateMonitor' \
                       --prometheus-port 9105 --log-level warn

# from a file
... PrometheusExporter --config /etc/ersap/prometheus-exporter.properties

# see everything
... PrometheusExporter --help
```

---

## Configuration

Precedence: **defaults < `--config` file < environment < command line.**

| Option | Environment | Default | Meaning |
|---|---|---|---|
| `--config <file>` | `ERSAP_PROM_CONFIG` | — | properties file; keys are long option names without dashes |
| `--monitor-host <host>` | `ERSAP_PROM_MONITOR_HOST` | host of `$ERSAP_MONITOR_FE`, else `localhost` | Monitor FE address |
| `--monitor-port <port>` | `ERSAP_PROM_MONITOR_PORT` | port of `$ERSAP_MONITOR_FE`, else `9000` | Monitor FE proxy port |
| `--session <s>` | `ERSAP_PROM_SESSION` | `test` | session filter; `*` subscribes to all sessions |
| `--engine <name>` | `ERSAP_PROM_ENGINE` | all engines | restricts the `userMetrics` subscription to one canonical engine name |
| `--prometheus-host <addr>` | `ERSAP_PROM_PROMETHEUS_HOST` | `0.0.0.0` | HTTP bind address |
| `--prometheus-port <port>` | `ERSAP_PROM_PROMETHEUS_PORT` | `9095` | HTTP port; `0` picks an ephemeral port |
| `--metric-prefix <p>` | `ERSAP_PROM_METRIC_PREFIX` | `ersap` | prepended to every metric name |
| `--label <name=value>` | `ERSAP_PROM_LABEL` | — | static label on every series; repeatable |
| `--include <pattern>` | `ERSAP_PROM_INCLUDE` | — | export only matching metrics; repeatable |
| `--exclude <pattern>` | `ERSAP_PROM_EXCLUDE` | — | never export matching metrics; repeatable |
| `--counter-pattern <re>` | `ERSAP_PROM_COUNTER_PATTERN` | `^total_.*\|.*_(total\|count\|counter)$` | which user metrics become counters; empty disables |
| `--reconnect-interval <s>` | `ERSAP_PROM_RECONNECT_INTERVAL` | `10` | delay between subscription attempts |
| `--stale-timeout <s>` | `ERSAP_PROM_STALE_TIMEOUT` | `0` (off) | resubscribe after this much silence |
| `--max-series <n>` | `ERSAP_PROM_MAX_SERIES` | `20000` | cap on exported time series |
| `--export-timestamps` | `ERSAP_PROM_EXPORT_TIMESTAMPS` | off | also export `ersap_metric_last_update_timestamp_seconds` |
| `--jvm-metrics` | `ERSAP_PROM_JVM_METRICS` | off | also export the exporter's own JVM metrics |
| `--log-level <level>` | `ERSAP_PROM_LOG_LEVEL` | `info` | `trace`, `debug`, `info`, `warn`, `error`, `off` |
| `-h`, `--help` | — | — | print the full option help and exit |

Repeatable options take a comma-separated list in the environment and in the properties
file (`exclude = ersap_dpe_*,ersap_container_*`); on the command line, repeat the flag.
Flags accept `true`, `yes` or `1` from the environment and the file.

---

## What gets exported

### DPE metrics, from `dpeReport`

Labels: `component="dpe"`, `source="dpe_report"`, `dpe`, `host`, `lang`, `session`.

| Metric | Type | Source |
|---|---|---|
| `ersap_dpe_cpu_usage_percent` | gauge | `DpeRuntimeData.cpuUsage()`, already a percentage |
| `ersap_dpe_memory_usage_bytes` | gauge | `memoryUsage()` |
| `ersap_dpe_system_load` | gauge | `systemLoad()`, omitted when negative |
| `ersap_dpe_cores` | gauge | `DpeRegistrationData.numCores()` |
| `ersap_dpe_memory_size_bytes` | gauge | `memorySize()` |
| `ersap_dpe_start_time_seconds` | gauge | `startTime()` |
| `ersap_dpe_snapshot_time_seconds` | gauge | `snapshotTime()` |

### Container metrics

Labels: the DPE labels plus `component="container"` and `container`.

| Metric | Type | Source |
|---|---|---|
| `ersap_container_requests_total` | counter | `ContainerRuntimeData.numRequests()` |

### Service metrics

Labels: the container labels plus `component="service"`, `service` (canonical name) and
`engine` (short name).

| Metric | Type | Source |
|---|---|---|
| `ersap_service_requests_total` | counter | `numRequests()` |
| `ersap_service_failures_total` | counter | `numFailures()` |
| `ersap_service_execution_time_seconds_total` | counter | `executionTime()` ÷ 1e6 |
| `ersap_service_shared_memory_reads_total` | counter | `sharedMemoryReads()` |
| `ersap_service_shared_memory_writes_total` | counter | `sharedMemoryWrites()` |
| `ersap_service_bytes_received_total` | counter | `bytesReceived()` |
| `ersap_service_bytes_sent_total` | counter | `bytesSent()` |

### Engine metrics, from `userMetrics`

Every key an engine publishes becomes `ersap_user_<sanitized key>`, labelled with
`component="engine"`, `source="user_metrics"`, `dpe`, `host`, `lang`, `session`,
`container`, `service`, `engine`. Names and types are discovered at runtime.

### Exporter self-metrics

Always exported, never filtered:

```
ersap_prometheus_exporter_up                              1 when subscribed, 0 otherwise
ersap_prometheus_exporter_messages_received_total{source} messages per subscription
ersap_prometheus_exporter_metrics_processed_total         samples written to the registry
ersap_prometheus_exporter_metrics_dropped_total{reason}   filtered / reserved_name / series_limit / invalid_value
ersap_prometheus_exporter_metric_parse_errors_total       values that were not numeric
ersap_prometheus_exporter_connection_errors_total         failed subscription attempts
ersap_prometheus_exporter_reconnects_total                successful resubscriptions
ersap_prometheus_exporter_metric_name_collisions_total    distinct source names that collided
ersap_prometheus_exporter_last_message_timestamp_seconds  when the last message arrived
ersap_prometheus_exporter_registered_metrics              metric families discovered
ersap_prometheus_exporter_registered_series               time series exported
ersap_prometheus_exporter_start_time_seconds              exporter start time
```

---

## Metric name sanitization

`MetricNameSanitizer` applies these rules, in order:

1. lower-case the source name;
2. replace every character outside `[a-z0-9_]` with `_`;
3. collapse runs of `_` into one;
4. strip leading and trailing `_`;
5. fall back to `unnamed` if nothing is left;
6. prepend the configured prefix;
7. prepend `_` if the result would still start with a digit.

```
Event Rate         ->  ersap_event_rate
input.bytes/sec    ->  ersap_input_bytes_sec
7_second_average   ->  ersap_7_second_average
host:container:Eng ->  ersap_host_container_eng
```

Colons are **not** preserved. Prometheus reserves `:` for recording-rule names, and ERSAP
canonical names use it as a separator — those go into labels instead.

**Counters and the `_total` suffix.** The Prometheus client enforces the counter naming
convention: a counter family whose name does not already end in `_total` gets it appended
to the exposed sample name. So a user metric `total_events` inferred as a counter is
exported as `ersap_user_total_events_total`. Metrics already ending in `_total` are
unaffected.

## Metric types

* **Counter** for the cumulative values in `dpeReport` — request, failure, shared-memory,
  byte and execution-time totals. These are documented as "accumulated since the service
  was deployed" in `ServiceRuntimeData`, so the type is certain.
* **Gauge** for everything else in `dpeReport`, and for every engine metric by default.
* Engine metrics become counters only when the sanitized name matches `--counter-pattern`
  (default: starts with `total_`, or ends in `_total`, `_count` or `_counter`).
  Set `--counter-pattern ''` to make every engine metric a gauge.
* **Histograms and summaries are never produced.** The Monitor FE publishes no bucket or
  quantile information, so they cannot be constructed correctly.
* Booleans map to `1` / `0`. So do the strings `true`/`false`, `yes`/`no`, `on`/`off`.
* Non-numeric strings, arrays and nulls are **not** exported; each one increments
  `ersap_prometheus_exporter_metric_parse_errors_total`. Turning them into labels would
  make cardinality unbounded, since their values are arbitrary engine output.
* Nested JSON objects in a `userMetrics` payload are flattened to depth 4 with `_`
  (`{"io":{"bytes":{"in":10}}}` → `ersap_user_io_bytes_in`). JSON arrays are rejected.

## Labels

Every label comes from a fixed vocabulary, all of it bounded by the size of the ERSAP
deployment:

| Label | Value |
|---|---|
| `component` | `dpe`, `container`, `service` or `engine` |
| `source` | `dpe_report` or `user_metrics` |
| `dpe` | canonical DPE name, `host%port_lang` |
| `host` | DPE host address |
| `lang` | `java`, `cpp` or `python` |
| `session` | ERSAP session |
| `container` | short container name |
| `service` | canonical service name |
| `engine` | short engine name |

Timestamps, event IDs, file names and free-form engine strings are never used as labels.
`--label name=value` adds static labels to every series; label names are sanitized the same
way metric names are.

**Consistency.** The label names of a metric family are fixed the first time it is
registered. Later samples are normalized onto that schema: a missing label becomes the
empty string, an unexpected one is dropped and logged once. Every series of a family
therefore always has the same label set, as Prometheus requires.

## Timestamps

The exporter does **not** attach source timestamps to samples; Prometheus records the
scrape time, which is the recommended default. With `--export-timestamps` it additionally
exposes

```
ersap_metric_last_update_timestamp_seconds{metric="...", component="...", ..., engine="..."}
```

one series per exported series, holding the DPE's `snapshot_time`. It is off by default
because it doubles cardinality. Engine metrics carry no timestamp of their own and do not
appear in it.

## Dynamic metrics

The exporter cannot know engine metric names at startup, so `PrometheusMetricRegistry`
registers them as they arrive. A single `Collector` is registered with the
`CollectorRegistry`; families are entries in a `ConcurrentHashMap`, so no collector object
is ever created per update.

* first sighting registers the family and logs it at `INFO`;
* later samples reuse it — no duplicate collectors;
* **collisions**: distinct source names can sanitize onto one Prometheus name. The first
  one owns the family, later ones share it, and each new colliding pair is logged as a
  `WARN` once and counted in `ersap_prometheus_exporter_metric_name_collisions_total`;
* **type conflicts**: the type recorded at registration wins; a conflicting later type is
  logged once and ignored;
* **growth**: `--max-series` caps the total number of series (20 000 by default). Beyond
  it, new series are dropped — existing ones keep updating — and counted under
  `metrics_dropped_total{reason="series_limit"}`;
* **reserved names**: anything that sanitizes into `<prefix>_prometheus_exporter_` is
  rejected, so a rogue engine metric can never shadow an exporter self-metric.

## Filters

Filters are matched against the **final metric name**, prefix included — what you write is
what you see in `/metrics`. Two syntaxes:

* **glob** (default): `*` matches any run of characters, `?` matches one. Everything else
  is literal, so `.` is a dot.
* **regular expression**: any pattern wrapped in slashes, e.g. `/^ersap_(dpe|service)_.*$/`.

Both must match the whole name. If any `--include` is given, a name must match at least
one; any `--exclude` match then drops it. Exclude wins. Self-metrics are never filtered.

```bash
--include 'ersap_user_*'                    # only engine metrics
--include 'ersap_service_*' --include 'ersap_user_*'
--exclude 'ersap_container_*'               # drop the container roll-up
--exclude '*_bytes_*'                       # drop the network byte counters
--include '/^ersap_(user|service)_.*$/'     # same as the second line, as a regex
--include 'ersap_user_*' --exclude 'ersap_user_debug_*'
```

## Reconnection behaviour

A single daemon thread supervises the subscription:

1. build a `MonitorOrchestrator` and start both subscriptions;
2. on success, set `up` to 1 and watch;
3. on failure, count `connection_errors_total`, set `up` to 0, close, wait
   `--reconnect-interval` seconds and retry — for ever;
4. every successful re-establishment after the first counts a `reconnects_total`.

The retry delay has a hard floor, so a permanently unreachable Monitor FE cannot turn this
into a tight loop. The wait is a latch, so shutdown interrupts it immediately. A temporary
outage never terminates the process; only an invalid configuration or a port that cannot
be bound does.

**What `up` really means.** The Monitor FE is a ZeroMQ pub/sub proxy. Subscribing to a
proxy that is down does not fail — the socket stays silent and reconnects on its own — so
there is no connection event to observe. `up` therefore reports that both subscriptions
were created without error. To also catch a Monitor FE that is up but silent, set
`--stale-timeout <seconds>`: after that much silence the exporter sets `up` to 0, tears the
subscription down and rebuilds it. It is off by default because an idle pipeline
legitimately publishes nothing.

## Shutdown

A JVM shutdown hook handles `SIGINT`, `SIGTERM` and normal exit. It stops the supervisor
thread, unsubscribes and closes the xMsg resources through `MonitorOrchestrator.close()`,
then stops the HTTP server and its executor. `close()` is idempotent.

---

## Prometheus

`prometheus/prometheus-example.yml` is ready to use:

```yaml
scrape_configs:
  - job_name: "ersap-monitor"
    static_configs:
      - targets:
          - "localhost:9095"
```

Scraping faster than the DPE report period (10 s by default) only re-reads the same
samples. It also contains suggested alert rules for `up == 0`, a silent Monitor FE and
failing services.

## Grafana

Import `grafana/ersap-monitor-dashboard.json` through **Dashboards → New → Import**. It
declares a `datasource` template variable instead of a hard-coded UID, so Grafana asks
which Prometheus data source to use at import time and the same file works in any
environment.

Variables:

| Variable | What it does |
|---|---|
| `datasource` | the Prometheus data source |
| `prefix` | the `--metric-prefix` in use, without the trailing underscore (default `ersap`) |
| `metric` | multi-select over **every** metric the exporter has published, discovered from Prometheus |
| `host`, `session`, `component`, `container`, `engine`, `service` | multi-select label filters, each with an *All* option |

Panels: selected metrics over time, their current values, their rate of change, exporter
connection status, Monitor FE message rate, metric processing errors, time since the last
message, and the number of registered metrics.

Because `metric` is populated by querying Prometheus rather than hard-coded, an engine
metric added long after this dashboard was written shows up in the picker on its own. The
panels select series with
`{__name__=~"$metric", source=~"dpe_report|user_metrics", host=~"$host", ...}` — the
`source` matcher keeps the query valid and restricted to Monitor FE payload metrics even
when every variable is set to *All*.

---

## Example `/metrics` output

Abridged; produced by the integration test against the fixture messages.

```
# HELP ersap_dpe_cpu_usage_percent DPE process CPU usage, in percent
# TYPE ersap_dpe_cpu_usage_percent gauge
ersap_dpe_cpu_usage_percent{component="dpe",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",} 38.4
# HELP ersap_dpe_memory_usage_bytes Memory in use by the DPE process, in bytes
# TYPE ersap_dpe_memory_usage_bytes gauge
ersap_dpe_memory_usage_bytes{component="dpe",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",} 6.31222786E8
# HELP ersap_dpe_system_load System load average of the DPE node
# TYPE ersap_dpe_system_load gauge
ersap_dpe_system_load{component="dpe",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",} 1.72
# HELP ersap_container_requests_total Requests received by all the services of the container
# TYPE ersap_container_requests_total counter
ersap_container_requests_total{component="container",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",container="user",} 30000.0
# HELP ersap_service_requests_total Requests received by the service since it was deployed
# TYPE ersap_service_requests_total counter
ersap_service_requests_total{component="service",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",container="user",service="10.1.1.10_java:user:EventRateMonitor",engine="EventRateMonitor",} 15000.0
# HELP ersap_service_execution_time_seconds_total Accumulated execution time of the service, in seconds
# TYPE ersap_service_execution_time_seconds_total counter
ersap_service_execution_time_seconds_total{component="service",source="dpe_report",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",session="test",container="user",service="10.1.1.10_java:user:EventRateMonitor",engine="EventRateMonitor",} 0.312
# HELP ersap_user_event_rate_hz ERSAP user engine metric "event_rate_hz"
# TYPE ersap_user_event_rate_hz gauge
ersap_user_event_rate_hz{component="engine",source="user_metrics",service="10.1.1.10_java:user:EventRateMonitor",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",container="user",engine="EventRateMonitor",session="test",} 3127.4
# HELP ersap_user_total_events_total ERSAP user engine metric "total_events"
# TYPE ersap_user_total_events_total counter
ersap_user_total_events_total{component="engine",source="user_metrics",service="10.1.1.10_java:user:EventRateMonitor",dpe="10.1.1.10_java",host="10.1.1.10",lang="java",container="user",engine="EventRateMonitor",session="test",} 45831.0
# HELP ersap_prometheus_exporter_up 1 when the Monitor FE subscription is established, 0 otherwise
# TYPE ersap_prometheus_exporter_up gauge
ersap_prometheus_exporter_up 1.0
# HELP ersap_prometheus_exporter_messages_received_total Messages received from the Monitor FE
# TYPE ersap_prometheus_exporter_messages_received_total counter
ersap_prometheus_exporter_messages_received_total{source="dpe_report",} 1.0
ersap_prometheus_exporter_messages_received_total{source="user_metrics",} 1.0
# HELP ersap_prometheus_exporter_registered_metrics Monitor FE metric families currently registered
# TYPE ersap_prometheus_exporter_registered_metrics gauge
ersap_prometheus_exporter_registered_metrics 18.0
```

Counters are also accompanied by the `_created` gauges the Prometheus client emits for
OpenMetrics compatibility; they are omitted above.

## Verifying the exporter sees what `TestMonitor` prints

Run both against the same Monitor FE and session:

```bash
# terminal 1
java -cp "$ERSAP_HOME/lib/*:$ERSAP_HOME/services/*" \
     org.jlab.epsci.ersap.examples.TestMonitor 10.1.1.10 test

# terminal 2
java -cp "$ERSAP_HOME/lib/*" \
     org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
     --monitor-host 10.1.1.10 --session test

# terminal 3
curl -s localhost:9095/metrics | grep -E '^ersap_(dpe|service|user)_'
```

Then compare, within one report period:

| `TestMonitor` line | `/metrics` series |
|---|---|
| `CPU usage : 38.4%` | `ersap_dpe_cpu_usage_percent` |
| `Memory : 601.9 MB` | `ersap_dpe_memory_usage_bytes` ÷ 1 048 576 |
| `System load : 1.72` | `ersap_dpe_system_load` |
| `Cores : 8` | `ersap_dpe_cores` |
| `requests : 15000` | `ersap_service_requests_total{engine="..."}` |
| `failures : 3` | `ersap_service_failures_total{engine="..."}` |
| `exec time : 312 ms` | `ersap_service_execution_time_seconds_total` × 1000 |
| `shm reads : 15000` | `ersap_service_shared_memory_reads_total` |
| `event_rate_hz : 3127.4` | `ersap_user_event_rate_hz` |
| `total_events : 45831` | `ersap_user_total_events_total` |

The two read the same messages from the same proxy, so the values agree up to the sampling
offset between the two processes.

---

## Known limitations

* **`up` cannot detect a silent proxy on its own.** xMsg pub/sub gives no connection event,
  so `up` reflects "both subscriptions were created". Use `--stale-timeout` when the
  pipeline is expected to publish continuously.
* **Counters are absolute values, not deltas.** ERSAP counters reset to zero when a service
  is redeployed. Prometheus handles this correctly through `rate()`/`increase()`, which
  detect counter resets, but a raw graph of the counter shows the drop.
* **No histograms or summaries.** The Monitor FE publishes no distribution data.
* **Non-numeric engine values are dropped**, not turned into labels or `info` metrics.
* **Cardinality follows the deployment.** One series per (metric, DPE, container, service).
  A very large deployment plus `--export-timestamps` can approach `--max-series`.
* **Nested `userMetrics` objects** are flattened to depth 4; JSON arrays are not exported.
* **One Monitor FE per exporter.** Run one exporter per data-ring and let Prometheus
  aggregate.
* **The engine filter applies only to `userMetrics`.** `--engine` cannot narrow
  `dpeReport`, which is published per DPE; use `--exclude` or `--include` for that.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `/metrics` has only `ersap_prometheus_exporter_*` | No Monitor FE message has arrived. Check `ersap_prometheus_exporter_messages_received_total`; if it is 0, see the next rows. |
| `ersap_prometheus_exporter_up` is 0 | The subscription is failing. The log names the address; check that the Monitor FE DPE is running and reachable. |
| `up` is 1 but no messages | Almost always the port or the session. The Monitor FE must be started with `--port 9000`; `--session` must match the DPE's `--session`. `TestMonitor` on the same host and session is the quickest cross-check — if it prints nothing either, the problem is upstream of the exporter. |
| DPE reports arrive but no user metrics | The engines must call `EngineMetricsPublisher.publish()`, and their DPE must have `ERSAP_MONITOR_FE` set. |
| A metric is missing from `/metrics` | Check the filters (`ersap_prometheus_exporter_metrics_dropped_total{reason="filtered"}`) and remember that filters match the sanitized name, prefix included. |
| `metric_parse_errors_total` is climbing | An engine is publishing non-numeric values. Run with `--log-level debug` to see which. |
| Two engine metrics share one series | A sanitization collision — check `metric_name_collisions_total` and the `WARN` in the log, then rename one of the source metrics. |
| `Address already in use` at startup | Another process holds `--prometheus-port`. Pick another, or use `0` for an ephemeral port. |
| Prometheus reports the target as down | The exporter binds `0.0.0.0` by default; check the firewall and that Prometheus targets the right host and port. |
| Grafana panels are empty | Check the `prefix` variable matches `--metric-prefix`, and that the label filters at the top are not narrowed to values that no longer exist. |

## Tests

```bash
./gradlew test
```

* `MetricNameSanitizerTest` — sanitization, leading digits, whitespace and special
  characters, determinism, collisions, prefix normalization, label names.
* `MetricFilterTest` — glob and regex syntax, include, exclude, precedence, whole-name
  matching.
* `MonitorMetricParserTest` — parsing real `dpeReport` and `userMetrics` messages, missing
  and malformed reports, unit conversion, numeric/boolean/string conversion, rejection of
  non-numeric values, label extraction from canonical names, type inference.
* `PrometheusMetricRegistryTest` — dynamic registration, collector reuse, repeated updates,
  collisions, label normalization, static labels, filters, reserved names, the series cap,
  timestamps, and 8 threads updating 40 families concurrently.
* `PrometheusExporterConfigTest` — defaults, command line, environment, properties file,
  precedence, and rejection of invalid values.
* `PrometheusExporterIntegrationTest` — starts the exporter on an ephemeral port against
  the in-process `FakeMonitorFe`, publishes representative messages, scrapes `/metrics` and
  `/health` over real HTTP, validates the exposition format line by line, and exercises
  filters, prefixes, self-metrics, reconnection, stale resubscription and clean shutdown.

The fixtures use the real wire format: `MonitorReportFactory` (in `org.jlab.epsci.ersap.base`,
next to the existing `RuntimeDataFactory`) feeds the JSON through the same
`DpeRegistrationData`/`DpeRuntimeData` constructors that `ErsapSubscriptions` uses. No test
needs a running Monitor FE.
