/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.monitor;

import org.jlab.epsci.ersap.base.ContainerRuntimeData;
import org.jlab.epsci.ersap.base.DataRingAddress;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.ServiceRuntimeData;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.MonitorOrchestrator;
import org.jlab.epsci.ersap.util.OptUtils;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import static java.util.Arrays.asList;

/**
 * Subscribes to the periodic reports published by ERSAP DPEs and exposes
 * them as Prometheus metrics on a {@code /metrics} HTTP endpoint.
 * <p>
 * This mirrors {@link InfluxDbReporter}: it is a standalone process that
 * connects to a DPE front-end (or a dedicated monitoring front-end) and
 * listens to {@code dpeReport} messages. It does not require any change to
 * the DPEs it monitors, and works the same way for Java, C++ or Python DPEs,
 * since all of them publish the same JSON report.
 */
public class PrometheusReporter implements DpeReportHandler {

    private static final String DEFAULT_MON_HOST = ErsapUtil.localhost();
    private static final int DEFAULT_MON_PORT = ErsapConstants.MONITOR_PORT;
    private static final int DEFAULT_METRICS_PORT = 9200;

    private static final String[] DPE_LABELS = {"dpe", "session"};
    private static final String[] SERVICE_LABELS = {"dpe", "container", "service", "session"};

    private final Gauge cpuUsage = Gauge.build()
            .name("ersap_dpe_cpu_usage_percent")
            .help("DPE process CPU usage, as a percentage (0-100)")
            .labelNames(DPE_LABELS)
            .register();

    private final Gauge memoryUsage = Gauge.build()
            .name("ersap_dpe_memory_usage_bytes")
            .help("DPE process memory usage, in bytes")
            .labelNames(DPE_LABELS)
            .register();

    private final Gauge systemLoad = Gauge.build()
            .name("ersap_dpe_system_load")
            .help("System load average of the node running the DPE")
            .labelNames(DPE_LABELS)
            .register();

    private final Gauge lastReportTimestamp = Gauge.build()
            .name("ersap_dpe_last_report_timestamp_seconds")
            .help("Unix timestamp of the last report received from a DPE")
            .labelNames(DPE_LABELS)
            .register();

    private final DeltaCounter requestsTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_requests_total")
            .help("Total number of requests received by a service")
            .labelNames(SERVICE_LABELS)
            .register());

    private final DeltaCounter failuresTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_failures_total")
            .help("Total number of failed requests processed by a service")
            .labelNames(SERVICE_LABELS)
            .register());

    private final DeltaCounter executionSecondsTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_execution_seconds_total")
            .help("Accumulated engine execution time of a service, in seconds")
            .labelNames(SERVICE_LABELS)
            .register(), 1.0 / 1_000_000.0); // source value is in microseconds

    private final DeltaCounter sharedMemoryReadsTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_shared_memory_reads_total")
            .help("Total number of requests a service received through DPE shared memory")
            .labelNames(SERVICE_LABELS)
            .register());

    private final DeltaCounter sharedMemoryWritesTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_shared_memory_writes_total")
            .help("Total number of requests a service sent through DPE shared memory")
            .labelNames(SERVICE_LABELS)
            .register());

    private final DeltaCounter bytesReceivedTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_bytes_received_total")
            .help("Total number of bytes a service received over the network")
            .labelNames(SERVICE_LABELS)
            .register());

    private final DeltaCounter bytesSentTotal = new DeltaCounter(Counter.build()
            .name("ersap_service_bytes_sent_total")
            .help("Total number of bytes a service sent over the network")
            .labelNames(SERVICE_LABELS)
            .register());

    public static void main(String[] args) {
        PROptionParser parser = new PROptionParser();
        if (!parser.parse(args)) {
            System.exit(1);
        }
        if (parser.hasHelp()) {
            System.out.println(parser.usage());
            System.exit(0);
        }
        try {
            DefaultExports.initialize();

            HTTPServer httpServer = new HTTPServer(
                    new InetSocketAddress(parser.metricsPort()), CollectorRegistry.defaultRegistry);

            DataRingAddress proxyAddress =
                    new DataRingAddress(parser.proxyHost(), parser.proxyPort());
            MonitorOrchestrator monitor = new MonitorOrchestrator(proxyAddress);
            PrometheusReporter reporter = new PrometheusReporter();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                monitor.close();
                httpServer.close();
            }));

            String session = parser.session();
            if (session == null) {
                monitor.listenDpeReports(reporter);
            } else {
                monitor.listenDpeReports(session, reporter);
            }
            System.out.printf("%s: serving ERSAP Prometheus metrics on port %d%n",
                    ErsapUtil.getCurrentTime(), parser.metricsPort());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    @Override
    public void handleReport(DpeRegistrationData dpeRegistration, DpeRuntimeData dpeRuntime) {
        String dpe = dpeRuntime.name().canonicalName();
        String session = dpeRegistration.session();

        cpuUsage.labels(dpe, session).set(dpeRuntime.cpuUsage());
        memoryUsage.labels(dpe, session).set(dpeRuntime.memoryUsage());
        systemLoad.labels(dpe, session).set(dpeRuntime.systemLoad());
        lastReportTimestamp.labels(dpe, session).setToCurrentTime();

        for (ContainerRuntimeData container : dpeRuntime.containers()) {
            String containerName = container.name().name();
            for (ServiceRuntimeData service : container.services()) {
                String[] labels = {dpe, containerName, service.name().name(), session};

                requestsTotal.update(service.numRequests(), labels);
                failuresTotal.update(service.numFailures(), labels);
                executionSecondsTotal.update(service.executionTime(), labels);
                sharedMemoryReadsTotal.update(service.sharedMemoryReads(), labels);
                sharedMemoryWritesTotal.update(service.sharedMemoryWrites(), labels);
                bytesReceivedTotal.update(service.bytesReceived(), labels);
                bytesSentTotal.update(service.bytesSent(), labels);
            }
        }
    }


    /**
     * Turns the cumulative-since-deploy values reported by a DPE into a
     * proper monotonic Prometheus {@link Counter}. A DPE only ever reports
     * its own running total, not a delta, so this keeps the last value seen
     * per label set and forwards the difference. If a report shows a lower
     * value than before (the DPE or a service restarted), the local baseline
     * is rebased to the new value and nothing is exported for that cycle,
     * so the counter exposed to Prometheus never goes backwards.
     */
    private static final class DeltaCounter {

        private final Counter counter;
        private final double scale;
        private final Map<String, Long> lastValues = new ConcurrentHashMap<>();

        DeltaCounter(Counter counter) {
            this(counter, 1.0);
        }

        DeltaCounter(Counter counter, double scale) {
            this.counter = counter;
            this.scale = scale;
        }

        void update(long newValue, String... labels) {
            if (newValue < 0) {
                return;
            }
            String key = String.join(" ", labels);
            Long previous = lastValues.put(key, newValue);
            long delta = previous == null ? newValue : newValue - previous;
            if (delta > 0) {
                counter.labels(labels).inc(delta * scale);
            }
        }
    }


    private static class PROptionParser {

        private final OptionSpec<String> proxyHost;
        private final OptionSpec<Integer> proxyPort;
        private final OptionSpec<Integer> metricsPort;
        private final OptionSpec<String> session;

        private final OptionParser parser;
        private OptionSet options;

        PROptionParser() {
            parser = new OptionParser();

            proxyHost = parser.acceptsAll(asList("fe-host", "m-host"))
                    .withRequiredArg()
                    .defaultsTo(DEFAULT_MON_HOST);

            proxyPort = parser.acceptsAll(asList("fe-port", "m-port"))
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .defaultsTo(DEFAULT_MON_PORT);

            metricsPort = parser.acceptsAll(asList("metrics-port"))
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .defaultsTo(DEFAULT_METRICS_PORT);

            session = parser.accepts("session").withRequiredArg();

            parser.acceptsAll(asList("h", "help")).forHelp();
        }

        public boolean parse(String[] args) {
            try {
                options = parser.parse(args);

                if (!options.has(proxyHost)) {
                    System.out.println("Using the default proxy host: " + DEFAULT_MON_HOST);
                }
                if (!options.has(proxyPort)) {
                    System.out.println("Using the default proxy port: " + DEFAULT_MON_PORT);
                }
                if (!options.has(metricsPort)) {
                    System.out.println("Using the default metrics port: " + DEFAULT_METRICS_PORT);
                }

                return true;
            } catch (OptionException e) {
                System.err.println("error: " + e.getMessage());
                return false;
            }
        }

        public String proxyHost() {
            return options.valueOf(proxyHost);
        }

        public int proxyPort() {
            return options.valueOf(proxyPort);
        }

        public int metricsPort() {
            return options.valueOf(metricsPort);
        }

        public String session() {
            return options.valueOf(session);
        }

        public boolean hasHelp() {
            return options.has("help");
        }

        public String usage() {
            return String.format("usage: j_pr [options]%n%n  Options:%n")
                    + OptUtils.optionHelp(proxyHost, "hostname", "the monitoring proxy host")
                    + OptUtils.optionHelp(proxyPort, "port", "the monitoring proxy port")
                    + OptUtils.optionHelp(metricsPort, "port",
                            "the port to serve Prometheus metrics on")
                    + OptUtils.optionHelp(session, "session",
                            "only listen to DPEs with this session");
        }
    }
}
