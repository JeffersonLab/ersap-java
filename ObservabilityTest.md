# ObservabilityTest: Running the Three-Service Pipeline with Metrics Observation

This document describes how to run the test pipeline

```
SourceOfDoubles → EventRateMonitor → DoubleDumpSink
```

across two nodes — a **Monitor FE DPE** (Node A) and a **Processing DPE** (Node B) — and
how to observe both standard DPE metrics and the custom `event_rate_hz` user metric via a
`MonitorOrchestrator` subscriber.

---

## Prerequisites

- ERSAP built and installed: `$ERSAP_HOME` points to the installation directory
- The three services are on the classpath (`$ERSAP_HOME/services/` or `$CLASSPATH`)
- Both nodes can reach each other on the xMsg proxy port (default **7111**)
- Replace `<monfe-host>` and `<proc-host>` with the actual hostnames or IP addresses

---

## Step 1 — Start the Monitor FE DPE (Node A)

The Monitor FE is a standalone front-end DPE whose only job is to act as a message broker
for monitoring traffic. It is started **without** `--fe-host`, which makes it the front-end.

```bash
# Node A
export ERSAP_HOME=/path/to/ersap
j_dpe --host <monfe-host> --session test
```

Expected console output:
```
 Session          = test
 Front-end host   = <monfe-host>
 ...
[INFO] DPE started
```

Leave this terminal running. The Monitor FE proxy is now listening on port **7111**.

---

## Step 2 — Start the Processing DPE with Services (Node B)

The processing DPE connects to the regular ERSAP front-end (which in a standalone test can
be the same node as the Monitor FE, or a separate front-end). It is told about the Monitor
FE via the `ERSAP_MONITOR_FE` environment variable so that every `dpeReport` and
`userMetrics` message is duplicated to the Monitor FE proxy.

```bash
# Node B
export ERSAP_HOME=/path/to/ersap
export ERSAP_MONITOR_FE="<monfe-host>%7111_java"

j_dpe --host <proc-host> --fe-host <monfe-host> --session test --report 5
```

- `--fe-host <monfe-host>` — makes this a worker DPE connecting to the front-end
- `--report 5` — publish a `dpeReport` every 5 seconds (default is 10; shorter helps
  during testing)
- `ERSAP_MONITOR_FE` — tells `ServiceEngine` and `Dpe.ReportService` to also publish
  to the Monitor FE proxy

Expected console output:
```
 Session          = test
 Front-end host   = <monfe-host>
 Using monitoring front-end <monfe-host>%7111_java
 ...
[INFO] DPE started
```

---

## Step 3 — Deploy and Start the Services

In a **third terminal on Node B**, use the `ersap-shell` interactive shell to deploy the
three services and start the pipeline.

### 3a — Launch the shell

```bash
export ERSAP_HOME=/path/to/ersap
ersap-shell
```

### 3b — Configure the session and front-end inside the shell

```
ersap> set session test
ersap> set frontend <monfe-host>
```

### 3c — Create the services YAML file

Create `$ERSAP_USER_DATA/config/double-pipeline.yml`:

```yaml
io-services:
  reader:
    class: org.jlab.epsci.ersap.examples.engines.generic.SourceOfDoubles
    name: SourceOfDoubles
  writer:
    class: org.jlab.epsci.ersap.examples.engines.generic.DoubleDumpSink
    name: DoubleDumpSink

services:
  - class: org.jlab.epsci.ersap.examples.engines.test.EventRateMonitor
    name: EventRateMonitor

mime-types:
  - binary/data-double
```

### 3d — Point the shell at the config and a dummy file list

```
ersap> set servicesFile $ERSAP_USER_DATA/config/double-pipeline.yml
ersap> set fileList     $ERSAP_USER_DATA/config/files.txt
ersap> set inputDir     $ERSAP_USER_DATA/data/input
ersap> set outputDir    $ERSAP_USER_DATA/data/output
```

(`files.txt` can contain a single dummy filename since `SourceOfDoubles` generates data
internally.)

### 3e — Run locally

```
ersap> run local
```

The shell deploys `SourceOfDoubles`, `EventRateMonitor`, and `DoubleDumpSink` on the
processing DPE and starts the orchestrator. Processing begins immediately.

---

## Step 4 — Start the MonitorOrchestrator (Node A or any node)

The `MonitorOrchestrator` subscribes to the Monitor FE proxy and prints everything it
receives. Below is a minimal standalone subscriber that listens to both `dpeReport` and
`userMetrics` topics.

Create `TestMonitor.java` (or run it from a test class):

```java
import org.jlab.epsci.ersap.base.ContainerRuntimeData;
import org.jlab.epsci.ersap.base.DataRingAddress;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.ServiceRuntimeData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.MonitorOrchestrator;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;

import java.util.Map;
import java.util.Set;

public class TestMonitor {

    public static void main(String[] args) throws Exception {

        DataRingAddress monitorFe = new DataRingAddress("<monfe-host>");
        MonitorOrchestrator monitor = new MonitorOrchestrator(monitorFe);

        // ── DPE system metrics ──────────────────────────────────────────────
        monitor.listenDpeReports("test", new DpeReportHandler() {
            @Override
            public void handleReport(DpeRegistrationData reg, DpeRuntimeData runtime) {

                System.out.printf("%n=== DPE Report: %s  session=%s ===%n",
                        runtime.name(), reg.session());
                System.out.printf("  CPU usage   : %.1f%%%n",  runtime.cpuUsage() * 100);
                System.out.printf("  Memory      : %.1f MB%n", runtime.memoryUsage() / 1_048_576.0);
                System.out.printf("  System load : %.2f%n",    runtime.systemLoad());
                System.out.printf("  Cores       : %d%n",      reg.numCores());

                for (ContainerRuntimeData container : runtime.containers()) {
                    System.out.printf("  Container: %s%n", container.name());
                    for (ServiceRuntimeData svc : container.services()) {
                        long execMs = svc.executionTime() / 1_000;   // µs → ms
                        System.out.printf("    Service        : %s%n", svc.name());
                        System.out.printf("      requests     : %d%n", svc.numRequests());
                        System.out.printf("      failures     : %d%n", svc.numFailures());
                        System.out.printf("      exec time    : %d ms (cumulative)%n", execMs);
                        System.out.printf("      shm reads    : %d%n", svc.sharedMemoryReads());
                        System.out.printf("      shm writes   : %d%n", svc.sharedMemoryWrites());
                        System.out.printf("      bytes recv   : %d%n", svc.bytesReceived());
                        System.out.printf("      bytes sent   : %d%n", svc.bytesSent());
                    }
                }
            }
        });

        // ── User metrics from EventRateMonitor ──────────────────────────────
        monitor.listenUserMetrics("test", new UserMetricsHandler() {
            @Override
            public Set<EngineDataType> dataTypes() {
                return Set.of(EngineDataType.JSON);
            }

            @Override
            public void handleMetrics(String session, String engine,
                                      Map<String, Object> metrics) {
                System.out.printf("%n--- User Metrics: %s  session=%s ---%n",
                        engine, session);
                metrics.forEach((k, v) -> System.out.printf("  %-22s: %s%n", k, v));
            }
        });

        System.out.println("MonitorOrchestrator listening on " + monitorFe
                + "  (session=test) — press Ctrl-C to stop");
        Thread.currentThread().join();   // block forever
    }
}
```

Run it:

```bash
java -cp "$ERSAP_HOME/lib/*:$ERSAP_HOME/services/*" TestMonitor
```

---

## Step 5 — Expected Console Output

### 5a — DPE system report (every 5 seconds)

Produced by `listenDpeReports`. One block per report period per processing DPE:

```
=== DPE Report: <proc-host>%7111_java  session=test ===
  CPU usage   : 38.4%
  Memory      : 512.3 MB
  System load : 1.72
  Cores       : 8
  Container: <proc-host>%7111_java:<username>
    Service        : <proc-host>%7111_java:<username>:SourceOfDoubles
      requests     : 15000
      failures     : 0
      exec time    : 0 ms (cumulative)
      shm reads    : 0
      shm writes   : 15000
      bytes recv   : 0
      bytes sent   : 0
    Service        : <proc-host>%7111_java:<username>:EventRateMonitor
      requests     : 15000
      failures     : 0
      exec time    : 312 ms (cumulative)
      shm reads    : 15000
      shm writes   : 15000
      bytes recv   : 0
      bytes sent   : 0
    Service        : <proc-host>%7111_java:<username>:DoubleDumpSink
      requests     : 15000
      failures     : 0
      exec time    : 48 ms (cumulative)
      shm reads    : 15000
      shm writes   : 0
      bytes recv   : 0
      bytes sent   : 0
```

**What to look for:**

| Field | Healthy sign |
|---|---|
| `CPU usage` | Non-zero; scales with throughput |
| `Memory` | Stable; no steady upward drift |
| `System load` | Proportional to active threads |
| `requests` (all services) | All three counts increase together at the same rate |
| `failures` | Zero for a healthy run |
| `exec time` (EventRateMonitor) | Growing; divide by `requests` for avg µs/event |
| `shm reads/writes` | Non-zero — confirms services are talking via shared memory (same DPE) |
| `bytes recv/sent` | Zero for intra-DPE chains; non-zero if services span DPEs |

---

### 5b — User metrics from EventRateMonitor (every ~1 second)

Produced by `listenUserMetrics`. One block per measurement window:

```
--- User Metrics: <proc-host>%7111_java:<username>:EventRateMonitor  session=test ---
  event_rate_hz         : 3127.4
  events_in_window      : 3127
  total_events          : 45831
```

**What to look for:**

| Field | Meaning |
|---|---|
| `event_rate_hz` | Aggregate throughput of all pool instances (events/sec). Expect values in the thousands for simple double passing. |
| `events_in_window` | Raw count in the last 1-second window. Should roughly equal `event_rate_hz`. |
| `total_events` | Cumulative since service start. Should increase monotonically and match `n_requests` in the DPE report. |

Because `EventRateMonitor` uses shared static counters with a CAS-based window handoff,
**exactly one** user metrics message is published per second regardless of the pool size.

---

## Step 6 — Verifying Consistency Between Report Types

Cross-check these values across the two report types to confirm everything is wired correctly:

1. **Request count matches**: `total_events` from user metrics should match
   `n_requests` for `EventRateMonitor` in the DPE report (both are cumulative since start).

2. **Rate is plausible**: `event_rate_hz` × `report_period_seconds` should roughly equal
   the increase in `n_requests` between two consecutive DPE reports.

3. **Shared memory confirms intra-DPE chain**: `shm_reads` and `shm_writes` for
   `EventRateMonitor` should equal `n_requests`, confirming data never leaves the DPE.

4. **Zero failures**: `n_failures = 0` for all three services confirms no errors in the
   chain and that `sendUserMetrics()` exceptions (if any) are being caught and logged,
   not propagated to the data plane.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No DPE report received | `ERSAP_MONITOR_FE` not set on Node B, or wrong host/port |
| No user metrics received | `ERSAP_MONITOR_FE` not set, or `EventRateMonitor` not in composition |
| `event_rate_hz` is ~1/5th of expected | Old non-static version of `EventRateMonitor` in classpath; verify the deployed jar |
| `total_events` doesn't match `n_requests` | Normal for the first few seconds while windows align; should converge |
| `shm_reads = 0` | Services deployed in separate containers or separate DPEs; use one container |
| Memory grows steadily | Engine or reader leaking objects; check `SourceOfDoubles.close()` |
