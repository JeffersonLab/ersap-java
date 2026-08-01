/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class PrometheusMetricRegistryTest {

    private static final double DELTA = 1e-9;

    private final CollectorRegistry collectors = new CollectorRegistry();

    // ------------------------------------------------- dynamic registration

    @Test
    void registersAMetricTheFirstTimeItIsSeen() {
        PrometheusMetricRegistry registry = registry();

        assertEquals(PrometheusMetricRegistry.UpdateResult.REGISTERED,
                registry.update(gauge("event_rate_hz", 12.5)));
        assertEquals(1, registry.metricCount());
        assertEquals(1, registry.seriesCount());
        assertEquals(12.5, sample("ersap_event_rate_hz"), DELTA);
    }

    @Test
    void reusesTheCollectorOnRepeatedUpdates() {
        PrometheusMetricRegistry registry = registry();

        registry.update(gauge("event_rate_hz", 1));
        for (int i = 2; i <= 50; i++) {
            assertEquals(PrometheusMetricRegistry.UpdateResult.UPDATED,
                    registry.update(gauge("event_rate_hz", i)));
        }

        assertEquals(1, registry.metricCount(), "no duplicate collectors");
        assertEquals(1, registry.seriesCount());
        assertEquals(50.0, sample("ersap_event_rate_hz"), DELTA);
    }

    @Test
    void keepsOneSeriesPerLabelCombination() {
        PrometheusMetricRegistry registry = registry();

        registry.update(gaugeFor("requests", 10, "svc-a"));
        registry.update(gaugeFor("requests", 20, "svc-b"));
        registry.update(gaugeFor("requests", 30, "svc-a"));

        assertEquals(1, registry.metricCount());
        assertEquals(2, registry.seriesCount());
        assertEquals(30.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service"}, new String[] {"svc-a"}), DELTA);
        assertEquals(20.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service"}, new String[] {"svc-b"}), DELTA);
    }

    @Test
    void exportsTheInferredMetricType() {
        PrometheusMetricRegistry registry = registry();
        registry.update(gauge("event_rate_hz", 1));
        registry.update(MonitorMetric.builder("requests_total")
                .type(MetricType.COUNTER).value(7).build());

        assertEquals("GAUGE", typeOf("ersap_event_rate_hz"));
        assertEquals("COUNTER", typeOf("ersap_requests_total"));
    }

    @Test
    void counterSamplesAlwaysEndInTotal() {
        // the Prometheus client enforces the counter naming convention: a family
        // name already ending in _total keeps it, and one that does not gets it
        // appended to the exposed sample name
        PrometheusMetricRegistry registry = registry();
        registry.update(MonitorMetric.builder("service.requests_total")
                .type(MetricType.COUNTER).value(1).build());
        registry.update(MonitorMetric.builder("user.total_events")
                .type(MetricType.COUNTER).value(2).build());

        assertEquals(1.0, sample("ersap_service_requests_total"), DELTA);
        assertNull(sample("ersap_service_requests_total_total"));
        assertEquals(2.0, sample("ersap_user_total_events_total"), DELTA);
        assertNull(sample("ersap_user_total_events"));
    }

    @Test
    void keepsTheFirstTypeOnConflict() {
        PrometheusMetricRegistry registry = registry();
        registry.update(MonitorMetric.builder("rate").type(MetricType.GAUGE).value(1).build());
        registry.update(MonitorMetric.builder("rate").type(MetricType.COUNTER).value(2).build());

        assertEquals("GAUGE", typeOf("ersap_rate"));
        assertEquals(2.0, sample("ersap_rate"), DELTA);
    }

    // ------------------------------------------------------------ collisions

    @Test
    void detectsSanitizationCollisions() {
        PrometheusMetricRegistry registry = registry();

        registry.update(gauge("Event Rate", 1));
        assertEquals(0, registry.collisionCount());

        registry.update(gauge("event.rate", 2));
        assertEquals(1, registry.collisionCount());
        assertEquals(1, registry.metricCount(), "colliding names share one family");
        assertEquals(2.0, sample("ersap_event_rate"), DELTA);

        // the same collision is only counted once
        registry.update(gauge("event.rate", 3));
        assertEquals(1, registry.collisionCount());

        // a third colliding name is a new collision
        registry.update(gauge("EVENT-RATE", 4));
        assertEquals(2, registry.collisionCount());
    }

    // ------------------------------------------------------- label handling

    @Test
    void normalizesIncompatibleLabelSets() {
        PrometheusMetricRegistry registry = registry();

        // first sample fixes the schema to {service}
        registry.update(MonitorMetric.builder("requests")
                .label("service", "svc-a").value(1).build());
        // a later sample misses "service" and adds an unexpected "engine"
        registry.update(MonitorMetric.builder("requests")
                .label("engine", "eng-a").value(2).build());

        assertEquals(1, registry.metricCount());
        assertEquals(2, registry.seriesCount());
        assertEquals(1.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service"}, new String[] {"svc-a"}), DELTA);
        // the missing label becomes the empty string, keeping the schema stable
        assertEquals(2.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service"}, new String[] {""}), DELTA);
    }

    @Test
    void appendsStaticLabelsToEverySeries() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .staticLabels(Map.of("cluster", "jlab"))
                .build();
        registry.register(collectors);

        registry.update(gaugeFor("requests", 5, "svc-a"));

        assertEquals(5.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service", "cluster"}, new String[] {"svc-a", "jlab"}), DELTA);
    }

    @Test
    void sanitizesStaticLabelNames() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .staticLabels(Map.of("data-center", "jlab"))
                .build();

        assertEquals(Map.of("data_center", "jlab"), registry.staticLabels());
    }

    // ------------------------------------------------------------- filtering

    @Test
    void appliesIncludeFilters() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .filter(MetricFilter.of(List.of("ersap_user_*"), List.of()))
                .build();
        registry.register(collectors);

        assertEquals(PrometheusMetricRegistry.UpdateResult.REGISTERED,
                registry.update(gauge("user.rate", 1)));
        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_FILTERED,
                registry.update(gauge("dpe.cpu_usage_percent", 2)));

        assertEquals(1, registry.metricCount());
        assertNotNull(sample("ersap_user_rate"));
        assertNull(sample("ersap_dpe_cpu_usage_percent"));
    }

    @Test
    void appliesExcludeFilters() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .filter(MetricFilter.of(List.of(), List.of("*_bytes_*")))
                .build();
        registry.register(collectors);

        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_FILTERED,
                registry.update(gauge("service.bytes_sent_total", 1)));
        assertEquals(PrometheusMetricRegistry.UpdateResult.REGISTERED,
                registry.update(gauge("service.requests_total", 2)));

        assertEquals(1, registry.metricCount());
    }

    // ------------------------------------------------------------- safeguards

    @Test
    void refusesTheReservedExporterNamespace() {
        PrometheusMetricRegistry registry = registry();

        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_RESERVED,
                registry.update(gauge("prometheus_exporter_up", 1)));
        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_RESERVED,
                registry.update(gauge("prometheus.exporter.messages_received_total", 1)));
        assertEquals(0, registry.metricCount());
    }

    @Test
    void rejectsUnusableValues() {
        PrometheusMetricRegistry registry = registry();

        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_INVALID,
                registry.update(gauge("rate", Double.NaN)));
        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_INVALID,
                registry.update(gauge("rate", Double.POSITIVE_INFINITY)));
        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_INVALID,
                registry.update(null));
        assertEquals(0, registry.metricCount());
    }

    @Test
    void capsTheNumberOfSeries() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .maxSeries(3)
                .build();
        registry.register(collectors);

        for (int i = 0; i < 3; i++) {
            assertEquals(PrometheusMetricRegistry.UpdateResult.REGISTERED,
                    registry.update(gaugeFor("requests", i, "svc-" + i)));
        }
        assertEquals(PrometheusMetricRegistry.UpdateResult.SKIPPED_LIMIT,
                registry.update(gaugeFor("requests", 9, "svc-overflow")));
        assertEquals(3, registry.seriesCount());
        assertEquals(1, registry.droppedSeriesCount());

        // existing series keep updating
        assertEquals(PrometheusMetricRegistry.UpdateResult.UPDATED,
                registry.update(gaugeFor("requests", 42, "svc-0")));
        assertEquals(42.0, collectors.getSampleValue("ersap_requests",
                new String[] {"service"}, new String[] {"svc-0"}), DELTA);
    }

    // ------------------------------------------------------------ timestamps

    @Test
    void doesNotExportTimestampsByDefault() {
        PrometheusMetricRegistry registry = registry();
        registry.update(MonitorMetric.builder("rate").value(1).timestampSeconds(1000).build());

        assertNull(sample("ersap_metric_last_update_timestamp_seconds"));
    }

    @Test
    void exportsTimestampsWhenEnabled() {
        PrometheusMetricRegistry registry = PrometheusMetricRegistry
                .builder(new MetricNameSanitizer("ersap"))
                .exportTimestamps(true)
                .build();
        registry.register(collectors);

        registry.update(MonitorMetric.builder("rate")
                .label(MonitorMetric.LABEL_ENGINE, "EventRateMonitor")
                .value(1)
                .timestampSeconds(1_624_192_500)
                .build());

        List<String> names = new ArrayList<>(
                MonitorMetric.CANONICAL_LABELS.size() + 1);
        List<String> values = new ArrayList<>();
        names.add("metric");
        values.add("ersap_rate");
        for (String label : MonitorMetric.CANONICAL_LABELS) {
            names.add(label);
            values.add(MonitorMetric.LABEL_ENGINE.equals(label) ? "EventRateMonitor" : "");
        }
        assertEquals(1_624_192_500.0,
                collectors.getSampleValue("ersap_metric_last_update_timestamp_seconds",
                        names.toArray(new String[0]), values.toArray(new String[0])), DELTA);
    }

    // ----------------------------------------------------------- concurrency

    @Test
    void survivesConcurrentUpdates() throws Exception {
        PrometheusMetricRegistry registry = registry();
        int threads = 8;
        int metricsPerThread = 40;
        int rounds = 25;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                final int thread = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int round = 0; round < rounds; round++) {
                            for (int m = 0; m < metricsPerThread; m++) {
                                // every thread writes to every metric and to its
                                // own label value, so families and series race
                                registry.update(gaugeFor("metric_" + m, round,
                                        "svc-" + thread));
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get());
        assertEquals(metricsPerThread, registry.metricCount());
        assertEquals(metricsPerThread * threads, registry.seriesCount(),
                "the series count must not drift under concurrency");
        for (int m = 0; m < metricsPerThread; m++) {
            for (int t = 0; t < threads; t++) {
                assertEquals(rounds - 1.0, collectors.getSampleValue("ersap_metric_" + m,
                        new String[] {"service"}, new String[] {"svc-" + t}), DELTA);
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private PrometheusMetricRegistry registry() {
        PrometheusMetricRegistry registry =
                PrometheusMetricRegistry.builder(new MetricNameSanitizer("ersap")).build();
        registry.register(collectors);
        return registry;
    }

    private static MonitorMetric gauge(String source, double value) {
        return MonitorMetric.builder(source).type(MetricType.GAUGE).value(value).build();
    }

    private static MonitorMetric gaugeFor(String source, double value, String service) {
        return MonitorMetric.builder(source)
                .type(MetricType.GAUGE)
                .label("service", service)
                .value(value)
                .build();
    }

    private Double sample(String name) {
        return collectors.getSampleValue(name);
    }

    /**
     * The Prometheus client strips the {@code _total} suffix from the family
     * name of a counter and re-adds it to the exposed sample names, so families
     * are looked up through {@code getNames()}.
     */
    private String typeOf(String name) {
        return Collections.list(collectors.metricFamilySamples()).stream()
                .filter(mfs -> List.of(mfs.getNames()).contains(name))
                .map(mfs -> mfs.type.name())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no metric family " + name));
    }
}
