/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

import java.util.List;

/**
 * Health and operational metrics about the exporter itself.
 *
 * <p>These are always exported, regardless of the include/exclude filters, so
 * that the exporter stays observable even when the payload is filtered down to
 * nothing. All names live under {@code <prefix>prometheus_exporter_}, a
 * namespace that {@link PrometheusMetricRegistry} refuses to let Monitor FE
 * metrics enter.
 *
 * <p>With the default {@code ersap} prefix the exposed names are:
 * <pre>
 *   ersap_prometheus_exporter_up
 *   ersap_prometheus_exporter_messages_received_total
 *   ersap_prometheus_exporter_metrics_processed_total
 *   ersap_prometheus_exporter_metrics_dropped_total
 *   ersap_prometheus_exporter_metric_parse_errors_total
 *   ersap_prometheus_exporter_connection_errors_total
 *   ersap_prometheus_exporter_reconnects_total
 *   ersap_prometheus_exporter_metric_name_collisions_total
 *   ersap_prometheus_exporter_last_message_timestamp_seconds
 *   ersap_prometheus_exporter_registered_metrics
 *   ersap_prometheus_exporter_registered_series
 *   ersap_prometheus_exporter_start_time_seconds
 * </pre>
 */
public final class ExporterSelfMetrics {

    /** The namespace segment shared by every exporter self-metric. */
    public static final String NAMESPACE = "prometheus_exporter";

    private static final double MILLIS_PER_SECOND = 1000.0;

    /** The bounded set of values the {@code reason} label can take. */
    static final List<String> DROP_REASONS =
            List.of("filtered", "reserved_name", "series_limit", "invalid_value");

    private final Gauge up;
    private final Counter messagesReceived;
    private final Counter metricsProcessed;
    private final Counter metricsDropped;
    private final Counter parseErrors;
    private final Counter connectionErrors;
    private final Counter reconnects;
    private final Counter collisions;
    private final Gauge lastMessage;
    private final Gauge registeredMetrics;
    private final Gauge registeredSeries;
    private final Gauge startTime;

    /**
     * Registers the exporter self-metrics.
     *
     * @param registry the Prometheus registry to register into
     * @param prefix   the configured metric prefix, including its trailing
     *                 underscore (see {@link MetricNameSanitizer#prefix()})
     */
    public ExporterSelfMetrics(CollectorRegistry registry, String prefix) {
        String base = (prefix == null ? "" : prefix) + NAMESPACE + "_";

        up = Gauge.build()
                .name(base + "up")
                .help("1 when the Monitor FE subscription is established, 0 otherwise")
                .register(registry);
        messagesReceived = Counter.build()
                .name(base + "messages_received_total")
                .help("Messages received from the Monitor FE")
                .labelNames("source")
                .register(registry);
        metricsProcessed = Counter.build()
                .name(base + "metrics_processed_total")
                .help("Metric samples decoded from Monitor FE messages and exported")
                .register(registry);
        metricsDropped = Counter.build()
                .name(base + "metrics_dropped_total")
                .help("Metric samples that were decoded but not exported")
                .labelNames("reason")
                .register(registry);
        parseErrors = Counter.build()
                .name(base + "metric_parse_errors_total")
                .help("Monitor FE values that could not be decoded into a numeric metric")
                .register(registry);
        connectionErrors = Counter.build()
                .name(base + "connection_errors_total")
                .help("Failed attempts to connect or subscribe to the Monitor FE")
                .register(registry);
        reconnects = Counter.build()
                .name(base + "reconnects_total")
                .help("Successful resubscriptions to the Monitor FE after a failure or stall")
                .register(registry);
        collisions = Counter.build()
                .name(base + "metric_name_collisions_total")
                .help("Distinct Monitor FE metric names that sanitized onto an existing name")
                .register(registry);
        lastMessage = Gauge.build()
                .name(base + "last_message_timestamp_seconds")
                .help("Time the last Monitor FE message was received, in seconds since the epoch")
                .register(registry);
        registeredMetrics = Gauge.build()
                .name(base + "registered_metrics")
                .help("Monitor FE metric families currently registered")
                .register(registry);
        registeredSeries = Gauge.build()
                .name(base + "registered_series")
                .help("Monitor FE time series currently registered")
                .register(registry);
        startTime = Gauge.build()
                .name(base + "start_time_seconds")
                .help("Start time of the exporter, in seconds since the epoch")
                .register(registry);
        startTime.setToCurrentTime();

        // create the labelled series up front so that rate() over them works
        // from the first scrape instead of only after the first observation
        messagesReceived.labels(MonitorMetricParser.SOURCE_DPE_REPORT);
        messagesReceived.labels(MonitorMetricParser.SOURCE_USER_METRICS);
        for (String reason : DROP_REASONS) {
            metricsDropped.labels(reason);
        }
    }

    /**
     * Sets the connection status.
     *
     * @param connected true when subscribed to the Monitor FE
     */
    public void setUp(boolean connected) {
        up.set(connected ? 1 : 0);
    }

    /**
     * Tells whether the exporter currently reports itself as connected.
     *
     * @return true when {@code up} is 1
     */
    public boolean isUp() {
        return up.get() > 0;
    }

    /**
     * Records the reception of a Monitor FE message.
     *
     * @param source          the subscription the message arrived on
     * @param receivedMillis  reception time, in milliseconds since the epoch
     */
    public void recordMessage(String source, long receivedMillis) {
        messagesReceived.labels(source).inc();
        lastMessage.set(receivedMillis / MILLIS_PER_SECOND);
    }

    /**
     * Records metric samples that were successfully exported.
     *
     * @param count the number of samples
     */
    public void recordProcessed(int count) {
        if (count > 0) {
            metricsProcessed.inc(count);
        }
    }

    /**
     * Records metric samples that were decoded but not exported.
     *
     * @param reason a bounded reason label, such as {@code filtered} or {@code limit}
     * @param count  the number of samples
     */
    public void recordDropped(String reason, int count) {
        if (count > 0) {
            metricsDropped.labels(reason).inc(count);
        }
    }

    /**
     * Records values that could not be decoded into a numeric metric.
     *
     * @param count the number of values
     */
    public void recordParseErrors(int count) {
        if (count > 0) {
            parseErrors.inc(count);
        }
    }

    /**
     * Records a failed connection or subscription attempt.
     */
    public void recordConnectionError() {
        connectionErrors.inc();
    }

    /**
     * Records a successful resubscription to the Monitor FE.
     */
    public void recordReconnect() {
        reconnects.inc();
    }

    /**
     * Publishes the current registry size.
     *
     * @param registry the payload metric registry
     */
    public void syncRegistrySize(PrometheusMetricRegistry registry) {
        registeredMetrics.set(registry.metricCount());
        registeredSeries.set(registry.seriesCount());
        collisions.inc(registry.collisionCount() - collisions.get());
    }
}
