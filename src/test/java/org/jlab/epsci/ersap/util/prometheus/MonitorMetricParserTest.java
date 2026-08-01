/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.MonitorReportFactory;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class MonitorMetricParserTest {

    private static final String ENGINE = "10.1.1.10_java:user:EventRateMonitor";
    private static final double DELTA = 1e-9;

    private final MonitorMetricParser parser = new MonitorMetricParser();

    // ---------------------------------------------------------------- dpeReport

    @Test
    void parsesTheDpeLevelValuesOfARealReport() {
        MonitorMetricParser.ParseResult result = parseReport();

        assertEquals(38.4, value(result, "dpe.cpu_usage_percent"), DELTA);
        assertEquals(631222786.0, value(result, "dpe.memory_usage_bytes"), DELTA);
        assertEquals(1.72, value(result, "dpe.system_load"), DELTA);
        assertEquals(8.0, value(result, "dpe.cores"), DELTA);
        assertEquals(1908932608.0, value(result, "dpe.memory_size_bytes"), DELTA);
        assertEquals(0, result.rejected());
    }

    @Test
    void labelsDpeMetricsWithTheReportingComponent() {
        MonitorMetric metric = find(parseReport(), "dpe.cpu_usage_percent");

        assertEquals("dpe", metric.labels().get(MonitorMetric.LABEL_COMPONENT));
        assertEquals(MonitorMetricParser.SOURCE_DPE_REPORT,
                metric.labels().get(MonitorMetric.LABEL_SOURCE));
        assertEquals("10.1.1.10_java", metric.labels().get(MonitorMetric.LABEL_DPE));
        assertEquals("10.1.1.10", metric.labels().get(MonitorMetric.LABEL_HOST));
        assertEquals("java", metric.labels().get(MonitorMetric.LABEL_LANG));
        assertEquals("test", metric.labels().get(MonitorMetric.LABEL_SESSION));
    }

    @Test
    void parsesTheServiceCountersOfARealReport() {
        MonitorMetricParser.ParseResult result = parseReport();
        MonitorMetric requests = findService(result, "service.requests_total",
                "10.1.1.10_java:user:EventRateMonitor");

        assertEquals(15000.0, requests.value(), DELTA);
        assertEquals(MetricType.COUNTER, requests.type());
        assertEquals("service", requests.labels().get(MonitorMetric.LABEL_COMPONENT));
        assertEquals("user", requests.labels().get(MonitorMetric.LABEL_CONTAINER));
        assertEquals("EventRateMonitor", requests.labels().get(MonitorMetric.LABEL_ENGINE));

        assertEquals(3.0, findService(result, "service.failures_total",
                "10.1.1.10_java:user:EventRateMonitor").value(), DELTA);
        assertEquals(15000.0, findService(result, "service.shared_memory_reads_total",
                "10.1.1.10_java:user:EventRateMonitor").value(), DELTA);
        assertEquals(0.0, findService(result, "service.bytes_sent_total",
                "10.1.1.10_java:user:EventRateMonitor").value(), DELTA);
    }

    @Test
    void convertsExecutionTimeFromMicrosecondsToSeconds() {
        MonitorMetric execTime = findService(parseReport(),
                "service.execution_time_seconds_total",
                "10.1.1.10_java:user:EventRateMonitor");
        // 312000 us reported by the DPE
        assertEquals(0.312, execTime.value(), DELTA);
    }

    @Test
    void parsesContainerCounters() {
        MonitorMetric requests = find(parseReport(), "container.requests_total");
        assertEquals(30000.0, requests.value(), DELTA);
        assertEquals(MetricType.COUNTER, requests.type());
        assertEquals("container", requests.labels().get(MonitorMetric.LABEL_COMPONENT));
        assertEquals("user", requests.labels().get(MonitorMetric.LABEL_CONTAINER));
    }

    @Test
    void carriesTheSnapshotTimeAsSourceTimestamp() {
        MonitorMetric metric = find(parseReport(), "dpe.cpu_usage_percent");
        assertTrue(metric.hasTimestamp());
        assertTrue(metric.timestampSeconds() > 0);
    }

    @Test
    void toleratesAMissingRegistrationReport() {
        MonitorMetricParser.ParseResult result =
                parser.parseDpeReport(null, MonitorReports.runtime());

        assertEquals(38.4, value(result, "dpe.cpu_usage_percent"), DELTA);
        assertTrue(findOptional(result, "dpe.cores").isEmpty());
        assertNull(find(result, "dpe.cpu_usage_percent")
                .labels().get(MonitorMetric.LABEL_SESSION));
    }

    @Test
    void toleratesAMissingRuntimeReport() {
        MonitorMetricParser.ParseResult result =
                parser.parseDpeReport(MonitorReports.registration(), null);

        assertTrue(result.metrics().isEmpty());
        assertEquals(1, result.rejected());
    }

    @Test
    void skipsUnavailableDpeValues() {
        // cpu_usage absent, load negative: both are "not available" in ERSAP
        String document = MonitorReports.DOCUMENT
                .replace("\"cpu_usage\": 38.4,", "")
                .replace("\"load\": 1.72,", "\"load\": -1,");
        DpeRuntimeData runtime = MonitorReportFactory.runtime(document);

        MonitorMetricParser.ParseResult result =
                parser.parseDpeReport(MonitorReports.registration(), runtime);

        assertTrue(findOptional(result, "dpe.cpu_usage_percent").isEmpty());
        assertTrue(findOptional(result, "dpe.system_load").isEmpty());
        assertEquals(1, result.rejected(), "the NaN cpu_usage must be counted as rejected");
    }

    @Test
    void rejectsMalformedReportDocuments() {
        assertThrows(RuntimeException.class,
                () -> MonitorReportFactory.runtime("not json"));
        assertThrows(RuntimeException.class,
                () -> MonitorReportFactory.runtime("{}"));
        assertThrows(RuntimeException.class,
                () -> MonitorReportFactory.runtime(
                        "{\"DPERuntime\": {\"snapshot_time\": \"2021-06-20 12:35:00\"}}"));
    }

    // -------------------------------------------------------------- userMetrics

    @Test
    void parsesTheUserMetricsPublishedByEventRateMonitor() {
        // exactly the payload EventRateMonitor publishes, as decoded by
        // MonitorOrchestrator: new JSONObject(json).toMap()
        Map<String, Object> published = new JSONObject(
                "{\"event_rate_hz\":3127.4,\"events_in_window\":3127,\"total_events\":45831}")
                .toMap();

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(3, result.metrics().size());
        assertEquals(0, result.rejected());
        assertEquals(3127.4, value(result, "user.event_rate_hz"), 1e-6);
        assertEquals(3127.0, value(result, "user.events_in_window"), DELTA);
        assertEquals(45831.0, value(result, "user.total_events"), DELTA);
    }

    @Test
    void splitsTheCanonicalEngineNameIntoLabels() {
        MonitorMetric metric = find(parser.parseUserMetrics("test", ENGINE,
                Map.of("event_rate_hz", 1.0)), "user.event_rate_hz");

        assertEquals("engine", metric.labels().get(MonitorMetric.LABEL_COMPONENT));
        assertEquals(MonitorMetricParser.SOURCE_USER_METRICS,
                metric.labels().get(MonitorMetric.LABEL_SOURCE));
        assertEquals("10.1.1.10_java", metric.labels().get(MonitorMetric.LABEL_DPE));
        assertEquals("10.1.1.10", metric.labels().get(MonitorMetric.LABEL_HOST));
        assertEquals("user", metric.labels().get(MonitorMetric.LABEL_CONTAINER));
        assertEquals(ENGINE, metric.labels().get(MonitorMetric.LABEL_SERVICE));
        assertEquals("EventRateMonitor", metric.labels().get(MonitorMetric.LABEL_ENGINE));
        assertEquals("test", metric.labels().get(MonitorMetric.LABEL_SESSION));
    }

    @Test
    void keepsNonCanonicalEngineNamesWhole() {
        MonitorMetric metric = find(parser.parseUserMetrics("test", "not-canonical",
                Map.of("rate", 1.0)), "user.rate");

        assertEquals("not-canonical", metric.labels().get(MonitorMetric.LABEL_SERVICE));
        assertEquals("not-canonical", metric.labels().get(MonitorMetric.LABEL_ENGINE));
    }

    @Test
    void infersCountersFromTheMetricName() {
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("event_rate_hz", 1.0);
        published.put("events_in_window", 2);
        published.put("total_events", 3);
        published.put("bytes_total", 4);
        published.put("error_count", 5);

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(MetricType.GAUGE, find(result, "user.event_rate_hz").type());
        assertEquals(MetricType.GAUGE, find(result, "user.events_in_window").type());
        assertEquals(MetricType.COUNTER, find(result, "user.total_events").type());
        assertEquals(MetricType.COUNTER, find(result, "user.bytes_total").type());
        assertEquals(MetricType.COUNTER, find(result, "user.error_count").type());
    }

    @Test
    void counterHeuristicCanBeDisabled() {
        MonitorMetricParser gaugesOnly = new MonitorMetricParser(null, null);
        MonitorMetricParser.ParseResult result =
                gaugesOnly.parseUserMetrics("test", ENGINE, Map.of("total_events", 1));

        assertEquals(MetricType.GAUGE, find(result, "user.total_events").type());
    }

    @Test
    void convertsBooleansToOneAndZero() {
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("healthy", true);
        published.put("degraded", false);

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(1.0, value(result, "user.healthy"), DELTA);
        assertEquals(0.0, value(result, "user.degraded"), DELTA);
        assertEquals(0, result.rejected());
    }

    @Test
    void rejectsNonNumericValues() {
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("state", "processing events");
        published.put("payload", List.of(1, 2, 3));
        published.put("rate", 12.5);

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(1, result.metrics().size());
        assertEquals(12.5, value(result, "user.rate"), DELTA);
        assertEquals(2, result.rejected());
    }

    @Test
    void acceptsNumbersAndBooleansEncodedAsStrings() {
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("rate", "12.5");
        published.put("enabled", "true");
        published.put("disabled", "off");

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(12.5, value(result, "user.rate"), DELTA);
        assertEquals(1.0, value(result, "user.enabled"), DELTA);
        assertEquals(0.0, value(result, "user.disabled"), DELTA);
    }

    @Test
    void flattensNestedObjects() {
        Map<String, Object> published = new JSONObject(
                "{\"io\":{\"bytes\":{\"in\":10,\"out\":20}},\"rate\":1}").toMap();

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(10.0, value(result, "user.io_bytes_in"), DELTA);
        assertEquals(20.0, value(result, "user.io_bytes_out"), DELTA);
        assertEquals(1.0, value(result, "user.rate"), DELTA);
    }

    @Test
    void handlesEmptyAndNullUserMetrics() {
        assertTrue(parser.parseUserMetrics("test", ENGINE, null).metrics().isEmpty());
        assertTrue(parser.parseUserMetrics("test", ENGINE, Map.of()).metrics().isEmpty());
    }

    @Test
    void handlesJsonNullValues() {
        Map<String, Object> published = new JSONObject("{\"rate\":null,\"count\":1}").toMap();

        MonitorMetricParser.ParseResult result =
                parser.parseUserMetrics("test", ENGINE, published);

        assertEquals(1, result.metrics().size());
        assertEquals(1, result.rejected());
    }

    // ------------------------------------------------------------ toDouble

    @Test
    void convertsNumericTypes() {
        assertEquals(1.0, MonitorMetricParser.toDouble(1).getAsDouble(), DELTA);
        assertEquals(1.0, MonitorMetricParser.toDouble(1L).getAsDouble(), DELTA);
        assertEquals(1.5, MonitorMetricParser.toDouble(1.5f).getAsDouble(), 1e-6);
        assertEquals(1.5, MonitorMetricParser.toDouble(1.5d).getAsDouble(), DELTA);
        assertEquals(1.5, MonitorMetricParser.toDouble(
                new java.math.BigDecimal("1.5")).getAsDouble(), DELTA);
    }

    @Test
    void rejectsUnusableValues() {
        assertFalse(MonitorMetricParser.toDouble(null).isPresent());
        assertFalse(MonitorMetricParser.toDouble("").isPresent());
        assertFalse(MonitorMetricParser.toDouble("n/a").isPresent());
        assertFalse(MonitorMetricParser.toDouble(Double.NaN).isPresent());
        assertFalse(MonitorMetricParser.toDouble(Double.POSITIVE_INFINITY).isPresent());
        assertFalse(MonitorMetricParser.toDouble(JSONObject.NULL).isPresent());
        assertFalse(MonitorMetricParser.toDouble(List.of(1)).isPresent());
    }

    // ------------------------------------------------------------- helpers

    private MonitorMetricParser.ParseResult parseReport() {
        return parser.parseDpeReport(MonitorReports.registration(), MonitorReports.runtime());
    }

    private double value(MonitorMetricParser.ParseResult result, String source) {
        return find(result, source).value();
    }

    private MonitorMetric find(MonitorMetricParser.ParseResult result, String source) {
        return findOptional(result, source).orElseThrow(
                () -> new AssertionError("no metric named " + source + " in " + result.metrics()));
    }

    private Optional<MonitorMetric> findOptional(MonitorMetricParser.ParseResult result,
                                                 String source) {
        return result.metrics().stream()
                .filter(m -> m.source().equals(source))
                .findFirst();
    }

    private MonitorMetric findService(MonitorMetricParser.ParseResult result,
                                      String source, String service) {
        return result.metrics().stream()
                .filter(m -> m.source().equals(source))
                .filter(m -> service.equals(m.labels().get(MonitorMetric.LABEL_SERVICE)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no " + source + " for " + service));
    }

    /** Checks that OptionalDouble.isEmpty() is used consistently in the parser. */
    @Test
    void optionalDoubleContract() {
        OptionalDouble empty = MonitorMetricParser.toDouble("x");
        assertTrue(empty.isEmpty());
    }
}
