/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class PrometheusExporterConfigTest {

    @Test
    void usesDefaultsDerivedFromTestMonitor() {
        assumeTrue(System.getenv(ErsapConstants.ENV_MONITOR_FE) == null,
                "ERSAP_MONITOR_FE is set in this environment");
        assumeTrue(System.getenv(PrometheusExporterConfig.ENV_PREFIX + "MONITOR_HOST") == null);

        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[0]);

        assertEquals("localhost", config.monitorHost());
        assertEquals(ErsapConstants.MONITOR_PORT, config.monitorPort());
        assertEquals(9000, config.monitorPort(), "the Monitor FE proxy port");
        assertEquals("test", config.session());
        assertEquals("", config.engine());
        assertEquals("0.0.0.0", config.prometheusHost());
        assertEquals(9095, config.prometheusPort());
        assertEquals("ersap_", config.sanitizer().prefix());
        assertEquals(10, config.reconnectIntervalSeconds());
        assertEquals(0, config.staleTimeoutSeconds());
        assertFalse(config.exportTimestamps());
        assertFalse(config.jvmMetrics());
        assertEquals("info", config.logLevel());
        assertNull(config.configFile());
    }

    @Test
    void readsCommandLineOptions() {
        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[] {
            "--monitor-host", "10.0.0.1",
            "--monitor-port", "9001",
            "--session", "prod",
            "--engine", "host_java:user:Engine",
            "--prometheus-host", "127.0.0.1",
            "--prometheus-port", "9096",
            "--metric-prefix", "myapp",
            "--reconnect-interval", "3",
            "--stale-timeout", "30",
            "--max-series", "500",
            "--export-timestamps",
            "--jvm-metrics",
            "--log-level", "debug",
        });

        assertEquals("10.0.0.1", config.monitorHost());
        assertEquals(9001, config.monitorPort());
        assertEquals("prod", config.session());
        assertEquals("host_java:user:Engine", config.engine());
        assertEquals("127.0.0.1", config.prometheusHost());
        assertEquals(9096, config.prometheusPort());
        assertEquals("myapp_", config.sanitizer().prefix());
        assertEquals(3, config.reconnectIntervalSeconds());
        assertEquals(30, config.staleTimeoutSeconds());
        assertEquals(500, config.maxSeries());
        assertTrue(config.exportTimestamps());
        assertTrue(config.jvmMetrics());
        assertEquals("debug", config.logLevel());
    }

    @Test
    void collectsRepeatableOptions() {
        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[] {
            "--include", "ersap_service_*",
            "--include", "ersap_user_*",
            "--exclude", "*_bytes_*",
            "--label", "cluster=jlab",
            "--label", "data-center=cc",
        });

        assertEquals(List.of("ersap_service_*", "ersap_user_*"), config.includes());
        assertEquals(List.of("*_bytes_*"), config.excludes());
        assertEquals(Map.of("cluster", "jlab", "data_center", "cc"), config.staticLabels());

        MetricFilter filter = config.filter();
        assertTrue(filter.test("ersap_service_requests_total"));
        assertFalse(filter.test("ersap_service_bytes_sent_total"));
        assertFalse(filter.test("ersap_dpe_cores"));
    }

    @Test
    void splitsCommaSeparatedRepeatableOptions() {
        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[] {
            "--exclude", "ersap_dpe_*,ersap_container_*",
        });

        assertEquals(List.of("ersap_dpe_*", "ersap_container_*"), config.excludes());
    }

    @Test
    void readsAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("exporter.properties");
        Files.writeString(file, String.join("\n",
                "monitor-host=10.0.0.9",
                "monitor-port=9002",
                "session=cooked",
                "prometheus-port=9099",
                "metric-prefix=lab",
                "exclude=ersap_container_*",
                "label=cluster=jlab",
                "export-timestamps=true"));

        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[] {
            "--config", file.toString(),
        });

        assertEquals("10.0.0.9", config.monitorHost());
        assertEquals(9002, config.monitorPort());
        assertEquals("cooked", config.session());
        assertEquals(9099, config.prometheusPort());
        assertEquals("lab_", config.sanitizer().prefix());
        assertEquals(List.of("ersap_container_*"), config.excludes());
        assertEquals(Map.of("cluster", "jlab"), config.staticLabels());
        assertTrue(config.exportTimestamps());
        assertEquals(file, config.configFile());
    }

    @Test
    void commandLineWinsOverThePropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("exporter.properties");
        Files.writeString(file, "monitor-host=from-file\nsession=from-file\n");

        PrometheusExporterConfig config = PrometheusExporterConfig.parse(new String[] {
            "--config", file.toString(),
            "--monitor-host", "from-cli",
        });

        assertEquals("from-cli", config.monitorHost());
        assertEquals("from-file", config.session());
    }

    @Test
    void rejectsAnUnreadableConfigFile(@TempDir Path dir) {
        Path missing = dir.resolve("nope.properties");
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> PrometheusExporterConfig.parse(
                        new String[] {"--config", missing.toString()}));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--prometheus-port", "70000"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--prometheus-port", "not-a-number"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--reconnect-interval", "0"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--stale-timeout", "-1"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--max-series", "0"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--label", "no-equals-sign"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--log-level", "loud"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--include", "/[unclosed/"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--counter-pattern", "([unclosed"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("--unknown-option", "x"));
        assertThrows(PrometheusExporterConfig.ConfigException.class,
                () -> parse("stray-argument"));
    }

    @Test
    void allowsAnEphemeralPrometheusPort() {
        assertEquals(0, parse("--prometheus-port", "0").prometheusPort());
    }

    @Test
    void counterHeuristicIsConfigurable() {
        assertEquals(MonitorMetricParser.DEFAULT_COUNTER_PATTERN,
                parse("--session", "test").counterPattern().pattern());
        assertEquals("^my_.*", parse("--counter-pattern", "^my_.*").counterPattern().pattern());
        assertNull(parse("--counter-pattern", "").counterPattern());
    }

    @Test
    void helpReturnsNoConfiguration() {
        assertNull(PrometheusExporterConfig.parse(new String[] {"--help"}));
        assertNull(PrometheusExporterConfig.parse(new String[] {"-h"}));
    }

    @Test
    void allSessionsIsRepresentedByAStar() {
        assertEquals(PrometheusExporterConfig.ALL_SESSIONS,
                parse("--session", "*").session());
    }

    private static PrometheusExporterConfig parse(String... args) {
        return PrometheusExporterConfig.parse(args);
    }
}
