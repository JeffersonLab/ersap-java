/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allows a user {@link Engine} to publish arbitrary key-value metrics to the
 * Monitor FE during {@code execute()}. Call {@link #publish} any number of
 * times inside {@code execute()}; all metrics are batched and sent as a single
 * JSON message after {@code execute()} returns.
 *
 * <p>Only active when {@code ERSAP_MONITOR_FE} is set. No-op otherwise.
 */
public final class EngineMetricsPublisher {

    private static final ThreadLocal<Map<String, Object>> BUFFER =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private EngineMetricsPublisher() { }

    /**
     * Publish a named metric value from within {@link Engine#execute}.
     *
     * @param key   metric name (must be non-null and non-empty)
     * @param value metric value (any JSON-serializable object)
     */
    public static void publish(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("metric key must not be null or empty");
        }
        BUFFER.get().put(key, value);
    }

    /**
     * Called by ServiceEngine before each {@code execute()} to reset the buffer.
     */
    public static void clear() {
        BUFFER.get().clear();
    }

    /**
     * Called by ServiceEngine after {@code execute()} returns. Returns a snapshot
     * of all published metrics and clears the buffer.
     */
    public static Map<String, Object> drain() {
        Map<String, Object> snapshot = new LinkedHashMap<>(BUFFER.get());
        BUFFER.get().clear();
        return snapshot;
    }
}
