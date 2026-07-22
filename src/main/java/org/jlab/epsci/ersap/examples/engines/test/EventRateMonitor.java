/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.engines.test;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineMetricsPublisher;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intermediate service that measures and publishes the rate of incoming events
 * (i.e. the rate at which {@link #execute} is called across all pool instances)
 * via {@link EngineMetricsPublisher}, then forwards the received double value
 * unchanged to the next service in the composition.
 *
 * <p>Intended position in the composition:
 * <pre>
 *   SourceOfDoubles  →  EventRateMonitor  →  DoubleDumpSink
 * </pre>
 *
 * <p>All engine instances in the pool share static counters, so the Monitor FE
 * receives exactly one metrics message per 1-second window regardless of pool
 * size, reporting the aggregate throughput of the entire service.
 *
 * <p>Metrics published once per window:
 * <ul>
 *   <li>{@code event_rate_hz}      – aggregate events/sec across all pool instances</li>
 *   <li>{@code events_in_window}   – raw event count in the completed window</li>
 *   <li>{@code total_events}       – cumulative count since service start</li>
 * </ul>
 */
public class EventRateMonitor implements Engine {

    private static final long WINDOW_MS = 1000;

    // Shared across all pool instances of this service class.
    // AtomicLong gives thread-safe increments and CAS-based window handoff.
    private static final AtomicLong TOTAL_EVENTS   = new AtomicLong(0);
    private static final AtomicLong WINDOW_COUNT   = new AtomicLong(0);
    // 0 = window not yet started; set to System.currentTimeMillis() on first event.
    private static final AtomicLong WINDOW_START_MS = new AtomicLong(0);

    @Override
    public EngineData execute(EngineData input) {
        long now = System.currentTimeMillis();

        TOTAL_EVENTS.incrementAndGet();
        WINDOW_COUNT.incrementAndGet();

        // Initialize the window on the very first event across all instances.
        WINDOW_START_MS.compareAndSet(0, now);

        long windowStart = WINDOW_START_MS.get();
        long elapsed     = now - windowStart;

        if (elapsed >= WINDOW_MS) {
            // Only the instance that wins the CAS publishes; all others skip.
            if (WINDOW_START_MS.compareAndSet(windowStart, now)) {
                long count   = WINDOW_COUNT.getAndSet(0);
                double rateHz = count * 1000.0 / elapsed;
                EngineMetricsPublisher.publish("event_rate_hz",    rateHz);
                EngineMetricsPublisher.publish("events_in_window", count);
                EngineMetricsPublisher.publish("total_events",     TOTAL_EVENTS.get());
            }
        }

        return input;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return inputs.iterator().next();
    }

    @Override
    public EngineData configure(EngineData input) {
        return input;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.DOUBLE);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.DOUBLE);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Measures and publishes the aggregate execute() call rate to the Monitor FE";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "Vardan Gyurgyan";
    }

    @Override
    public void reset() {
        TOTAL_EVENTS.set(0);
        WINDOW_COUNT.set(0);
        WINDOW_START_MS.set(0);
    }

    @Override
    public void destroy() {
    }
}
