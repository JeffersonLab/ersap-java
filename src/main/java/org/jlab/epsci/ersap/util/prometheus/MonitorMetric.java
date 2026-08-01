/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single numeric sample extracted from a Monitor FE message.
 *
 * <p>This is the transport-independent intermediate representation used between
 * {@link MonitorMetricParser} and {@link PrometheusMetricRegistry}. It carries
 * the <em>source</em> metric name exactly as published by the Monitor FE; the
 * translation into a valid Prometheus identifier is done later by
 * {@link MetricNameSanitizer} so that name collisions can be detected and
 * reported in terms of the original names.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class MonitorMetric {

    /** Label holding the canonical DPE name, e.g. {@code 10.0.0.1%9000_java}. */
    public static final String LABEL_DPE = "dpe";

    /** Label holding the host (IP address) of the reporting DPE. */
    public static final String LABEL_HOST = "host";

    /** Label holding the DPE language: {@code java}, {@code cpp} or {@code python}. */
    public static final String LABEL_LANG = "lang";

    /** Label holding the ERSAP session the report was published under. */
    public static final String LABEL_SESSION = "session";

    /** Label holding the short container name. */
    public static final String LABEL_CONTAINER = "container";

    /** Label holding the canonical service name. */
    public static final String LABEL_SERVICE = "service";

    /** Label holding the short engine name of a service. */
    public static final String LABEL_ENGINE = "engine";

    /** Label holding the logical group a metric belongs to (dpe, container, ...). */
    public static final String LABEL_COMPONENT = "component";

    /** Label holding the Monitor FE subscription the metric arrived on. */
    public static final String LABEL_SOURCE = "source";

    /**
     * The canonical, ordered label vocabulary produced by {@link MonitorMetricParser}.
     *
     * <p>Every label used by the exporter comes from this list, which keeps the
     * label schema stable and bounded. Synthetic metrics that need to align
     * heterogeneous series (such as the last-update timestamp gauge) use this
     * list as their fixed schema.
     */
    public static final List<String> CANONICAL_LABELS = List.of(
            LABEL_COMPONENT, LABEL_SOURCE, LABEL_DPE, LABEL_HOST, LABEL_LANG,
            LABEL_SESSION, LABEL_CONTAINER, LABEL_SERVICE, LABEL_ENGINE);

    private final String source;
    private final MetricType type;
    private final String help;
    private final Map<String, String> labels;
    private final double value;
    private final double timestampSeconds;

    private MonitorMetric(Builder builder) {
        this.source = builder.source;
        this.type = builder.type;
        this.help = builder.help;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(builder.labels));
        this.value = builder.value;
        this.timestampSeconds = builder.timestampSeconds;
    }

    /**
     * Creates a builder for a metric with the given source name.
     *
     * @param source the metric name exactly as published by the Monitor FE
     * @return a new builder
     */
    public static Builder builder(String source) {
        return new Builder(source);
    }

    /**
     * Gets the metric name as published by the Monitor FE, before sanitization.
     *
     * @return the source metric name
     */
    public String source() {
        return source;
    }

    /**
     * Gets the Prometheus type inferred for this metric.
     *
     * @return the metric type
     */
    public MetricType type() {
        return type;
    }

    /**
     * Gets the {@code HELP} text to expose for this metric.
     *
     * @return the help text, never null
     */
    public String help() {
        return help;
    }

    /**
     * Gets the labels of this sample, in a stable insertion order.
     *
     * @return an unmodifiable map of label name to label value
     */
    public Map<String, String> labels() {
        return labels;
    }

    /**
     * Gets the numeric value of this sample.
     *
     * @return the metric value
     */
    public double value() {
        return value;
    }

    /**
     * Gets the source timestamp of this sample, in seconds since the epoch.
     *
     * @return the source timestamp, or {@code NaN} when the Monitor FE message
     *         carried no usable timestamp
     */
    public double timestampSeconds() {
        return timestampSeconds;
    }

    /**
     * Tells whether this sample carries a source timestamp.
     *
     * @return true if {@link #timestampSeconds()} is a real value
     */
    public boolean hasTimestamp() {
        return !Double.isNaN(timestampSeconds);
    }

    @Override
    public String toString() {
        return "MonitorMetric{" + source + labels + " = " + value + " (" + type + ")}";
    }

    /**
     * Incremental builder for {@link MonitorMetric} instances.
     */
    public static final class Builder {

        private final String source;
        private final Map<String, String> labels = new LinkedHashMap<>();
        private MetricType type = MetricType.GAUGE;
        private String help = "";
        private double value;
        private double timestampSeconds = Double.NaN;

        private Builder(String source) {
            this.source = Objects.requireNonNull(source, "source metric name");
            if (source.isEmpty()) {
                throw new IllegalArgumentException("empty source metric name");
            }
        }

        /**
         * Sets the Prometheus type of the metric.
         *
         * @param metricType the metric type
         * @return this builder
         */
        public Builder type(MetricType metricType) {
            this.type = Objects.requireNonNull(metricType, "metric type");
            return this;
        }

        /**
         * Sets the {@code HELP} text of the metric.
         *
         * @param helpText the help text
         * @return this builder
         */
        public Builder help(String helpText) {
            this.help = helpText == null ? "" : helpText;
            return this;
        }

        /**
         * Adds a label. Null or empty values are ignored, so that absent
         * metadata never creates an empty dimension.
         *
         * @param name  the label name
         * @param value the label value
         * @return this builder
         */
        public Builder label(String name, String value) {
            if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
                labels.put(name, value);
            }
            return this;
        }

        /**
         * Copies all the given labels into this builder.
         *
         * @param source the labels to copy
         * @return this builder
         */
        public Builder labels(Map<String, String> source) {
            if (source != null) {
                source.forEach(this::label);
            }
            return this;
        }

        /**
         * Sets the numeric value of the sample.
         *
         * @param metricValue the value
         * @return this builder
         */
        public Builder value(double metricValue) {
            this.value = metricValue;
            return this;
        }

        /**
         * Sets the source timestamp of the sample.
         *
         * @param seconds seconds since the epoch, or {@code NaN} if unknown
         * @return this builder
         */
        public Builder timestampSeconds(double seconds) {
            this.timestampSeconds = seconds;
            return this;
        }

        /**
         * Builds the immutable metric.
         *
         * @return the new metric
         */
        public MonitorMetric build() {
            return new MonitorMetric(this);
        }
    }
}
