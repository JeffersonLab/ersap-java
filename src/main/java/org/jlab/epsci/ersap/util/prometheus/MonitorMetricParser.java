/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.ContainerRuntimeData;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.ServiceName;
import org.jlab.epsci.ersap.base.ServiceRuntimeData;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Converts Monitor FE messages into {@link MonitorMetric} samples.
 *
 * <p>Two message families are understood, matching exactly the two
 * subscriptions used by {@code org.jlab.epsci.ersap.examples.TestMonitor}:
 *
 * <ul>
 *   <li><b>{@code dpeReport}</b> — a periodic JSON document already decoded by
 *       the ERSAP base library into {@link DpeRegistrationData} and
 *       {@link DpeRuntimeData}. It carries DPE-wide values (CPU, memory, load,
 *       cores), one entry per container, and one entry per service with the
 *       cumulative request, failure, execution-time, shared-memory and network
 *       counters.</li>
 *   <li><b>{@code userMetrics}</b> — an arbitrary flat JSON object published by
 *       an engine through {@code EngineMetricsPublisher}, decoded by
 *       {@code MonitorOrchestrator} into a {@code Map<String, Object>}. Keys and
 *       value types are chosen by the engine author and are unknown until the
 *       first message arrives.</li>
 * </ul>
 *
 * <p>Metric names produced here are still <em>source</em> names; sanitization and
 * prefixing happen in {@link PrometheusMetricRegistry}.
 *
 * <p>Instances are immutable and thread safe.
 */
public final class MonitorMetricParser {

    /**
     * Default heuristic used to promote a user metric to a Prometheus counter.
     *
     * <p>Matched, case-insensitively, against the sanitized user metric name.
     * Anything that does not match stays a gauge, which is the safe default.
     */
    public static final String DEFAULT_COUNTER_PATTERN = "^total_.*|.*_(total|count|counter)$";

    /** Value of the {@code source} label for {@code dpeReport} messages. */
    public static final String SOURCE_DPE_REPORT = "dpe_report";

    /** Value of the {@code source} label for {@code userMetrics} messages. */
    public static final String SOURCE_USER_METRICS = "user_metrics";

    /** Maximum nesting depth flattened out of a user metrics JSON object. */
    private static final int MAX_NESTING_DEPTH = 4;

    private static final double MICROSECONDS_PER_SECOND = 1_000_000.0;

    private final Pattern counterPattern;
    private final ZoneId zone;

    /**
     * Creates a parser with the default counter heuristic and the system time zone.
     */
    public MonitorMetricParser() {
        this(Pattern.compile(DEFAULT_COUNTER_PATTERN, Pattern.CASE_INSENSITIVE),
             ZoneId.systemDefault());
    }

    /**
     * Creates a parser with a custom counter heuristic.
     *
     * @param counterPattern pattern matched against the sanitized user metric
     *                       name to infer a counter; null disables the
     *                       heuristic and every user metric becomes a gauge
     * @param zone           the time zone used to interpret the local timestamps
     *                       carried by DPE reports
     */
    public MonitorMetricParser(Pattern counterPattern, ZoneId zone) {
        this.counterPattern = counterPattern;
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
    }

    /**
     * Converts a {@code dpeReport} message into metric samples.
     *
     * @param registration the DPE registration report
     * @param runtime      the DPE runtime report
     * @return the parse result, never null
     */
    public ParseResult parseDpeReport(DpeRegistrationData registration,
                                      DpeRuntimeData runtime) {
        List<MonitorMetric> metrics = new ArrayList<>();
        int rejected = 0;
        if (runtime == null) {
            return new ParseResult(metrics, 1);
        }

        DpeName dpe = runtime.name();
        String session = registration == null ? null : registration.session();
        Map<String, String> dpeLabels = new LinkedHashMap<>();
        dpeLabels.put(MonitorMetric.LABEL_COMPONENT, "dpe");
        dpeLabels.put(MonitorMetric.LABEL_SOURCE, SOURCE_DPE_REPORT);
        putDpeLabels(dpeLabels, dpe);
        if (session != null && !session.isEmpty()) {
            dpeLabels.put(MonitorMetric.LABEL_SESSION, session);
        }

        double snapshot = toEpochSeconds(runtime.snapshotTime());

        rejected += addDpeMetric(metrics, dpeLabels, snapshot, "dpe.cpu_usage_percent",
                MetricType.GAUGE, "DPE process CPU usage, in percent",
                runtime.cpuUsage());
        rejected += addDpeMetric(metrics, dpeLabels, snapshot, "dpe.memory_usage_bytes",
                MetricType.GAUGE, "Memory in use by the DPE process, in bytes",
                runtime.memoryUsage());
        // ERSAP reports a negative system load when the value is unavailable
        if (runtime.systemLoad() >= 0) {
            rejected += addDpeMetric(metrics, dpeLabels, snapshot, "dpe.system_load",
                    MetricType.GAUGE, "System load average of the DPE node",
                    runtime.systemLoad());
        }
        if (snapshot > 0) {
            metrics.add(build("dpe.snapshot_time_seconds", MetricType.GAUGE,
                    "Time the DPE runtime snapshot was taken, in seconds since the epoch",
                    dpeLabels, snapshot, snapshot));
        }

        if (registration != null) {
            rejected += addDpeMetric(metrics, dpeLabels, snapshot, "dpe.cores",
                    MetricType.GAUGE, "Number of cores assigned to the DPE",
                    registration.numCores());
            rejected += addDpeMetric(metrics, dpeLabels, snapshot, "dpe.memory_size_bytes",
                    MetricType.GAUGE, "Maximum memory available to the DPE, in bytes",
                    registration.memorySize());
            double startTime = toEpochSeconds(registration.startTime());
            if (startTime > 0) {
                metrics.add(build("dpe.start_time_seconds", MetricType.GAUGE,
                        "Start time of the DPE, in seconds since the epoch",
                        dpeLabels, startTime, snapshot));
            }
        }

        for (ContainerRuntimeData container : runtime.containers()) {
            Map<String, String> containerLabels = new LinkedHashMap<>(dpeLabels);
            containerLabels.put(MonitorMetric.LABEL_COMPONENT, "container");
            containerLabels.put(MonitorMetric.LABEL_CONTAINER, container.name().name());
            double containerSnapshot = orElse(toEpochSeconds(container.snapshotTime()), snapshot);

            metrics.add(build("container.requests_total", MetricType.COUNTER,
                    "Requests received by all the services of the container",
                    containerLabels, container.numRequests(), containerSnapshot));

            for (ServiceRuntimeData service : container.services()) {
                metrics.addAll(parseService(service, containerLabels, containerSnapshot));
            }
        }

        return new ParseResult(metrics, rejected);
    }

    /**
     * Converts a {@code userMetrics} message into metric samples.
     *
     * <p>The engine identifier is the canonical service name published by
     * {@code ServiceEngine}, for example
     * {@code 10.0.0.2%7771_java:username:EventRateMonitor}. It is split into
     * {@code dpe}, {@code host}, {@code lang}, {@code container}, {@code service}
     * and {@code engine} labels when it parses as a canonical name, and kept
     * whole in the {@code service} label otherwise.
     *
     * @param session the ERSAP session
     * @param engine  the canonical engine (service) name
     * @param metrics the key-value pairs published by the engine
     * @return the parse result, never null
     */
    public ParseResult parseUserMetrics(String session, String engine,
                                        Map<String, Object> metrics) {
        List<MonitorMetric> result = new ArrayList<>();
        if (metrics == null || metrics.isEmpty()) {
            return new ParseResult(result, 0);
        }
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(MonitorMetric.LABEL_COMPONENT, "engine");
        labels.put(MonitorMetric.LABEL_SOURCE, SOURCE_USER_METRICS);
        putServiceLabels(labels, engine);
        if (session != null && !session.isEmpty()) {
            labels.put(MonitorMetric.LABEL_SESSION, session);
        }
        int rejected = flatten("", metrics, labels, result, 0);
        return new ParseResult(result, rejected);
    }

    /**
     * Converts an arbitrary Monitor FE value into a double.
     *
     * <p>Numbers are taken as-is, booleans map to {@code 1}/{@code 0}, and
     * strings are accepted when they parse as a number or as a boolean literal
     * ({@code true}/{@code false}, {@code yes}/{@code no}, {@code on}/{@code off}).
     * Everything else — free-form text, nulls, arrays — is rejected: exporting it
     * would either be meaningless or create unbounded label cardinality.
     *
     * @param value the raw value
     * @return the numeric value, or an empty optional when not convertible
     */
    public static OptionalDouble toDouble(Object value) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        if (value instanceof Number) {
            return finite(((Number) value).doubleValue());
        }
        if (value instanceof Boolean) {
            return OptionalDouble.of((Boolean) value ? 1 : 0);
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return OptionalDouble.empty();
            }
            switch (text.toLowerCase()) {
                case "true": case "yes": case "on":
                    return OptionalDouble.of(1);
                case "false": case "no": case "off":
                    return OptionalDouble.of(0);
                default:
                    break;
            }
            try {
                return finite(Double.parseDouble(text));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    private List<MonitorMetric> parseService(ServiceRuntimeData service,
                                             Map<String, String> containerLabels,
                                             double containerSnapshot) {
        Map<String, String> labels = new LinkedHashMap<>(containerLabels);
        labels.put(MonitorMetric.LABEL_COMPONENT, "service");
        ServiceName name = service.name();
        labels.put(MonitorMetric.LABEL_SERVICE, name.canonicalName());
        labels.put(MonitorMetric.LABEL_ENGINE, name.name());
        double snapshot = orElse(toEpochSeconds(service.snapshotTime()), containerSnapshot);

        List<MonitorMetric> metrics = new ArrayList<>(7);
        metrics.add(build("service.requests_total", MetricType.COUNTER,
                "Requests received by the service since it was deployed",
                labels, service.numRequests(), snapshot));
        metrics.add(build("service.failures_total", MetricType.COUNTER,
                "Requests that returned an error since the service was deployed",
                labels, service.numFailures(), snapshot));
        metrics.add(build("service.execution_time_seconds_total", MetricType.COUNTER,
                "Accumulated execution time of the service, in seconds",
                labels, service.executionTime() / MICROSECONDS_PER_SECOND, snapshot));
        metrics.add(build("service.shared_memory_reads_total", MetricType.COUNTER,
                "Requests received by the service through the DPE shared memory",
                labels, service.sharedMemoryReads(), snapshot));
        metrics.add(build("service.shared_memory_writes_total", MetricType.COUNTER,
                "Requests sent by the service through the DPE shared memory",
                labels, service.sharedMemoryWrites(), snapshot));
        metrics.add(build("service.bytes_received_total", MetricType.COUNTER,
                "Bytes received by the service through the network",
                labels, service.bytesReceived(), snapshot));
        metrics.add(build("service.bytes_sent_total", MetricType.COUNTER,
                "Bytes sent by the service through the network",
                labels, service.bytesSent(), snapshot));
        return metrics;
    }

    private int flatten(String prefix, Map<?, ?> values, Map<String, String> labels,
                        List<MonitorMetric> out, int depth) {
        int rejected = 0;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                rejected++;
                continue;
            }
            String key = prefix.isEmpty()
                    ? entry.getKey().toString()
                    : prefix + "_" + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                if (depth >= MAX_NESTING_DEPTH) {
                    rejected++;
                } else {
                    rejected += flatten(key, (Map<?, ?>) value, labels, out, depth + 1);
                }
                continue;
            }
            OptionalDouble numeric = toDouble(value);
            if (numeric.isEmpty()) {
                rejected++;
                continue;
            }
            out.add(build("user." + key, inferUserType(key),
                    "ERSAP user engine metric \"" + key + "\"",
                    labels, numeric.getAsDouble(), Double.NaN));
        }
        return rejected;
    }

    private MetricType inferUserType(String key) {
        if (counterPattern == null) {
            return MetricType.GAUGE;
        }
        String sanitized = MetricNameSanitizer.sanitizeBare(key);
        return counterPattern.matcher(sanitized).matches() ? MetricType.COUNTER : MetricType.GAUGE;
    }

    private int addDpeMetric(List<MonitorMetric> out, Map<String, String> labels,
                             double timestamp, String name, MetricType type,
                             String help, double value) {
        OptionalDouble numeric = finite(value);
        if (numeric.isEmpty()) {
            return 1;
        }
        out.add(build(name, type, help, labels, numeric.getAsDouble(), timestamp));
        return 0;
    }

    private MonitorMetric build(String name, MetricType type, String help,
                                Map<String, String> labels, double value, double timestamp) {
        return MonitorMetric.builder(name)
                .type(type)
                .help(help)
                .labels(labels)
                .value(value)
                .timestampSeconds(timestamp > 0 ? timestamp : Double.NaN)
                .build();
    }

    private static void putDpeLabels(Map<String, String> labels, DpeName dpe) {
        if (dpe == null) {
            return;
        }
        labels.put(MonitorMetric.LABEL_DPE, dpe.canonicalName());
        if (dpe.address() != null) {
            labels.put(MonitorMetric.LABEL_HOST, dpe.address().host());
        }
        if (dpe.language() != null) {
            labels.put(MonitorMetric.LABEL_LANG, dpe.language().toString());
        }
    }

    private static void putServiceLabels(Map<String, String> labels, String canonicalName) {
        if (canonicalName == null || canonicalName.isEmpty()) {
            return;
        }
        labels.put(MonitorMetric.LABEL_SERVICE, canonicalName);
        try {
            ServiceName service = new ServiceName(canonicalName);
            putDpeLabels(labels, service.dpe());
            labels.put(MonitorMetric.LABEL_CONTAINER, service.container().name());
            labels.put(MonitorMetric.LABEL_ENGINE, service.name());
        } catch (RuntimeException e) {
            // not a canonical ERSAP name: keep the raw value in the service label
            labels.put(MonitorMetric.LABEL_ENGINE, canonicalName);
        }
    }

    private double toEpochSeconds(LocalDateTime time) {
        if (time == null) {
            return Double.NaN;
        }
        try {
            return time.atZone(zone).toEpochSecond();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static double orElse(double value, double fallback) {
        return Double.isNaN(value) ? fallback : value;
    }

    /**
     * The outcome of parsing one Monitor FE message.
     */
    public static final class ParseResult {

        private final List<MonitorMetric> metrics;
        private final int rejected;

        ParseResult(List<MonitorMetric> metrics, int rejected) {
            this.metrics = Collections.unmodifiableList(metrics);
            this.rejected = rejected;
        }

        /**
         * Gets the metrics extracted from the message.
         *
         * @return an unmodifiable list of metrics, possibly empty
         */
        public List<MonitorMetric> metrics() {
            return metrics;
        }

        /**
         * Gets the number of values that could not be turned into a metric,
         * such as free-form strings, arrays or missing numbers.
         *
         * @return the number of rejected values
         */
        public int rejected() {
            return rejected;
        }
    }
}
