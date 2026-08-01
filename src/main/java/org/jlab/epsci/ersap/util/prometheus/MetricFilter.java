/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Include/exclude filtering of exported metrics.
 *
 * <p>Filters are matched against the <em>sanitized</em> Prometheus metric name,
 * including the configured prefix (for example {@code ersap_service_requests_total}),
 * so what you write in a filter is exactly what you see in {@code /metrics}.
 *
 * <p>Two pattern syntaxes are supported:
 * <ul>
 *   <li><b>glob</b> (the default): {@code *} matches any run of characters and
 *       {@code ?} matches a single character. Everything else is literal.
 *       Example: {@code ersap_service_*}</li>
 *   <li><b>regular expression</b>: any pattern wrapped in slashes is compiled as
 *       a {@link Pattern}. Example: {@code /^ersap_(dpe|service)_.*$/}</li>
 * </ul>
 *
 * <p>Both syntaxes must match the <em>whole</em> name.
 *
 * <p>Evaluation order:
 * <ol>
 *   <li>if any include pattern is configured, the name must match at least one
 *       of them, otherwise it is dropped;</li>
 *   <li>if the name matches any exclude pattern it is dropped.</li>
 * </ol>
 * Exclude always wins over include.
 *
 * <p>Exporter self-metrics are never passed through a filter: the exporter must
 * remain observable regardless of how aggressively the payload is filtered.
 *
 * <p>Instances are immutable and thread safe.
 */
public final class MetricFilter {

    private static final MetricFilter ACCEPT_ALL =
            new MetricFilter(Collections.emptyList(), Collections.emptyList());

    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    private MetricFilter(List<Pattern> includes, List<Pattern> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * A filter that accepts every metric.
     *
     * @return the shared accept-all filter
     */
    public static MetricFilter acceptAll() {
        return ACCEPT_ALL;
    }

    /**
     * Builds a filter from the given pattern lists.
     *
     * @param includePatterns include patterns, may be null or empty
     * @param excludePatterns exclude patterns, may be null or empty
     * @return the filter
     * @throws IllegalArgumentException if a pattern cannot be compiled
     */
    public static MetricFilter of(Collection<String> includePatterns,
                                  Collection<String> excludePatterns) {
        List<Pattern> in = compileAll(includePatterns);
        List<Pattern> ex = compileAll(excludePatterns);
        if (in.isEmpty() && ex.isEmpty()) {
            return ACCEPT_ALL;
        }
        return new MetricFilter(in, ex);
    }

    /**
     * Tells whether a metric name should be exported.
     *
     * @param metricName the sanitized Prometheus metric name
     * @return true if the metric passes the filter
     */
    public boolean test(String metricName) {
        if (metricName == null) {
            return false;
        }
        if (!includes.isEmpty() && !matchesAny(includes, metricName)) {
            return false;
        }
        return !matchesAny(excludes, metricName);
    }

    /**
     * Tells whether this filter drops nothing.
     *
     * @return true if no pattern is configured
     */
    public boolean isAcceptAll() {
        return includes.isEmpty() && excludes.isEmpty();
    }

    /**
     * Compiles a single filter pattern.
     *
     * <p>Patterns of the form {@code /.../} are treated as regular expressions,
     * anything else as a glob.
     *
     * @param pattern the pattern to compile
     * @return the compiled pattern, anchored to the whole name
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static Pattern compile(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("empty metric filter pattern");
        }
        String trimmed = pattern.trim();
        try {
            if (trimmed.length() > 1 && trimmed.startsWith("/") && trimmed.endsWith("/")) {
                return Pattern.compile(trimmed.substring(1, trimmed.length() - 1));
            }
            return Pattern.compile(globToRegex(trimmed));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "invalid metric filter pattern \"" + pattern + "\": " + e.getMessage(), e);
        }
    }

    private static boolean matchesAny(List<Pattern> patterns, String name) {
        for (Pattern p : patterns) {
            if (p.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compileAll(Collection<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                compiled.add(compile(pattern));
            }
        }
        return Collections.unmodifiableList(compiled);
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() * 2);
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    sb.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                sb.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            sb.append(Pattern.quote(literal.toString()));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "MetricFilter{include=" + includes + ", exclude=" + excludes + "}";
    }
}
