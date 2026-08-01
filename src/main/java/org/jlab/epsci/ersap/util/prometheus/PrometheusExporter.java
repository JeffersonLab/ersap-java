/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;
import org.jlab.epsci.ersap.util.logging.SimpleLogger;

import java.io.IOException;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Exports the metrics broadcast by an ERSAP Monitor FE as Prometheus metrics.
 *
 * <p>The exporter subscribes to exactly the two Monitor FE topics that
 * {@code org.jlab.epsci.ersap.examples.TestMonitor} prints — {@code dpeReport}
 * and {@code userMetrics} — converts every numeric value it finds into a
 * Prometheus sample, and serves them on {@code /metrics}.
 *
 * <p>Typical invocation:
 * <pre>
 *   java -cp "$ERSAP_HOME/lib/*" \
 *        org.jlab.epsci.ersap.util.prometheus.PrometheusExporter \
 *        --monitor-host localhost \
 *        --monitor-port 9000 \
 *        --session test \
 *        --prometheus-port 9095 \
 *        --metric-prefix ersap
 * </pre>
 *
 * <p>Run with {@code --help} for the full list of settings. See the package
 * {@code README.md} for the metric catalogue and the Grafana dashboard.
 *
 * <p>The exporter runs until interrupted. A shutdown hook closes the
 * subscription, the messaging resources and the HTTP server on {@code SIGINT},
 * {@code SIGTERM} and normal JVM exit.
 */
public final class PrometheusExporter implements AutoCloseable {

    private static final int EXIT_CONFIG_ERROR = 1;
    private static final int EXIT_STARTUP_ERROR = 2;

    // Not a static field: SimpleLogger reads its level once, the first time any
    // logger is created, and main() must be able to set --log-level before that.
    private final Logger logger;
    private final PrometheusExporterConfig config;
    private final CollectorRegistry collectorRegistry;
    private final PrometheusMetricRegistry metricRegistry;
    private final ExporterSelfMetrics selfMetrics;
    private final MonitorMetricParser parser;
    private final MonitorFeSubscriber subscriber;
    private final MetricsHttpServer httpServer;
    private final CountDownLatch terminated = new CountDownLatch(1);

    /**
     * Builds and starts an exporter.
     *
     * @param config  the resolved configuration
     * @param factory creates the Monitor FE connections
     * @throws IOException if the HTTP endpoint could not be bound
     */
    public PrometheusExporter(PrometheusExporterConfig config,
                              MonitorConnection.Factory factory) throws IOException {
        this.logger = new LoggerFactory().getLogger(PrometheusExporter.class.getSimpleName());
        this.config = config;
        this.collectorRegistry = new CollectorRegistry();
        MetricNameSanitizer sanitizer = config.sanitizer();

        this.metricRegistry = PrometheusMetricRegistry.builder(sanitizer)
                .filter(config.filter())
                .staticLabels(config.staticLabels())
                .exportTimestamps(config.exportTimestamps())
                .maxSeries(config.maxSeries())
                .build();
        this.metricRegistry.register(collectorRegistry);

        this.selfMetrics = new ExporterSelfMetrics(collectorRegistry, sanitizer.prefix());
        this.selfMetrics.setUp(false);
        if (config.jvmMetrics()) {
            DefaultExports.register(collectorRegistry);
        }

        this.parser = new MonitorMetricParser(config.counterPattern(), ZoneId.systemDefault());
        this.subscriber = new MonitorFeSubscriber(config, factory,
                new MetricHandler(), selfMetrics);
        this.httpServer = new MetricsHttpServer(collectorRegistry, config.prometheusHost(),
                config.prometheusPort(), subscriber::isSubscribed);
        this.subscriber.start();
    }

    /**
     * Runs the exporter.
     *
     * @param args the command-line arguments; see {@code --help}
     */
    public static void main(String[] args) {
        PrometheusExporterConfig config;
        try {
            config = PrometheusExporterConfig.parse(args);
        } catch (PrometheusExporterConfig.ConfigException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println("run with --help for the list of options");
            System.exit(EXIT_CONFIG_ERROR);
            return;
        }
        if (config == null) {
            return;     // --help was printed
        }
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, config.logLevel());

        PrometheusExporter exporter;
        try {
            exporter = new PrometheusExporter(config, ErsapMonitorConnection.factory());
        } catch (IOException e) {
            System.err.println("error: could not start the Prometheus endpoint on "
                    + config.prometheusHost() + ":" + config.prometheusPort()
                    + ": " + e.getMessage());
            System.exit(EXIT_STARTUP_ERROR);
            return;
        }
        Runtime.getRuntime().addShutdownHook(
                new Thread(exporter::close, "ersap-exporter-shutdown"));

        Logger log = new LoggerFactory().getLogger(PrometheusExporter.class.getSimpleName());
        log.info("ERSAP Prometheus exporter started [{}]", config);
        log.info("scrape it at http://{}:{}/metrics  (health: /health)",
                config.prometheusHost(), exporter.prometheusPort());
        exporter.awaitTermination();
    }

    /**
     * Gets the port the Prometheus endpoint is bound to.
     *
     * @return the HTTP port
     */
    public int prometheusPort() {
        return httpServer.port();
    }

    /**
     * Gets the configuration this exporter was built from.
     *
     * @return the configuration
     */
    public PrometheusExporterConfig config() {
        return config;
    }

    /**
     * Gets the Prometheus registry holding every exported collector.
     *
     * @return the collector registry
     */
    public CollectorRegistry collectorRegistry() {
        return collectorRegistry;
    }

    /**
     * Gets the registry of dynamically discovered Monitor FE metrics.
     *
     * @return the metric registry
     */
    public PrometheusMetricRegistry metricRegistry() {
        return metricRegistry;
    }

    /**
     * Gets the exporter self-metrics.
     *
     * @return the self-metrics
     */
    public ExporterSelfMetrics selfMetrics() {
        return selfMetrics;
    }

    /**
     * Gets the Monitor FE subscriber.
     *
     * @return the subscriber
     */
    public MonitorFeSubscriber subscriber() {
        return subscriber;
    }

    /**
     * Blocks until the exporter is closed.
     */
    public void awaitTermination() {
        try {
            terminated.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (terminated.getCount() == 0) {
            return;
        }
        logger.info("shutting down the ERSAP Prometheus exporter");
        try {
            subscriber.close();
        } catch (RuntimeException e) {
            logger.error("could not stop the Monitor FE subscriber: {}", e.toString());
        }
        try {
            httpServer.close();
        } catch (RuntimeException e) {
            logger.error("could not stop the Prometheus HTTP endpoint: {}", e.toString());
        }
        terminated.countDown();
        logger.info("ERSAP Prometheus exporter stopped");
    }

    /**
     * Turns decoded Monitor FE messages into Prometheus samples.
     */
    private final class MetricHandler implements MonitorFeSubscriber.Handler {

        @Override
        public void onDpeReport(DpeRegistrationData registration, DpeRuntimeData runtime) {
            apply(parser.parseDpeReport(registration, runtime));
        }

        @Override
        public void onUserMetrics(String session, String engine, Map<String, Object> metrics) {
            apply(parser.parseUserMetrics(session, engine, metrics));
        }

        private void apply(MonitorMetricParser.ParseResult result) {
            selfMetrics.recordParseErrors(result.rejected());
            List<MonitorMetric> metrics = result.metrics();
            if (metrics.isEmpty()) {
                selfMetrics.syncRegistrySize(metricRegistry);
                return;
            }
            Map<PrometheusMetricRegistry.UpdateResult, Integer> counts =
                    new EnumMap<>(PrometheusMetricRegistry.UpdateResult.class);
            int applied = 0;
            for (MonitorMetric metric : metrics) {
                PrometheusMetricRegistry.UpdateResult outcome = metricRegistry.update(metric);
                if (outcome.isApplied()) {
                    applied++;
                } else {
                    counts.merge(outcome, 1, Integer::sum);
                }
            }
            selfMetrics.recordProcessed(applied);
            counts.forEach((outcome, count) -> selfMetrics.recordDropped(reason(outcome), count));
            selfMetrics.syncRegistrySize(metricRegistry);
        }

        private String reason(PrometheusMetricRegistry.UpdateResult outcome) {
            switch (outcome) {
                case SKIPPED_FILTERED:
                    return "filtered";
                case SKIPPED_RESERVED:
                    return "reserved_name";
                case SKIPPED_LIMIT:
                    return "series_limit";
                case SKIPPED_INVALID:
                    return "invalid_value";
                default:
                    return "unknown";
            }
        }
    }
}
