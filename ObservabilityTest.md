# ObservabilityTest: Running the Three-Service Pipeline with Metrics Observation

This document describes how to run the test pipeline

```
SourceOfDoubles → EventRateMonitor → DoubleDumpSink
```

across two nodes — a **Monitor FE DPE** (Node A) and a **Processing node** (Node B) — and
how to observe both standard DPE metrics and the custom `event_rate_hz` user metric via a
`MonitorOrchestrator` subscriber.

**How the DPEs are started:**

- Node A runs one DPE acting as the **Monitor FE** — a dedicated message broker for
  monitoring traffic only.
- Node B uses `ersap-shell` → `run local`, which starts **one processing DPE** (acting as
  its own local front-end) plus the orchestrator in a single step. There is no separate
  manual `j_dpe` invocation on Node B. Setting `ERSAP_MONITOR_FE` before launching
  `ersap-shell` is sufficient — `run local` automatically passes that variable to the DPE
  it starts.

---

## Prerequisites

- ERSAP built and installed: `$ERSAP_HOME` points to the installation directory
- The three services are on the classpath (`$ERSAP_HOME/services/`)
- Both nodes can reach each other on the xMsg proxy port (default **7111**)
- Replace `<monfe-ip>` and `<proc-host>` with actual **IP addresses** (dotted-decimal).
  ERSAP canonical names only accept IP addresses, not hostnames. Use `127.0.0.1` for
  localhost. Running `hostname -I` or `ifconfig` will give you the address to use.

---

## Step 1 — Start the Monitor FE DPE (Node A)

The Monitor FE is a standalone front-end DPE whose sole job is to act as a message broker
for monitoring traffic. It is started without `--fe-host`, which makes it the front-end.

```bash
# Node A — one terminal
export ERSAP_HOME=/path/to/ersap
j_dpe --host <monfe-ip> --session test
```

Expected console output:
```
 Session          = test
 Front-end host   = <monfe-ip>
 ...
[INFO] DPE started
```

Leave this terminal running. The Monitor FE proxy is now listening on port **7111**.

---

## Step 2 — Start the Processing DPE and Services (Node B)

`run local` inside `ersap-shell` starts the processing DPE and the orchestrator together.
It reads `ERSAP_MONITOR_FE` from the shell environment and passes it to the DPE it creates,
so the DPE automatically duplicates all `dpeReport` and `userMetrics` messages to the
Monitor FE proxy on Node A.

### 2a — Create the services YAML file

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

### 2b — Launch ersap-shell with ERSAP_MONITOR_FE set

```bash
# Node B — one terminal
export ERSAP_HOME=/path/to/ersap
export ERSAP_MONITOR_FE="<monfe-ip>%7111_java"
ersap-shell
```

`ERSAP_MONITOR_FE` must be set **before** launching the shell. `run local` reads this
variable and injects it into the environment of the DPE process it spawns.

### 2c — Configure the shell

Inside `ersap-shell`:

```
ersap> set session      test
ersap> set servicesFile $ERSAP_USER_DATA/config/double-pipeline.yml
ersap> set fileList     $ERSAP_USER_DATA/config/files.txt
ersap> set inputDir     $ERSAP_USER_DATA/data/input
ersap> set outputDir    $ERSAP_USER_DATA/data/output
```

(`files.txt` can contain a single dummy filename — `SourceOfDoubles` generates data
internally and does not actually read input files.)

### 2d — Run

```
ersap> run local
```

`run local` now:
1. Starts one processing DPE on `<proc-host>` (acting as its own local front-end) with
   `--session test` and `ERSAP_MONITOR_FE=<monfe-ip>%7111_java` in its environment
2. Deploys `SourceOfDoubles`, `EventRateMonitor`, and `DoubleDumpSink` into that DPE
3. Launches the orchestrator to drive event processing

There is **no second DPE**. The processing DPE started by `run local` is the only one on
Node B.

Expected output from the shell after `run local`:
```
 Session          = test
 Using monitoring front-end <monfe-ip>%7111_java
 ...
[INFO] DPE started
[INFO] Deploying services...
[INFO] Starting orchestrator...
```

---

## Step 3 — Start TestMonitor (Node A or any node)

`TestMonitor` subscribes to the Monitor FE proxy and prints all received reports to stdout.

```bash
# Node A (or any node that can reach <monfe-ip>)
java -cp "$ERSAP_HOME/lib/*:$ERSAP_HOME/services/*" \
     org.jlab.epsci.ersap.examples.TestMonitor <monfe-ip> test
```

The two arguments are:
- `<monfe-ip>` — where the Monitor FE proxy is running
- `test` — the session to filter on; must match `--session` used by the processing DPE

Expected startup line:
```
TestMonitor listening on <monfe-ip>  (session=test) — press Ctrl-C to stop
```

---

## Step 4 — Expected Console Output

### 4a — DPE system report (every 10 seconds by default)

One block per report period, produced by `listenDpeReports`:

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
| `requests` (all three services) | All three counts increase together at the same rate |
| `failures` | Zero for a healthy run |
| `exec time` (EventRateMonitor) | Growing; divide by `requests` for average µs/event |
| `shm reads/writes` | Non-zero — confirms services communicate via shared memory (same DPE) |
| `bytes recv/sent` | Zero for an intra-DPE chain |

---

### 4b — User metrics from EventRateMonitor (every ~1 second)

One block per measurement window, produced by `listenUserMetrics`:

```
--- User Metrics: <proc-host>%7111_java:<username>:EventRateMonitor  session=test ---
  event_rate_hz         : 3127.4
  events_in_window      : 3127
  total_events          : 45831
```

**What to look for:**

| Field | Meaning |
|---|---|
| `event_rate_hz` | Aggregate throughput of all pool instances (events/sec) |
| `events_in_window` | Raw count in the last 1-second window; should roughly equal `event_rate_hz` |
| `total_events` | Cumulative since service start; should match `n_requests` in the DPE report |

Because `EventRateMonitor` uses shared static counters with a CAS-based window handoff,
exactly **one** user metrics message is published per second regardless of pool size.

---

## Step 5 — Verifying Consistency Between Report Types

| Check | How |
|---|---|
| Request count matches | `total_events` in user metrics ≈ `n_requests` for `EventRateMonitor` in DPE report |
| Rate is plausible | `event_rate_hz` × report-period-seconds ≈ increase in `n_requests` between two DPE reports |
| Intra-DPE chain confirmed | `shm_reads` and `shm_writes` for `EventRateMonitor` equal `n_requests` |
| No data-plane impact | `n_failures = 0` for all three services |

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No DPE report received | `ERSAP_MONITOR_FE` was not set before launching `ersap-shell`, or wrong host/port |
| No user metrics received | `ERSAP_MONITOR_FE` not set, or `EventRateMonitor` not in the composition |
| `event_rate_hz` is ~1/5th of expected | Old non-static `EventRateMonitor` on classpath; verify the deployed jar |
| `total_events` doesn't match `n_requests` | Normal for the first few seconds while windows align; should converge |
| `shm_reads = 0` | Services in separate containers or separate DPEs; ensure one container |
| Memory grows steadily | Leak in engine or reader; check `SourceOfDoubles.close()` |
