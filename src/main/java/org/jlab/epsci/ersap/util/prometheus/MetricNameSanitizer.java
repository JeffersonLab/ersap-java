/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import java.util.Objects;

/**
 * Deterministic translation of Monitor FE metric names into valid Prometheus
 * identifiers.
 *
 * <p>Prometheus metric names must match {@code [a-zA-Z_:][a-zA-Z0-9_:]*}. ERSAP
 * user metrics are free-form strings chosen by engine authors, so they can hold
 * spaces, dots, slashes, dashes and any other character. The sanitizer applies
 * the following rules, in order:
 *
 * <ol>
 *   <li>lower-case the whole name;</li>
 *   <li>replace every character outside {@code [a-z0-9_]} with {@code _};</li>
 *   <li>collapse runs of {@code _} into a single {@code _};</li>
 *   <li>strip leading and trailing {@code _};</li>
 *   <li>fall back to {@code unnamed} if nothing is left;</li>
 *   <li>prepend the configured prefix;</li>
 *   <li>prepend {@code _} if the result would still start with a digit.</li>
 * </ol>
 *
 * <p>The colon is <em>not</em> preserved. Prometheus reserves {@code :} for
 * names produced by recording rules, and ERSAP canonical names use {@code :} as
 * a component separator; keeping it would produce metric names that look like
 * recording rules. Canonical names are exposed as labels instead.
 *
 * <p>Examples, with the default {@code ersap_} prefix:
 * <pre>
 *   Event Rate        -&gt; ersap_event_rate
 *   input.bytes/sec   -&gt; ersap_input_bytes_sec
 *   7_second_average  -&gt; ersap_7_second_average
 * </pre>
 *
 * <p>The transformation is not injective: distinct source names can map onto the
 * same Prometheus name. {@link PrometheusMetricRegistry} detects that case and
 * logs a warning.
 *
 * <p>Instances are immutable and thread safe.
 */
public final class MetricNameSanitizer {

    /** The metric name prefix used when none is configured. */
    public static final String DEFAULT_PREFIX = "ersap";

    /** The name used when a source name sanitizes to the empty string. */
    static final String FALLBACK_NAME = "unnamed";

    private final String prefix;

    /**
     * Creates a sanitizer with the given prefix.
     *
     * @param prefix the metric name prefix; it is itself sanitized and a single
     *               trailing {@code _} is enforced. An empty or null prefix
     *               disables prefixing.
     */
    public MetricNameSanitizer(String prefix) {
        this.prefix = normalizePrefix(prefix);
    }

    /**
     * Gets the normalized prefix, including the trailing underscore.
     *
     * @return the prefix, possibly the empty string
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Sanitizes a Monitor FE metric name and applies the configured prefix.
     *
     * @param sourceName the metric name as published by the Monitor FE
     * @return a valid Prometheus metric name
     */
    public String sanitize(String sourceName) {
        String bare = sanitizeBare(sourceName);
        String full = prefix + bare;
        if (startsWithDigit(full)) {
            return "_" + full;
        }
        return full;
    }

    /**
     * Sanitizes a name without applying any prefix.
     *
     * @param name the name to sanitize
     * @return the sanitized name, never empty
     */
    public static String sanitizeBare(String name) {
        if (name == null) {
            return FALLBACK_NAME;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean lastUnderscore = true;   // true, so leading separators are dropped
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (valid) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        // drop a trailing separator
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() == 0) {
            return FALLBACK_NAME;
        }
        return sb.toString();
    }

    /**
     * Sanitizes a Prometheus label name.
     *
     * <p>Label names must match {@code [a-zA-Z_][a-zA-Z0-9_]*}, and names
     * starting with {@code __} are reserved for internal Prometheus use, so a
     * leading digit gets a single {@code _} prepended and reserved prefixes are
     * collapsed.
     *
     * @param name the label name to sanitize
     * @return a valid Prometheus label name
     */
    public static String sanitizeLabelName(String name) {
        String bare = sanitizeBare(name);
        if (startsWithDigit(bare)) {
            return "_" + bare;
        }
        return bare;
    }

    private static boolean startsWithDigit(String value) {
        return !value.isEmpty() && value.charAt(0) >= '0' && value.charAt(0) <= '9';
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "";
        }
        String bare = sanitizeBare(prefix);
        if (FALLBACK_NAME.equals(bare) && !prefix.contains(FALLBACK_NAME)) {
            // the prefix was made only of separators: treat it as "no prefix"
            return "";
        }
        return bare + "_";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MetricNameSanitizer)) {
            return false;
        }
        return prefix.equals(((MetricNameSanitizer) obj).prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix);
    }

    @Override
    public String toString() {
        return "MetricNameSanitizer{prefix=" + prefix + "}";
    }
}
