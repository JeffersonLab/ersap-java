# Upgrade Plan: User Engine Metrics Publishing via Monitor FE

## Context

User `Engine` implementations run inside `ServiceEngine` but currently have no access to
xMsg or monitoring infrastructure. This upgrade lets an engine publish arbitrary key-value
metrics (e.g. hit counts, rates, custom physics quantities) through the Monitor FE proxy
during `execute()`, reusing the existing `monitorFe` connection pool already present in
`ServiceEngine`. No change to the core `Engine` interface is required.

---

## Data Plane Safety

**Short answer: the data chain is not broken by this change.**

The insertion point for `sendUserMetrics()` is *after* `sendResult()` in the normal execution
path — i.e., after the output data has already been forwarded to the next service in the
composition. The relevant flow in `ServiceEngine.execute()` is:

```
engine.execute(input)          ← user engine runs, metrics buffered in ThreadLocal
  ↓
sendResult(outData, links)     ← data passed to next service in chain  ← CHAIN COMPLETE
  ↓
sendMonitorData(state, outData) ← existing ring publish (ring path only)
  ↓
sendUserMetrics()              ← NEW: publish user key-value metrics
```

Because `sendUserMetrics()` is placed *after* `sendResult()`:
- Even if the metrics send fails or throws, the downstream service has already received its
  input — the chain is unaffected.
- `sendUncheck()` uses the `uncheckedPool` (fire-and-forget, same as `sendMonitorData`) — it
  does not block the execution thread.
- `sendUserMetrics()` is wrapped in its own `try-catch` that logs and swallows exceptions so
  no monitoring failure can ever surface as a data-plane error.

The two early-return paths — synchronous reply (`replyTo != null`) and ERROR status — do
**not** call `sendUserMetrics()`. This is intentional: no metrics are published for
request-reply interactions or failed executions.

---

## Design Overview

### Engine Developer API

A new static utility class `EngineMetricsPublisher` (in the `engine` package, visible to
engine developers) uses a `ThreadLocal` buffer:

```java
// Inside Engine.execute():
EngineMetricsPublisher.publish("hit_rate", 0.95);
EngineMetricsPublisher.publish("cluster_count", 42);
```

All metrics published during a single `execute()` call are batched into one JSON message and
sent to the Monitor FE after `execute()` returns. No change to the `Engine` interface.

### Topic Format

New topic constant `USER_METRICS = "userMetrics"` in `ErsapConstants`.

**Topic**: `userMetrics:<session>:<engine_canonical_name>`

**Payload** (JSON, one message per `execute()` call):
```json
{"hit_rate": 0.95, "cluster_count": 42}
```

### Message Flow

```
Engine.execute()
  └─ EngineMetricsPublisher.publish("k", v)   ← writes to ThreadLocal buffer
       ↓
ServiceEngine (after sendResult returns)
  └─ drainMetrics() → JSON → sendUncheck → monitorFe proxy
       ↓
MonitorOrchestrator.listenUserMetrics(handler)
  └─ UserMetricsHandler.handleMetrics(session, engine, Map<String,Object>)
```

---

## Files to Create / Modify

### 1. `src/main/java/org/jlab/epsci/ersap/base/core/ErsapConstants.java`
Add one constant:
```java
/** Topic prefix for user-defined engine metrics published to the Monitor FE. */
public static final String USER_METRICS = "userMetrics";
```

---

### 2. NEW: `src/main/java/org/jlab/epsci/ersap/engine/EngineMetricsPublisher.java`

```java
package org.jlab.epsci.ersap.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allows a user Engine to publish arbitrary key-value metrics to the Monitor FE
 * during execute(). Call publish() any number of times inside execute(); metrics
 * are batched and sent as a single JSON message after execute() returns.
 *
 * Only active when ERSAP_MONITOR_FE is set. No-op otherwise.
 */
public final class EngineMetricsPublisher {

    private static final ThreadLocal<Map<String, Object>> BUFFER =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private EngineMetricsPublisher() { }

    /** Publish a named metric value from within Engine.execute(). */
    public static void publish(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("metric key must not be null or empty");
        }
        BUFFER.get().put(key, value);
    }

    /** Called by ServiceEngine before execute() to reset the buffer. */
    static void clear() {
        BUFFER.get().clear();
    }

    /**
     * Called by ServiceEngine after execute(). Returns a snapshot of all
     * published metrics and clears the buffer.
     */
    static Map<String, Object> drain() {
        Map<String, Object> snapshot = new LinkedHashMap<>(BUFFER.get());
        BUFFER.get().clear();
        return snapshot;
    }
}
```

---

### 3. `src/main/java/org/jlab/epsci/ersap/sys/ServiceEngine.java`

**Before** `executeEngine(inData)` (line ~141), clear the buffer:
```java
EngineMetricsPublisher.clear();
outData = executeEngine(inData);
```

**After** both `sendResult()` calls (lines ~180-186) and after `sendMonitorData()`,
add a call to the new private method at the end of the normal-path block:

```java
if (sysConfig.isRingRequest()) {
    String executionState = outData.getExecutionState();
    if (!executionState.isEmpty()) {
        sendResult(inData, getLinks(inData, outData));
        sendMonitorData(executionState, outData);
    } else {
        sendResult(outData, getLinks(inData, outData));
    }
} else {
    sendResult(outData, getLinks(inData, outData));
}
sendUserMetrics();    // ← NEW: after chain data is already sent
```

New private method:
```java
private void sendUserMetrics() {
    if (monitorFe == null) return;
    try {
        Map<String, Object> metrics = EngineMetricsPublisher.drain();
        if (metrics.isEmpty()) return;

        String json = new JSONObject(metrics).toString();
        EngineData metricData = new EngineData();
        metricData.setData(EngineDataType.JSON.mimeType(), json);

        xMsgTopic topic = xMsgTopic.wrap(ErsapConstants.USER_METRICS
                + xMsgConstants.TOPIC_SEP + sysReport.getSession()
                + xMsgConstants.TOPIC_SEP + base.getEngine());
        xMsgMessage msg = DataUtil.serialize(topic, metricData,
                Collections.singleton(EngineDataType.JSON));
        base.sendUncheck(monitorFe.getProxyAddress(), msg);
    } catch (Exception e) {
        Logging.error("Could not send user metrics for %s: %s", base.getName(), e.getMessage());
    }
}
```

---

### 4. NEW: `src/main/java/org/jlab/epsci/ersap/std/orchestrators/UserMetricsHandler.java`

```java
package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.engine.EngineDataType;
import java.util.Map;
import java.util.Set;

/** Callback interface for receiving user-defined engine metrics from the Monitor FE. */
public interface UserMetricsHandler {

    /** Data types this handler can deserialize (typically singleton JSON). */
    Set<EngineDataType> dataTypes();

    /**
     * Called once per engine execute() invocation that published at least one metric.
     *
     * @param session  the DPE session
     * @param engine   the canonical engine name
     * @param metrics  the key-value pairs published by the engine
     */
    void handleMetrics(String session, String engine, Map<String, Object> metrics);
}
```

---

### 5. `src/main/java/org/jlab/epsci/ersap/base/ErsapSubscriptions.java`

Add two methods to `GlobalSubscriptionBuilder`:

```java
/** Subscribe to user metrics published by all engines. */
public ServiceSubscription userMetrics() {
    xMsgTopic topic = MessageUtil.buildTopic(ErsapConstants.USER_METRICS, "");
    return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd, topic);
}

/** Subscribe to user metrics filtered by session and engine name. */
public ServiceSubscription userMetrics(String session, String engine) {
    String keyword = session + xMsgConstants.TOPIC_SEP + engine;
    xMsgTopic topic = buildMatchingTopic(ErsapConstants.USER_METRICS, keyword);
    return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd, topic);
}
```

---

### 6. `src/main/java/org/jlab/epsci/ersap/std/orchestrators/MonitorOrchestrator.java`

Add two new public listen methods:

```java
/**
 * Listen to user-defined engine metrics from all engines.
 *
 * @param handler user metrics handler
 */
public void listenUserMetrics(UserMetricsHandler handler) throws ErsapException {
    orchestrator.listen()
            .userMetrics()
            .withDataTypes(handler.dataTypes())
            .start(msg -> dispatchUserMetrics(msg, handler));
    Logging.info("Subscribed to all user engine metrics");
}

/**
 * Listen to user-defined engine metrics from a specific session and engine.
 *
 * @param session  session to filter on
 * @param engine   canonical engine name to filter on
 * @param handler  user metrics handler
 */
public void listenUserMetrics(String session, String engine, UserMetricsHandler handler)
        throws ErsapException {
    orchestrator.listen()
            .userMetrics(session, engine)
            .withDataTypes(handler.dataTypes())
            .start(msg -> dispatchUserMetrics(msg, handler));
    Logging.info("Subscribed to user metrics for engine \"%s\" session \"%s\"", engine, session);
}

private void dispatchUserMetrics(EngineData data, UserMetricsHandler handler) {
    try {
        String topic  = data.getCommunicationId();  // carries the full topic string
        String[] parts = topic.split(":");           // userMetrics:session:engine
        String session = parts.length > 1 ? parts[1] : "";
        String engine  = parts.length > 2 ? parts[2] : "";
        String json    = (String) data.getData();
        Map<String, Object> metrics = new JSONObject(json).toMap();
        handler.handleMetrics(session, engine, metrics);
    } catch (Exception e) {
        Logging.error("Error dispatching user metrics: %s", e.getMessage());
    }
}
```

---

## Example User Engine

```java
package org.jlab.epsci.ersap.examples;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineMetricsPublisher;

import java.util.Set;

/**
 * Example engine that publishes custom physics metrics on every execution.
 * Metrics are sent to the Monitor FE asynchronously after execute() returns —
 * no changes to the return value or data chain are needed.
 */
public class ClusterFinderEngine implements Engine {

    // Internal counters maintained across events
    private long totalEvents = 0;
    private long totalClusters = 0;

    @Override
    public EngineData execute(EngineData input) {
        // --- normal engine logic ---
        int clustersFound = findClusters(input);
        totalEvents++;
        totalClusters += clustersFound;

        // --- publish custom metrics ---
        // These are batched and sent to Monitor FE after this method returns.
        // They do not affect the return value or the data passed to the next service.
        EngineMetricsPublisher.publish("clusters_this_event",  clustersFound);
        EngineMetricsPublisher.publish("total_events",         totalEvents);
        EngineMetricsPublisher.publish("avg_clusters_per_event",
                totalEvents > 0 ? (double) totalClusters / totalEvents : 0.0);
        EngineMetricsPublisher.publish("occupancy_percent",    computeOccupancy(input));

        // Return data to the next service in the chain as usual
        input.setData(EngineDataType.STRING, "clusters:" + clustersFound);
        return input;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    @Override
    public EngineData configure(EngineData input) {
        return input;
    }

    @Override public Set<EngineDataType> getInputDataTypes()  { return Set.of(EngineDataType.STRING); }
    @Override public Set<EngineDataType> getOutputDataTypes() { return Set.of(EngineDataType.STRING); }
    @Override public Set<String> getStates()                  { return Set.of(); }
    @Override public String getDescription()                  { return "Cluster finder engine"; }
    @Override public String getVersion()                      { return "1.0"; }
    @Override public String getAuthor()                       { return "jlab"; }
    @Override public void reset()   { totalEvents = 0; totalClusters = 0; }
    @Override public void destroy() { }

    private int findClusters(EngineData input) {
        // ... real cluster-finding logic ...
        return 7;
    }

    private double computeOccupancy(EngineData input) {
        // ... real occupancy calculation ...
        return 0.42;
    }
}
```

**Resulting Monitor FE message** (on topic `userMetrics:prod:localhost%7771_java:myContainer:ClusterFinderEngine`):
```json
{
  "clusters_this_event": 7,
  "total_events": 1024,
  "avg_clusters_per_event": 6.8,
  "occupancy_percent": 0.42
}
```

---

## Monitoring Side: Subscribing to User Metrics

```java
MonitorOrchestrator monitor = new MonitorOrchestrator(new DataRingAddress("monitor-host"));

monitor.listenUserMetrics(new UserMetricsHandler() {
    @Override
    public Set<EngineDataType> dataTypes() {
        return Set.of(EngineDataType.JSON);
    }

    @Override
    public void handleMetrics(String session, String engine, Map<String, Object> metrics) {
        System.out.printf("[%s] %s → %s%n", session, engine, metrics);
        // forward to Prometheus, InfluxDB, Grafana, etc.
    }
});
```

---

## Summary of Changes

| File | Type | Change |
|---|---|---|
| `ErsapConstants.java` | Modify | Add `USER_METRICS = "userMetrics"` constant |
| `EngineMetricsPublisher.java` | Create | ThreadLocal buffer + `publish()` / `drain()` / `clear()` |
| `ServiceEngine.java` | Modify | Clear buffer before execute, call `sendUserMetrics()` after `sendResult()` |
| `UserMetricsHandler.java` | Create | Callback interface for receiving user metrics |
| `ErsapSubscriptions.java` | Modify | Add `userMetrics()` methods to `GlobalSubscriptionBuilder` |
| `MonitorOrchestrator.java` | Modify | Add `listenUserMetrics()` methods + `dispatchUserMetrics()` |
