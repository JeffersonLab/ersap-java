/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import io.prometheus.client.Collector;
import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe, dynamically populated Prometheus collector for Monitor FE metrics.
 *
 * <p>The Monitor FE publishes user metrics whose names are chosen by engine
 * authors, so the full set of metrics is unknown until messages start arriving.
 * This collector registers a metric family the first time it is seen and then
 * reuses it, keyed by the sanitized Prometheus name. A single
 * {@link Collector} instance is registered with the
 * {@link io.prometheus.client.CollectorRegistry}; individual families are just
 * entries in a concurrent map, so no new collector object is created per update.
 *
 * <p>Safety properties:
 * <ul>
 *   <li><b>Name collisions</b> — two different source names can sanitize to the
 *       same Prometheus name. The first source name to arrive owns the family;
 *       later ones share it and a warning is logged once per colliding pair.</li>
 *   <li><b>Label schemas</b> — the label names of a family are fixed at
 *       registration. Later samples are normalized onto that schema: missing
 *       labels become the empty string and unexpected labels are dropped with a
 *       one-time warning. This guarantees every series of a family has the same
 *       label set, as Prometheus requires.</li>
 *   <li><b>Type conflicts</b> — the type recorded at registration wins; a
 *       conflicting later type is logged once and ignored.</li>
 *   <li><b>Unbounded growth</b> — the total number of series is capped. Once the
 *       cap is reached new series are dropped (existing ones keep updating) and
 *       the drop is reported through {@link UpdateResult#SKIPPED_LIMIT}.</li>
 *   <li><b>Reserved names</b> — anything under the exporter's own
 *       {@code <prefix>prometheus_exporter_} namespace is rejected so that
 *       payload metrics can never shadow exporter self-metrics.</li>
 * </ul>
 */
public final class PrometheusMetricRegistry extends Collector {

    /** Default cap on the total number of exported time series. */
    public static final int DEFAULT_MAX_SERIES = 20_000;

    /** Suffix of the synthetic gauge holding per-metric source timestamps. */
    static final String TIMESTAMP_METRIC = "metric_last_update_timestamp_seconds";

    private static final Logger LOGGER =
            new LoggerFactory().getLogger(PrometheusMetricRegistry.class.getSimpleName());

    private final MetricNameSanitizer sanitizer;
    private final MetricFilter filter;
    private final Map<String, String> staticLabels;
    private final List<String> staticLabelNames;
    private final List<String> staticLabelValues;
    private final boolean exportTimestamps;
    private final int maxSeries;
    private final String reservedPrefix;

    private final ConcurrentHashMap<String, Family> families = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<List<String>, Double> timestamps = new ConcurrentHashMap<>();
    private final List<String> timestampLabelNames;
    private final Set<String> warnings = ConcurrentHashMap.newKeySet();
    private final AtomicInteger series = new AtomicInteger();
    private final AtomicLong collisions = new AtomicLong();
    private final AtomicLong droppedSeries = new AtomicLong();

    private PrometheusMetricRegistry(Builder builder) {
        this.sanitizer = builder.sanitizer;
        this.filter = builder.filter;
        this.exportTimestamps = builder.exportTimestamps;
        this.maxSeries = builder.maxSeries;
        this.reservedPrefix = sanitizer.prefix() + ExporterSelfMetrics.NAMESPACE + "_";

        Map<String, String> sanitized = new LinkedHashMap<>();
        builder.staticLabels.forEach((name, value) -> {
            if (name != null && value != null && !name.trim().isEmpty()) {
                sanitized.put(MetricNameSanitizer.sanitizeLabelName(name), value);
            }
        });
        this.staticLabels = Collections.unmodifiableMap(sanitized);
        this.staticLabelNames = List.copyOf(sanitized.keySet());
        this.staticLabelValues = List.copyOf(sanitized.values());

        List<String> tsLabels = new ArrayList<>();
        tsLabels.add("metric");
        tsLabels.addAll(MonitorMetric.CANONICAL_LABELS);
        tsLabels.addAll(staticLabelNames);
        this.timestampLabelNames = List.copyOf(tsLabels);
    }

    /**
     * Creates a builder for a registry using the given sanitizer.
     *
     * @param sanitizer the metric name sanitizer
     * @return a new builder
     */
    public static Builder builder(MetricNameSanitizer sanitizer) {
        return new Builder(sanitizer);
    }

    /**
     * Records a metric sample, registering its family on first sight.
     *
     * @param metric the metric to record
     * @return the outcome of the update
     */
    public UpdateResult update(MonitorMetric metric) {
        if (metric == null) {
            return UpdateResult.SKIPPED_INVALID;
        }
        double value = metric.value();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return UpdateResult.SKIPPED_INVALID;
        }

        String name = sanitizer.sanitize(metric.source());
        if (name.startsWith(reservedPrefix)) {
            warnOnce("reserved:" + name,
                    "metric \"{}\" maps to the reserved exporter namespace \"{}\" and is dropped",
                    metric.source(), reservedPrefix);
            return UpdateResult.SKIPPED_RESERVED;
        }
        if (!filter.test(name)) {
            return UpdateResult.SKIPPED_FILTERED;
        }

        boolean[] created = new boolean[1];
        Family family = families.computeIfAbsent(name, key -> {
            created[0] = true;
            return new Family(key, metric);
        });

        if (created[0]) {
            LOGGER.info("registered new metric \"{}\" from Monitor FE metric \"{}\" ({})",
                    name, metric.source(), metric.type());
        } else {
            checkConsistency(family, metric, name);
        }

        List<String> labelValues = family.alignLabels(metric, name);
        UpdateResult result = family.record(labelValues, value);
        if (result == UpdateResult.SKIPPED_LIMIT) {
            warnOnce("limit",
                    "series limit of {} reached; new time series are dropped "
                            + "(raise it with --max-series)", maxSeries);
            return result;
        }

        if (exportTimestamps && metric.hasTimestamp()) {
            recordTimestamp(name, metric);
        }
        return result;
    }

    /**
     * Gets the number of registered metric families.
     *
     * @return the number of distinct Prometheus metric names
     */
    public int metricCount() {
        return families.size();
    }

    /**
     * Gets the number of exported time series.
     *
     * @return the number of distinct label combinations across all families
     */
    public int seriesCount() {
        return series.get();
    }

    /**
     * Gets the number of detected sanitization collisions.
     *
     * @return the number of times two source names mapped onto one metric name
     */
    public long collisionCount() {
        return collisions.get();
    }

    /**
     * Gets the number of series dropped because of the series cap.
     *
     * @return the number of dropped series
     */
    public long droppedSeriesCount() {
        return droppedSeries.get();
    }

    /**
     * Gets the sanitizer used by this registry.
     *
     * @return the metric name sanitizer
     */
    public MetricNameSanitizer sanitizer() {
        return sanitizer;
    }

    /**
     * Gets the immutable static labels added to every exported series.
     *
     * @return the static labels
     */
    public Map<String, String> staticLabels() {
        return staticLabels;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> out = new ArrayList<>(families.size() + 1);
        for (Family family : families.values()) {
            List<MetricFamilySamples.Sample> samples = new ArrayList<>(family.values.size());
            family.values.forEach((labels, value) -> samples.add(
                    new MetricFamilySamples.Sample(family.name, family.labelNames, labels, value)));
            if (!samples.isEmpty()) {
                out.add(new MetricFamilySamples(family.name, family.type(), family.help, samples));
            }
        }
        if (exportTimestamps && !timestamps.isEmpty()) {
            String name = sanitizer.prefix() + TIMESTAMP_METRIC;
            List<MetricFamilySamples.Sample> samples = new ArrayList<>(timestamps.size());
            timestamps.forEach((labels, value) -> samples.add(
                    new MetricFamilySamples.Sample(name, timestampLabelNames, labels, value)));
            out.add(new MetricFamilySamples(name, Type.GAUGE,
                    "Source timestamp of the last Monitor FE update of each metric, "
                            + "in seconds since the epoch", samples));
        }
        return out;
    }

    private void recordTimestamp(String name, MonitorMetric metric) {
        List<String> labels = new ArrayList<>(timestampLabelNames.size());
        labels.add(name);
        for (String label : MonitorMetric.CANONICAL_LABELS) {
            labels.add(metric.labels().getOrDefault(label, ""));
        }
        labels.addAll(staticLabelValues);
        List<String> key = List.copyOf(labels);
        if (!timestamps.containsKey(key)) {
            if (!reserveSeries()) {
                droppedSeries.incrementAndGet();
                return;
            }
            if (timestamps.putIfAbsent(key, metric.timestampSeconds()) == null) {
                return;
            }
            series.decrementAndGet();
        }
        timestamps.put(key, metric.timestampSeconds());
    }

    private boolean reserveSeries() {
        while (true) {
            int current = series.get();
            if (current >= maxSeries) {
                return false;
            }
            if (series.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void checkConsistency(Family family, MonitorMetric metric, String name) {
        if (!family.source.equals(metric.source())) {
            if (warnings.add("collision:" + name + ":" + metric.source())) {
                collisions.incrementAndGet();
                LOGGER.warn("Monitor FE metrics \"{}\" and \"{}\" both map to the Prometheus "
                                + "name \"{}\"; they will share one metric family",
                        family.source, metric.source(), name);
            }
        }
        if (family.metricType != metric.type()) {
            warnOnce("type:" + name,
                    "metric \"{}\" was registered as {} but arrived as {}; keeping {}",
                    name, family.metricType, metric.type(), family.metricType);
        }
    }

    private void warnOnce(String key, String format, Object... args) {
        if (warnings.add(key)) {
            LOGGER.warn(format, args);
        }
    }

    void warnLabelMismatch(String name, String label) {
        warnOnce("label:" + name + ":" + label,
                "metric \"{}\" got unexpected label \"{}\"; it is dropped to keep a stable "
                        + "label schema", name, label);
    }

    /**
     * A registered metric family: a Prometheus name, its fixed type, help text
     * and label schema, plus one value per label combination.
     */
    private final class Family {

        private final String name;
        private final String source;
        private final MetricType metricType;
        private final String help;
        private final List<String> labelNames;
        private final ConcurrentHashMap<List<String>, Double> values = new ConcurrentHashMap<>();

        Family(String name, MonitorMetric first) {
            this.name = name;
            this.source = first.source();
            this.metricType = first.type();
            this.help = first.help().isEmpty()
                    ? "ERSAP Monitor FE metric \"" + first.source() + "\""
                    : first.help();
            List<String> names = new ArrayList<>(first.labels().keySet());
            names.addAll(staticLabelNames);
            this.labelNames = List.copyOf(names);
        }

        Type type() {
            return metricType == MetricType.COUNTER ? Type.COUNTER : Type.GAUGE;
        }

        /**
         * Normalizes the labels of an incoming sample onto this family's fixed
         * schema: known labels keep their value, missing ones become the empty
         * string, and unexpected ones are dropped with a one-time warning.
         */
        List<String> alignLabels(MonitorMetric metric, String metricName) {
            Map<String, String> labels = metric.labels();
            List<String> aligned = new ArrayList<>(labelNames.size());
            for (String labelName : labelNames) {
                String value = labels.get(labelName);
                if (value == null) {
                    value = staticLabels.get(labelName);
                }
                aligned.add(value == null ? "" : value);
            }
            if (!labelNames.containsAll(labels.keySet())) {
                for (String labelName : labels.keySet()) {
                    if (!labelNames.contains(labelName)) {
                        warnLabelMismatch(metricName, labelName);
                    }
                }
            }
            return List.copyOf(aligned);
        }

        /**
         * Stores a value, reserving a slot in the global series budget when the
         * label combination is seen for the first time.
         */
        UpdateResult record(List<String> labelValues, double value) {
            if (values.containsKey(labelValues)) {
                values.put(labelValues, value);
                return UpdateResult.UPDATED;
            }
            if (!reserveSeries()) {
                droppedSeries.incrementAndGet();
                return UpdateResult.SKIPPED_LIMIT;
            }
            if (values.putIfAbsent(labelValues, value) == null) {
                return UpdateResult.REGISTERED;
            }
            // another thread created the same series first: return the reservation
            series.decrementAndGet();
            values.put(labelValues, value);
            return UpdateResult.UPDATED;
        }
    }

    /**
     * The outcome of a single {@link PrometheusMetricRegistry#update} call.
     */
    public enum UpdateResult {

        /** A new metric family or a new time series was created. */
        REGISTERED,

        /** An existing time series was updated. */
        UPDATED,

        /** The metric was dropped by the include/exclude filters. */
        SKIPPED_FILTERED,

        /** The metric name fell into the reserved exporter namespace. */
        SKIPPED_RESERVED,

        /** The series cap was reached and the new series was dropped. */
        SKIPPED_LIMIT,

        /** The metric carried no usable numeric value. */
        SKIPPED_INVALID;

        /**
         * Tells whether the update was applied to the registry.
         *
         * @return true if the sample is now exported
         */
        public boolean isApplied() {
            return this == REGISTERED || this == UPDATED;
        }
    }

    /**
     * Builder for {@link PrometheusMetricRegistry}.
     */
    public static final class Builder {

        private final MetricNameSanitizer sanitizer;
        private MetricFilter filter = MetricFilter.acceptAll();
        private Map<String, String> staticLabels = Collections.emptyMap();
        private boolean exportTimestamps;
        private int maxSeries = DEFAULT_MAX_SERIES;

        private Builder(MetricNameSanitizer sanitizer) {
            if (sanitizer == null) {
                throw new IllegalArgumentException("null metric name sanitizer");
            }
            this.sanitizer = sanitizer;
        }

        /**
         * Sets the include/exclude filter.
         *
         * @param metricFilter the filter, null means accept all
         * @return this builder
         */
        public Builder filter(MetricFilter metricFilter) {
            this.filter = metricFilter == null ? MetricFilter.acceptAll() : metricFilter;
            return this;
        }

        /**
         * Sets labels added to every exported series.
         *
         * @param labels the static labels
         * @return this builder
         */
        public Builder staticLabels(Map<String, String> labels) {
            this.staticLabels = labels == null ? Collections.emptyMap() : labels;
            return this;
        }

        /**
         * Enables the synthetic per-metric source timestamp gauge.
         *
         * @param enabled true to export {@code <prefix>metric_last_update_timestamp_seconds}
         * @return this builder
         */
        public Builder exportTimestamps(boolean enabled) {
            this.exportTimestamps = enabled;
            return this;
        }

        /**
         * Sets the cap on the total number of exported time series.
         *
         * @param limit the maximum number of series
         * @return this builder
         */
        public Builder maxSeries(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("max series must be positive");
            }
            this.maxSeries = limit;
            return this;
        }

        /**
         * Builds the registry.
         *
         * @return the new registry
         */
        public PrometheusMetricRegistry build() {
            return new PrometheusMetricRegistry(this);
        }
    }
}
