/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class DpeLifecycleTest {

    private static final double DELTA = 1e-9;
    private static final String CPU = "ersap_dpe_cpu_usage_percent";

    private final CollectorRegistry collectors = new CollectorRegistry();
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final PrometheusMetricRegistry registry = registry();
    private DpeLifecycle lifecycle;

    @AfterEach
    void closeLifecycle() {
        if (lifecycle != null) {
            lifecycle.close();
            lifecycle = null;
        }
    }

    @Test
    void newlyReportedDpeIsExportedAndRefreshed() {
        lifecycle = tracker(30, 5);
        publish("test", "dpe-1", 50.0);
        lifecycle.touch("test", "dpe-1");

        assertEquals(50.0, sample("test", "dpe-1"), DELTA);
        assertEquals(1, lifecycle.trackedCount());

        // repeated reports refresh lastSeen and never trigger expiration
        clock.addAndGet(TimeUnit.SECONDS.toMillis(20));
        publish("test", "dpe-1", 60.0);
        lifecycle.touch("test", "dpe-1");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(20));
        assertEquals(List.of(), lifecycle.expireOnce(),
                "20s since last touch is under the 30s timeout");
        assertEquals(60.0, sample("test", "dpe-1"), DELTA);
    }

    @Test
    void activeDpeIsNotExpiredBeforeTheTimeout() {
        lifecycle = tracker(30, 5);
        publish("test", "dpe-1", 10.0);
        lifecycle.touch("test", "dpe-1");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(29));
        assertTrue(lifecycle.expireOnce().isEmpty());
        assertNotNull(sample("test", "dpe-1"));
    }

    @Test
    void expiredDpeIsRemovedFromMetrics() {
        lifecycle = tracker(30, 5);
        publish("test", "dpe-1", 10.0);
        lifecycle.touch("test", "dpe-1");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(31));
        List<DpeLifecycle.Expired> expired = lifecycle.expireOnce();

        assertEquals(1, expired.size());
        assertEquals("dpe-1", expired.get(0).key().dpe());
        assertEquals(0, lifecycle.trackedCount());
        assertNull(sample("test", "dpe-1"),
                "expired DPE must not remain in the Prometheus label series");
    }

    @Test
    void twoDpesCoexistAndExpirationIsIsolated() {
        lifecycle = tracker(30, 5);
        publish("test", "dpe-1", 10.0);
        lifecycle.touch("test", "dpe-1");
        publish("test", "dpe-2", 20.0);
        lifecycle.touch("test", "dpe-2");

        // dpe-2 keeps reporting; dpe-1 goes silent
        clock.addAndGet(TimeUnit.SECONDS.toMillis(25));
        publish("test", "dpe-2", 21.0);
        lifecycle.touch("test", "dpe-2");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(10));
        List<DpeLifecycle.Expired> expired = lifecycle.expireOnce();

        assertEquals(1, expired.size());
        assertEquals("dpe-1", expired.get(0).key().dpe());
        assertNull(sample("test", "dpe-1"));
        assertEquals(21.0, sample("test", "dpe-2"), DELTA);
    }

    @Test
    void expirationIsSessionScoped() {
        lifecycle = tracker(30, 5);
        publish("sess-a", "shared-dpe", 1);
        lifecycle.touch("sess-a", "shared-dpe");
        publish("sess-b", "shared-dpe", 2);
        lifecycle.touch("sess-b", "shared-dpe");

        // let sess-a age out while sess-b keeps reporting
        clock.addAndGet(TimeUnit.SECONDS.toMillis(20));
        publish("sess-b", "shared-dpe", 3);
        lifecycle.touch("sess-b", "shared-dpe");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(15));
        List<DpeLifecycle.Expired> expired = lifecycle.expireOnce();

        assertEquals(1, expired.size());
        assertEquals("sess-a", expired.get(0).key().session());
        assertNull(sample("sess-a", "shared-dpe"));
        assertEquals(3.0, sample("sess-b", "shared-dpe"), DELTA);
    }

    @Test
    void previouslyExpiredDpeCanReconnect() {
        lifecycle = tracker(30, 5);
        publish("test", "dpe-1", 10);
        lifecycle.touch("test", "dpe-1");

        clock.addAndGet(TimeUnit.SECONDS.toMillis(31));
        lifecycle.expireOnce();
        assertNull(sample("test", "dpe-1"));

        // DPE comes back
        publish("test", "dpe-1", 42);
        lifecycle.touch("test", "dpe-1");

        assertEquals(42.0, sample("test", "dpe-1"), DELTA);
        assertEquals(1, lifecycle.trackedCount());
    }

    @Test
    void disabledTrackerNeverExpires() {
        lifecycle = tracker(0, 5);
        assertFalse(lifecycle.isEnabled());

        publish("test", "dpe-1", 10);
        lifecycle.touch("test", "dpe-1");

        clock.addAndGet(TimeUnit.HOURS.toMillis(1));
        assertTrue(lifecycle.expireOnce().isEmpty());
        assertEquals(0, lifecycle.trackedCount(),
                "touch is a no-op when the tracker is disabled");
        assertNotNull(sample("test", "dpe-1"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DpeLifecycle(registry, -1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new DpeLifecycle(registry, 30, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DpeLifecycle(registry, 30, -3));
    }

    @Test
    void concurrentTouchAndExpireAreSafe() throws Exception {
        lifecycle = tracker(30, 5);
        int threads = 8;
        int dpesPerThread = 20;
        int rounds = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                final int thread = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int round = 0; round < rounds; round++) {
                            for (int i = 0; i < dpesPerThread; i++) {
                                String dpe = "dpe-" + thread + "-" + i;
                                publish("test", dpe, round);
                                lifecycle.touch("test", dpe);
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            // sweeper races against writers with the clock advancing so
            // some DPEs really do expire while others are refreshing
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        clock.addAndGet(TimeUnit.SECONDS.toMillis(1));
                        lifecycle.expireOnce();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, failures.get());
    }

    @Test
    void schedulerRunsSweepsPeriodically() throws Exception {
        lifecycle = tracker(1, 1);
        lifecycle.start();

        publish("test", "dpe-scheduled", 5);
        lifecycle.touch("test", "dpe-scheduled");

        // advance the injected clock past the timeout; the scheduler
        // will notice on its next tick
        clock.addAndGet(TimeUnit.SECONDS.toMillis(5));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && lifecycle.trackedCount() > 0) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertEquals(0, lifecycle.trackedCount(), "scheduler never fired the sweep");
        assertNull(sample("test", "dpe-scheduled"));
    }

    // --------------------------------------------------------------- helpers

    private PrometheusMetricRegistry registry() {
        PrometheusMetricRegistry r =
                PrometheusMetricRegistry.builder(new MetricNameSanitizer("ersap")).build();
        r.register(collectors);
        return r;
    }

    private DpeLifecycle tracker(int staleSeconds, int sweepSeconds) {
        return new DpeLifecycle(registry, staleSeconds, sweepSeconds, clock::get);
    }

    private void publish(String session, String dpe, double value) {
        registry.update(MonitorMetric.builder("dpe.cpu_usage_percent")
                .type(MetricType.GAUGE)
                .label(MonitorMetric.LABEL_SESSION, session)
                .label(MonitorMetric.LABEL_DPE, dpe)
                .value(value)
                .build());
    }

    private Double sample(String session, String dpe) {
        return collectors.getSampleValue(CPU,
                new String[] {"session", "dpe"},
                new String[] {session, dpe});
    }
}
