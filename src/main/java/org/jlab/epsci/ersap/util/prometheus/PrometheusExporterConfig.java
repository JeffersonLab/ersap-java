/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.core.ErsapConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable configuration of the {@link PrometheusExporter}.
 *
 * <p>Every setting can come from three places. Later sources win:
 * <ol>
 *   <li>built-in defaults, derived from {@code TestMonitor} and
 *       {@link ErsapConstants} where possible;</li>
 *   <li>a properties file given with {@code --config}, whose keys are the long
 *       option names without the leading dashes;</li>
 *   <li>environment variables, named {@code ERSAP_PROM_} + the long option name
 *       upper-cased with dashes turned into underscores;</li>
 *   <li>command-line options.</li>
 * </ol>
 *
 * <p>{@code ERSAP_MONITOR_FE} — the variable ERSAP itself uses to point
 * components at a Monitor FE — is honoured as the default Monitor FE address, so
 * the exporter can be started with no arguments in an environment already set up
 * for monitoring.
 */
public final class PrometheusExporterConfig {

    /** Session value that subscribes to every session instead of filtering. */
    public static final String ALL_SESSIONS = "*";

    /** Default ERSAP session, matching the {@code TestMonitor} default. */
    public static final String DEFAULT_SESSION = "test";

    /** Default Monitor FE host, matching the {@code TestMonitor} default. */
    public static final String DEFAULT_MONITOR_HOST = "localhost";

    /** Default bind address of the Prometheus HTTP endpoint. */
    public static final String DEFAULT_PROMETHEUS_HOST = "0.0.0.0";

    /** Default port of the Prometheus HTTP endpoint. */
    public static final int DEFAULT_PROMETHEUS_PORT = 9095;

    /** Default delay between Monitor FE subscription attempts, in seconds. */
    public static final int DEFAULT_RECONNECT_INTERVAL = 10;

    /** Prefix of every exporter environment variable. */
    public static final String ENV_PREFIX = "ERSAP_PROM_";

    private static final String OPT_CONFIG = "config";
    private static final String OPT_MONITOR_HOST = "monitor-host";
    private static final String OPT_MONITOR_PORT = "monitor-port";
    private static final String OPT_SESSION = "session";
    private static final String OPT_ENGINE = "engine";
    private static final String OPT_PROM_HOST = "prometheus-host";
    private static final String OPT_PROM_PORT = "prometheus-port";
    private static final String OPT_PREFIX = "metric-prefix";
    private static final String OPT_LABEL = "label";
    private static final String OPT_INCLUDE = "include";
    private static final String OPT_EXCLUDE = "exclude";
    private static final String OPT_COUNTER_PATTERN = "counter-pattern";
    private static final String OPT_RECONNECT = "reconnect-interval";
    private static final String OPT_STALE = "stale-timeout";
    private static final String OPT_MAX_SERIES = "max-series";
    private static final String OPT_TIMESTAMPS = "export-timestamps";
    private static final String OPT_JVM = "jvm-metrics";
    private static final String OPT_LOG_LEVEL = "log-level";

    private static final List<String> LOG_LEVELS =
            List.of("trace", "debug", "info", "warn", "error", "off");

    private final String monitorHost;
    private final int monitorPort;
    private final String session;
    private final String engine;
    private final String prometheusHost;
    private final int prometheusPort;
    private final String metricPrefix;
    private final Map<String, String> staticLabels;
    private final List<String> includes;
    private final List<String> excludes;
    private final Pattern counterPattern;
    private final int reconnectIntervalSeconds;
    private final int staleTimeoutSeconds;
    private final int maxSeries;
    private final boolean exportTimestamps;
    private final boolean jvmMetrics;
    private final String logLevel;
    private final Path configFile;

    private PrometheusExporterConfig(Resolver resolver) {
        this.configFile = resolver.configFile;
        this.monitorHost = resolver.string(OPT_MONITOR_HOST, defaultMonitorHost());
        this.monitorPort = resolver.port(OPT_MONITOR_PORT, defaultMonitorPort());
        this.session = resolver.string(OPT_SESSION, DEFAULT_SESSION);
        this.engine = resolver.string(OPT_ENGINE, "");
        this.prometheusHost = resolver.string(OPT_PROM_HOST, DEFAULT_PROMETHEUS_HOST);
        this.prometheusPort = resolver.port(OPT_PROM_PORT, DEFAULT_PROMETHEUS_PORT);
        this.metricPrefix = resolver.string(OPT_PREFIX, MetricNameSanitizer.DEFAULT_PREFIX);
        this.staticLabels = parseLabels(resolver.list(OPT_LABEL));
        this.includes = resolver.list(OPT_INCLUDE);
        this.excludes = resolver.list(OPT_EXCLUDE);
        this.counterPattern = parseCounterPattern(
                resolver.string(OPT_COUNTER_PATTERN, MonitorMetricParser.DEFAULT_COUNTER_PATTERN));
        this.reconnectIntervalSeconds =
                resolver.positiveInt(OPT_RECONNECT, DEFAULT_RECONNECT_INTERVAL);
        this.staleTimeoutSeconds = resolver.nonNegativeInt(OPT_STALE, 0);
        this.maxSeries = resolver.positiveInt(OPT_MAX_SERIES,
                PrometheusMetricRegistry.DEFAULT_MAX_SERIES);
        this.exportTimestamps = resolver.flag(OPT_TIMESTAMPS, false);
        this.jvmMetrics = resolver.flag(OPT_JVM, false);
        this.logLevel = parseLogLevel(resolver.string(OPT_LOG_LEVEL, "info"));

        validate();
    }

    /**
     * Parses the command line, the environment and the optional properties file.
     *
     * @param args the command-line arguments
     * @return the resolved configuration, or null if {@code --help} was given
     *         (in which case the usage text has already been printed)
     * @throws ConfigException if the configuration is invalid
     */
    public static PrometheusExporterConfig parse(String[] args) {
        OptionParser parser = buildParser();
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            throw new ConfigException(e.getMessage(), e);
        }
        if (options.has("help")) {
            writeHelp(parser, System.out);
            return null;
        }
        if (!options.nonOptionArguments().isEmpty()) {
            throw new ConfigException(
                    "unexpected arguments: " + options.nonOptionArguments());
        }
        return new PrometheusExporterConfig(new Resolver(options));
    }

    /**
     * Writes the usage text to the given stream.
     *
     * @param out the stream to write to
     */
    public static void printHelp(PrintStream out) {
        writeHelp(buildParser(), out);
    }

    /**
     * Gets the Monitor FE host.
     *
     * @return the Monitor FE host or IP address
     */
    public String monitorHost() {
        return monitorHost;
    }

    /**
     * Gets the Monitor FE proxy port.
     *
     * @return the Monitor FE port
     */
    public int monitorPort() {
        return monitorPort;
    }

    /**
     * Gets the ERSAP session to subscribe to.
     *
     * @return the session, or {@value #ALL_SESSIONS} for every session
     */
    public String session() {
        return session;
    }

    /**
     * Gets the canonical engine name the {@code userMetrics} subscription is
     * restricted to.
     *
     * @return the engine name, or the empty string for every engine
     */
    public String engine() {
        return engine;
    }

    /**
     * Gets the bind address of the Prometheus HTTP endpoint.
     *
     * @return the bind address
     */
    public String prometheusHost() {
        return prometheusHost;
    }

    /**
     * Gets the port of the Prometheus HTTP endpoint.
     *
     * @return the HTTP port, or 0 to pick an ephemeral port
     */
    public int prometheusPort() {
        return prometheusPort;
    }

    /**
     * Gets the configured metric name prefix, before normalization.
     *
     * @return the metric prefix
     */
    public String metricPrefix() {
        return metricPrefix;
    }

    /**
     * Gets the labels added to every exported series.
     *
     * @return an unmodifiable map of label name to value
     */
    public Map<String, String> staticLabels() {
        return staticLabels;
    }

    /**
     * Gets the include filter patterns.
     *
     * @return an unmodifiable list of patterns, possibly empty
     */
    public List<String> includes() {
        return includes;
    }

    /**
     * Gets the exclude filter patterns.
     *
     * @return an unmodifiable list of patterns, possibly empty
     */
    public List<String> excludes() {
        return excludes;
    }

    /**
     * Gets the pattern used to promote a user metric to a counter.
     *
     * @return the pattern, or null when the heuristic is disabled
     */
    public Pattern counterPattern() {
        return counterPattern;
    }

    /**
     * Gets the delay between Monitor FE subscription attempts.
     *
     * @return the reconnect interval, in seconds
     */
    public int reconnectIntervalSeconds() {
        return reconnectIntervalSeconds;
    }

    /**
     * Gets the silence window after which the subscription is rebuilt.
     *
     * @return the stale timeout in seconds, or 0 when disabled
     */
    public int staleTimeoutSeconds() {
        return staleTimeoutSeconds;
    }

    /**
     * Gets the cap on the number of exported time series.
     *
     * @return the maximum number of series
     */
    public int maxSeries() {
        return maxSeries;
    }

    /**
     * Tells whether the per-metric source timestamp gauge is exported.
     *
     * @return true if enabled
     */
    public boolean exportTimestamps() {
        return exportTimestamps;
    }

    /**
     * Tells whether the standard JVM metrics of the exporter process are exported.
     *
     * @return true if enabled
     */
    public boolean jvmMetrics() {
        return jvmMetrics;
    }

    /**
     * Gets the log level.
     *
     * @return one of trace, debug, info, warn, error, off
     */
    public String logLevel() {
        return logLevel;
    }

    /**
     * Gets the properties file the configuration was loaded from.
     *
     * @return the config file path, or null if none was given
     */
    public Path configFile() {
        return configFile;
    }

    /**
     * Builds the metric name sanitizer described by this configuration.
     *
     * @return a new sanitizer
     */
    public MetricNameSanitizer sanitizer() {
        return new MetricNameSanitizer(metricPrefix);
    }

    /**
     * Builds the metric filter described by this configuration.
     *
     * @return a new filter
     */
    public MetricFilter filter() {
        return MetricFilter.of(includes, excludes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("monitor-fe=").append(monitorHost).append(':').append(monitorPort)
          .append(", session=").append(session);
        if (!engine.isEmpty()) {
            sb.append(", engine=").append(engine);
        }
        sb.append(", endpoint=http://").append(prometheusHost).append(':')
          .append(prometheusPort).append("/metrics")
          .append(", prefix=").append(new MetricNameSanitizer(metricPrefix).prefix());
        if (!staticLabels.isEmpty()) {
            sb.append(", labels=").append(staticLabels);
        }
        if (!includes.isEmpty()) {
            sb.append(", include=").append(includes);
        }
        if (!excludes.isEmpty()) {
            sb.append(", exclude=").append(excludes);
        }
        sb.append(", reconnect=").append(reconnectIntervalSeconds).append('s');
        if (staleTimeoutSeconds > 0) {
            sb.append(", stale-timeout=").append(staleTimeoutSeconds).append('s');
        }
        return sb.toString();
    }

    private void validate() {
        if (monitorHost.isEmpty()) {
            throw new ConfigException("empty Monitor FE host");
        }
        if (prometheusHost.isEmpty()) {
            throw new ConfigException("empty Prometheus bind address");
        }
        try {
            filter();
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
        if (new MetricNameSanitizer(metricPrefix).prefix().isEmpty() && !metricPrefix.isEmpty()) {
            throw new ConfigException("invalid metric prefix: \"" + metricPrefix + "\"");
        }
    }

    private static String defaultMonitorHost() {
        DpeName monitorFe = monitorFeFromEnv();
        return monitorFe == null ? DEFAULT_MONITOR_HOST : monitorFe.address().host();
    }

    private static int defaultMonitorPort() {
        DpeName monitorFe = monitorFeFromEnv();
        return monitorFe == null ? ErsapConstants.MONITOR_PORT : monitorFe.address().pubPort();
    }

    private static DpeName monitorFeFromEnv() {
        String value = System.getenv(ErsapConstants.ENV_MONITOR_FE);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new DpeName(value.trim());
        } catch (RuntimeException e) {
            // not a canonical DPE name: ignore it and fall back to the defaults
            return null;
        }
    }

    private static Map<String, String> parseLabels(List<String> values) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String value : values) {
            int sep = value.indexOf('=');
            if (sep <= 0) {
                throw new ConfigException(
                        "invalid label \"" + value + "\", expected name=value");
            }
            String name = value.substring(0, sep).trim();
            String labelValue = value.substring(sep + 1).trim();
            if (name.isEmpty()) {
                throw new ConfigException("invalid label \"" + value + "\", empty name");
            }
            labels.put(MetricNameSanitizer.sanitizeLabelName(name), labelValue);
        }
        return Collections.unmodifiableMap(labels);
    }

    private static Pattern parseCounterPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(pattern.trim(), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new ConfigException("invalid counter pattern: " + e.getMessage(), e);
        }
    }

    private static String parseLogLevel(String level) {
        String normalized = level.trim().toLowerCase(Locale.ROOT);
        if (!LOG_LEVELS.contains(normalized)) {
            throw new ConfigException("invalid log level \"" + level + "\", expected one of "
                    + LOG_LEVELS);
        }
        return normalized;
    }

    private static OptionParser buildParser() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Show this help and exit.").forHelp();

        parser.accepts(OPT_CONFIG, "Properties file with any of the settings below, "
                + "keyed by long option name.").withRequiredArg().describedAs("file");
        parser.accepts(OPT_MONITOR_HOST, "Monitor FE host or IP address. Defaults to the host "
                + "of $ERSAP_MONITOR_FE, or " + DEFAULT_MONITOR_HOST + ".")
                .withRequiredArg().describedAs("host");
        parser.accepts(OPT_MONITOR_PORT, "Monitor FE proxy port. Defaults to the port of "
                + "$ERSAP_MONITOR_FE, or " + ErsapConstants.MONITOR_PORT + ".")
                .withRequiredArg().ofType(Integer.class).describedAs("port");
        parser.accepts(OPT_SESSION, "ERSAP session to subscribe to. Use \"" + ALL_SESSIONS
                + "\" for every session. Default: " + DEFAULT_SESSION + ".")
                .withRequiredArg().describedAs("session");
        parser.accepts(OPT_ENGINE, "Restrict the userMetrics subscription to one canonical "
                + "engine name. Default: every engine.")
                .withRequiredArg().describedAs("name");

        parser.accepts(OPT_PROM_HOST, "Bind address of the Prometheus endpoint. Default: "
                + DEFAULT_PROMETHEUS_HOST + ".").withRequiredArg().describedAs("address");
        parser.accepts(OPT_PROM_PORT, "Port of the Prometheus endpoint, 0 for an ephemeral "
                + "port. Default: " + DEFAULT_PROMETHEUS_PORT + ".")
                .withRequiredArg().ofType(Integer.class).describedAs("port");

        parser.accepts(OPT_PREFIX, "Prefix for every exported metric name. Default: "
                + MetricNameSanitizer.DEFAULT_PREFIX + ".")
                .withRequiredArg().describedAs("prefix");
        parser.accepts(OPT_LABEL, "Static label added to every series. Repeatable.")
                .withRequiredArg().describedAs("name=value");
        parser.accepts(OPT_INCLUDE, "Only export metrics matching this glob, or /regex/. "
                + "Repeatable. Matched against the final metric name.")
                .withRequiredArg().describedAs("pattern");
        parser.accepts(OPT_EXCLUDE, "Never export metrics matching this glob, or /regex/. "
                + "Repeatable. Wins over --include.")
                .withRequiredArg().describedAs("pattern");
        parser.accepts(OPT_COUNTER_PATTERN, "Regex deciding which user metrics become "
                + "counters instead of gauges. Empty disables the heuristic. Default: "
                + MonitorMetricParser.DEFAULT_COUNTER_PATTERN)
                .withRequiredArg().describedAs("regex");

        parser.accepts(OPT_RECONNECT, "Seconds between Monitor FE subscription attempts. "
                + "Default: " + DEFAULT_RECONNECT_INTERVAL + ".")
                .withRequiredArg().ofType(Integer.class).describedAs("seconds");
        parser.accepts(OPT_STALE, "Rebuild the subscription after this many seconds without a "
                + "Monitor FE message. 0 disables it. Default: 0.")
                .withRequiredArg().ofType(Integer.class).describedAs("seconds");
        parser.accepts(OPT_MAX_SERIES, "Cap on the number of exported time series. Default: "
                + PrometheusMetricRegistry.DEFAULT_MAX_SERIES + ".")
                .withRequiredArg().ofType(Integer.class).describedAs("count");
        parser.accepts(OPT_TIMESTAMPS, "Also export <prefix>"
                + PrometheusMetricRegistry.TIMESTAMP_METRIC + ".");
        parser.accepts(OPT_JVM, "Also export the JVM metrics of the exporter process.");
        parser.accepts(OPT_LOG_LEVEL, "Log level: " + String.join(", ", LOG_LEVELS)
                + ". Default: info.").withRequiredArg().describedAs("level");
        return parser;
    }

    private static void writeHelp(OptionParser parser, PrintStream out) {
        out.println("usage: ersap-prometheus-exporter [options]");
        out.println();
        out.println("Subscribes to an ERSAP Monitor FE and exports every metric it publishes");
        out.println("through a Prometheus /metrics endpoint.");
        out.println();
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            parser.printHelpOn(writer);
        } catch (IOException e) {
            out.println("could not print the option help: " + e.getMessage());
        }
        out.println();
        out.println("Every option can also be set through the environment, as "
                + ENV_PREFIX + "<OPTION>");
        out.println("with dashes replaced by underscores, for example:");
        out.println();
        out.println("  " + ENV_PREFIX + "MONITOR_HOST=10.0.0.1");
        out.println("  " + ENV_PREFIX + "PROMETHEUS_PORT=9095");
        out.println("  " + ENV_PREFIX + "EXCLUDE='ersap_dpe_*,ersap_container_*'");
        out.println();
        out.println("Repeatable options take a comma-separated list in the environment and in");
        out.println("the properties file. $" + ErsapConstants.ENV_MONITOR_FE
                + " provides the default Monitor FE address.");
        out.println();
        out.println("Precedence: defaults < --config file < environment < command line.");
    }

    /**
     * Raised when the configuration cannot be resolved.
     */
    public static class ConfigException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message the reason the configuration is invalid
         */
        public ConfigException(String message) {
            super(message);
        }

        /**
         * Creates the exception.
         *
         * @param message the reason the configuration is invalid
         * @param cause   the underlying error
         */
        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Resolves each setting across the command line, the environment, the
     * properties file and the built-in defaults, in that order of precedence.
     */
    private static final class Resolver {

        private final OptionSet options;
        private final Properties properties = new Properties();
        private final Path configFile;

        Resolver(OptionSet options) {
            this.options = options;
            this.configFile = readConfigFile();
        }

        private Path readConfigFile() {
            String path = raw(OPT_CONFIG);
            if (path == null || path.trim().isEmpty()) {
                return null;
            }
            Path file = Paths.get(path.trim());
            if (!Files.isReadable(file)) {
                throw new ConfigException("cannot read the config file: " + file);
            }
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                throw new ConfigException("cannot read the config file " + file + ": "
                        + e.getMessage(), e);
            }
            return file;
        }

        private String raw(String option) {
            if (options.has(option)) {
                // jopt-simple converts typed arguments lazily, so a bad value
                // only fails here and not in OptionParser.parse
                Object value;
                try {
                    value = options.valueOf(option);
                } catch (OptionException e) {
                    throw new ConfigException("--" + option + ": " + e.getMessage(), e);
                }
                if (value != null) {
                    return value.toString();
                }
            }
            String env = System.getenv(envName(option));
            if (env != null && !env.trim().isEmpty()) {
                return env.trim();
            }
            String property = properties.getProperty(option);
            if (property != null && !property.trim().isEmpty()) {
                return property.trim();
            }
            return null;
        }

        String string(String option, String fallback) {
            String value = raw(option);
            return value == null ? fallback : value;
        }

        boolean flag(String option, boolean fallback) {
            if (options.has(option)) {
                return true;
            }
            String value = raw(option);
            if (value == null) {
                return fallback;
            }
            return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
                    || "1".equals(value);
        }

        List<String> list(String option) {
            List<String> values = new ArrayList<>();
            if (options.has(option)) {
                for (Object value : options.valuesOf(option)) {
                    addSplit(values, value.toString());
                }
                return Collections.unmodifiableList(values);
            }
            String value = raw(option);
            if (value != null) {
                addSplit(values, value);
            }
            return Collections.unmodifiableList(values);
        }

        int port(String option, int fallback) {
            int value = intValue(option, fallback);
            if (value < 0 || value > 65535) {
                throw new ConfigException("invalid port for --" + option + ": " + value);
            }
            return value;
        }

        int positiveInt(String option, int fallback) {
            int value = intValue(option, fallback);
            if (value <= 0) {
                throw new ConfigException("--" + option + " must be positive, got " + value);
            }
            return value;
        }

        int nonNegativeInt(String option, int fallback) {
            int value = intValue(option, fallback);
            if (value < 0) {
                throw new ConfigException("--" + option + " must not be negative, got " + value);
            }
            return value;
        }

        private int intValue(String option, int fallback) {
            String value = raw(option);
            if (value == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new ConfigException("--" + option + " expects a number, got \""
                        + value + "\"", e);
            }
        }

        private static void addSplit(List<String> target, String value) {
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    target.add(trimmed);
                }
            }
        }

        private static String envName(String option) {
            return ENV_PREFIX + option.toUpperCase(Locale.ROOT).replace('-', '_');
        }
    }

    /**
     * Builder used by tests and embedders to create a configuration
     * programmatically, without touching the command line or the environment.
     */
    public static final class Builder {

        private final List<String> args = new ArrayList<>();

        /**
         * Adds a valued option.
         *
         * @param option the long option name, without dashes
         * @param value  the option value
         * @return this builder
         */
        public Builder with(String option, Object value) {
            args.add("--" + option);
            args.add(String.valueOf(value));
            return this;
        }

        /**
         * Adds a flag option.
         *
         * @param option the long option name, without dashes
         * @return this builder
         */
        public Builder with(String option) {
            args.add("--" + option);
            return this;
        }

        /**
         * Resolves the configuration.
         *
         * @return the configuration
         */
        public PrometheusExporterConfig build() {
            return parse(args.toArray(new String[0]));
        }
    }
}
