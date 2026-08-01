/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
/**
 * End-to-end test of the exporter: a fake Monitor FE publishes representative
 * messages, the exporter is scraped over HTTP, and the resulting exposition is
 * checked for the expected series, values and types. No running data-ring or
 * Monitor FE is needed.
 */
class PrometheusExporterIntegrationTest {

    private static final double DELTA = 1e-9;
    private static final long TIMEOUT_SECONDS = 20;

    /** A metric line of the text exposition format: name, optional labels, value. */
    private static final Pattern SAMPLE_LINE = Pattern.compile(
            "^[a-zA-Z_:][a-zA-Z0-9_:]*(\\{.*\\})? -?[0-9.eE+]+(NaN|Inf)?( \\d+)?$");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final FakeMonitorFe monitorFe = new FakeMonitorFe();
    private PrometheusExporter exporter;

    @AfterEach
    void stopExporter() {
        if (exporter != null) {
            exporter.close();
            exporter = null;
        }
    }

    @Test
    void exportsTheMetricsPublishedByTheMonitorFe() throws Exception {
        start();
        publishOneOfEach();

        String metrics = get("/metrics");

        // DPE system metrics, from the dpeReport subscription
        assertEquals(38.4, sample("ersap_dpe_cpu_usage_percent"), DELTA);
        assertEquals(631222786.0, sample("ersap_dpe_memory_usage_bytes"), DELTA);
        assertEquals(1.72, sample("ersap_dpe_system_load"), DELTA);
        assertEquals(8.0, sample("ersap_dpe_cores"), DELTA);

        // per-service counters, labelled by container, service and engine
        assertEquals(15000.0, sample("ersap_service_requests_total",
                serviceLabels("EventRateMonitor")), DELTA);
        assertEquals(3.0, sample("ersap_service_failures_total",
                serviceLabels("EventRateMonitor")), DELTA);
        assertEquals(0.312, sample("ersap_service_execution_time_seconds_total",
                serviceLabels("EventRateMonitor")), DELTA);
        assertEquals(0.0, sample("ersap_service_failures_total",
                serviceLabels("SourceOfDoubles")), DELTA);

        // container roll-up
        assertEquals(30000.0, sample("ersap_container_requests_total"), DELTA);

        // user metrics published by EventRateMonitor
        assertEquals(3127.4, sample("ersap_user_event_rate_hz"), 1e-6);
        assertEquals(3127.0, sample("ersap_user_events_in_window"), DELTA);
        // the Prometheus client appends _total to counter sample names
        assertEquals(45831.0, sample("ersap_user_total_events_total"), DELTA);

        // and the same values are actually in the exposition payload
        assertTrue(metrics.contains("# TYPE ersap_service_requests_total counter"), metrics);
        assertTrue(metrics.contains("# TYPE ersap_dpe_cpu_usage_percent gauge"), metrics);
        assertTrue(metrics.contains("# TYPE ersap_user_event_rate_hz gauge"), metrics);
        assertTrue(metrics.contains("# TYPE ersap_user_total_events_total counter"), metrics);
        assertTrue(metrics.contains("engine=\"EventRateMonitor\""), metrics);
        assertTrue(metrics.contains("session=\"test\""), metrics);
        assertTrue(metrics.contains("ersap_dpe_cpu_usage_percent{"), metrics);
    }

    @Test
    void servesValidPrometheusExposition() throws Exception {
        start();
        publishOneOfEach();

        String metrics = get("/metrics");

        int samples = 0;
        for (String line : metrics.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                assertTrue(trimmed.startsWith("# HELP ") || trimmed.startsWith("# TYPE "),
                        "unexpected comment: " + trimmed);
                continue;
            }
            assertTrue(SAMPLE_LINE.matcher(trimmed).matches(),
                    "not a valid exposition line: " + trimmed);
            samples++;
        }
        assertTrue(samples > 20, "expected many samples, got " + samples);
    }

    @Test
    void exposesExporterSelfMetrics() throws Exception {
        start();
        publishOneOfEach();

        assertEquals(1.0, sample("ersap_prometheus_exporter_up"), DELTA);
        assertEquals(1.0, sample("ersap_prometheus_exporter_messages_received_total",
                labels("source", "dpe_report")), DELTA);
        assertEquals(1.0, sample("ersap_prometheus_exporter_messages_received_total",
                labels("source", "user_metrics")), DELTA);
        assertEquals(0.0, sample("ersap_prometheus_exporter_metric_parse_errors_total"), DELTA);
        assertEquals(0.0, sample("ersap_prometheus_exporter_connection_errors_total"), DELTA);
        assertEquals(0.0, sample("ersap_prometheus_exporter_reconnects_total"), DELTA);
        assertTrue(sample("ersap_prometheus_exporter_metrics_processed_total") > 20);
        assertTrue(sample("ersap_prometheus_exporter_last_message_timestamp_seconds") > 0);
        assertEquals(exporter.metricRegistry().metricCount(),
                sample("ersap_prometheus_exporter_registered_metrics").intValue());
        assertTrue(sample("ersap_prometheus_exporter_registered_metrics") > 5);
        assertTrue(sample("ersap_prometheus_exporter_start_time_seconds") > 0);
    }

    @Test
    void countsValuesThatCannotBeExported() throws Exception {
        start();
        awaitSubscription();
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("state", "running");
        published.put("rate", 3.5);
        monitorFe.publishUserMetrics("test", MonitorReports.ENGINE, published);

        assertEquals(1.0, sample("ersap_prometheus_exporter_metric_parse_errors_total"), DELTA);
        assertEquals(3.5, sample("ersap_user_rate"), DELTA);
    }

    @Test
    void updatesExistingSeriesOnEveryMessage() throws Exception {
        start();
        awaitSubscription();
        for (int i = 1; i <= 5; i++) {
            monitorFe.publishUserMetrics("test", MonitorReports.ENGINE,
                    MonitorReports.userMetrics("event_rate_hz", 100.0 * i));
        }

        assertEquals(500.0, sample("ersap_user_event_rate_hz"), DELTA);
        assertEquals(1, exporter.metricRegistry().metricCount());
        assertEquals(1, exporter.metricRegistry().seriesCount());
        assertEquals(5.0, sample("ersap_prometheus_exporter_metrics_processed_total"), DELTA);
    }

    @Test
    void appliesFiltersAndStaticLabelsEndToEnd() throws Exception {
        start("--exclude", "ersap_dpe_*,ersap_container_*",
              "--label", "cluster=jlab");
        publishOneOfEach();

        String metrics = get("/metrics");
        assertTrue(metrics.contains("cluster=\"jlab\""), metrics);
        assertTrue(metrics.contains("ersap_service_requests_total"), metrics);
        assertTrue(!metrics.contains("ersap_dpe_cpu_usage_percent"), metrics);
        assertTrue(!metrics.contains("ersap_container_requests_total"), metrics);

        assertEquals(15000.0, exporter.collectorRegistry().getSampleValue(
                "ersap_service_requests_total",
                new String[] {"component", "source", "dpe", "host", "lang", "session",
                              "container", "service", "engine", "cluster"},
                new String[] {"service", "dpe_report", "10.1.1.10_java", "10.1.1.10", "java",
                              "test", "user", MonitorReports.ENGINE, "EventRateMonitor",
                              "jlab"}), DELTA);

        // filtered samples are accounted for, not silently lost
        assertTrue(sample("ersap_prometheus_exporter_metrics_dropped_total",
                labels("reason", "filtered")) > 0);
    }

    @Test
    void usesTheConfiguredMetricPrefix() throws Exception {
        start("--metric-prefix", "jlab");
        publishOneOfEach();

        String metrics = get("/metrics");
        assertTrue(metrics.contains("jlab_user_event_rate_hz"), metrics);
        assertTrue(metrics.contains("jlab_prometheus_exporter_up"), metrics);
        assertTrue(!metrics.contains("ersap_user_event_rate_hz"), metrics);
    }

    @Test
    void servesTheHealthEndpoint() throws Exception {
        start();

        HttpResponse<String> response = request("/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
        assertTrue(response.body().contains("\"monitorFeSubscribed\":true"), response.body());
    }

    @Test
    void reportsDegradedHealthWhileTheMonitorFeIsDown() throws Exception {
        monitorFe.failNextConnections(1000);
        start("--reconnect-interval", "1");

        waitFor(() -> !exporter.subscriber().isSubscribed());

        HttpResponse<String> response = request("/health");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"degraded\""), response.body());
        assertEquals(0.0, sample("ersap_prometheus_exporter_up"), DELTA);
        waitFor(() -> sample("ersap_prometheus_exporter_connection_errors_total") >= 1.0);

        // a temporary outage must not kill the exporter: /metrics still answers
        assertNotNull(get("/metrics"));
    }

    @Test
    void reconnectsAndResubscribesAfterAFailedStart() throws Exception {
        monitorFe.failNextConnections(2);
        start("--reconnect-interval", "1");

        assertTrue(monitorFe.awaitSubscription(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the exporter never recovered from the initial failures");
        assertTrue(monitorFe.connectAttempts() >= 3);
        assertEquals(2.0, sample("ersap_prometheus_exporter_connection_errors_total"), DELTA);
        assertEquals(1.0, sample("ersap_prometheus_exporter_up"), DELTA);

        // and it works once it is up
        publishOneOfEach();
        assertEquals(3127.4, sample("ersap_user_event_rate_hz"), 1e-6);
    }

    @Test
    void resubscribesWhenTheSubscriptionGoesStale() throws Exception {
        start("--reconnect-interval", "1", "--stale-timeout", "1");
        assertTrue(monitorFe.awaitSubscription(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        publishOneOfEach();
        int attemptsBefore = monitorFe.connectAttempts();

        // stop publishing: the stale timeout must tear the subscription down
        // and build a new one
        waitFor(() -> sample("ersap_prometheus_exporter_reconnects_total") >= 1.0);

        assertTrue(monitorFe.connectAttempts() > attemptsBefore);
        assertTrue(monitorFe.closedConnections() >= 1);
        waitFor(() -> exporter.subscriber().isSubscribed());
        assertTrue(monitorFe.awaitSubscription(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // the resubscribed connection delivers messages again
        monitorFe.publishUserMetrics("test", MonitorReports.ENGINE,
                MonitorReports.userMetrics("event_rate_hz", 77.0));
        assertEquals(77.0, sample("ersap_user_event_rate_hz"), DELTA);
    }

    @Test
    void shutsDownCleanly() throws Exception {
        start();
        publishOneOfEach();
        int port = exporter.prometheusPort();

        exporter.close();

        assertThrows(ConnectException.class, () -> get("http://127.0.0.1:" + port + "/metrics"));
        assertTrue(monitorFe.closedConnections() >= 1, "the Monitor FE connection was not closed");
        assertTrue(!exporter.subscriber().isSubscribed());

        // closing twice is harmless
        exporter.close();
        exporter = null;
    }

    // --------------------------------------------------------------- helpers

    private void start(String... extraArgs) throws IOException {
        String[] args = new String[extraArgs.length + 4];
        args[0] = "--prometheus-host";
        args[1] = "127.0.0.1";
        args[2] = "--prometheus-port";
        args[3] = "0";
        System.arraycopy(extraArgs, 0, args, 4, extraArgs.length);
        exporter = new PrometheusExporter(PrometheusExporterConfig.parse(args), monitorFe);
    }

    private void awaitSubscription() throws InterruptedException {
        assertTrue(monitorFe.awaitSubscription(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the exporter never subscribed to the fake Monitor FE");
    }

    private void publishOneOfEach() throws InterruptedException {
        awaitSubscription();
        monitorFe.publishDpeReport(MonitorReports.registration(), MonitorReports.runtime());
        monitorFe.publishUserMetrics(MonitorReports.SESSION, MonitorReports.ENGINE,
                MonitorReports.userMetrics());
    }

    /**
     * Finds the single sample exposed under {@code name}, whatever its labels.
     *
     * <p>{@code CollectorRegistry.getSampleValue(String)} only matches samples
     * with no labels at all, which almost nothing the exporter produces is.
     */
    private Double sample(String name) {
        List<Double> values = Collections.list(
                        exporter.collectorRegistry().metricFamilySamples()).stream()
                .flatMap(mfs -> mfs.samples.stream())
                .filter(s -> s.name.equals(name))
                .map(s -> s.value)
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            return null;
        }
        assertEquals(1, values.size(), "several series are exposed as " + name);
        return values.get(0);
    }

    private Double sample(String name, Map<String, String> labels) {
        return exporter.collectorRegistry().getSampleValue(name,
                labels.keySet().toArray(new String[0]),
                labels.values().toArray(new String[0]));
    }

    private static Map<String, String> labels(String name, String value) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(name, value);
        return labels;
    }

    private static Map<String, String> serviceLabels(String engine) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("component", "service");
        labels.put("source", "dpe_report");
        labels.put("dpe", "10.1.1.10_java");
        labels.put("host", "10.1.1.10");
        labels.put("lang", "java");
        labels.put("session", "test");
        labels.put("container", "user");
        labels.put("service", "10.1.1.10_java:user:" + engine);
        labels.put("engine", engine);
        return labels;
    }

    private String get(String path) throws IOException, InterruptedException {
        if (path.startsWith("http")) {
            return send(path).body();
        }
        return request(path).body();
    }

    private HttpResponse<String> request(String path) throws IOException, InterruptedException {
        return send("http://127.0.0.1:" + exporter.prometheusPort() + path);
    }

    private HttpResponse<String> send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("condition never became true within "
                + TIMEOUT_SECONDS + " s");
    }
}
