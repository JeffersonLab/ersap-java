/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.MonitorReportFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// checkstyle.off: Javadoc
/**
 * Representative Monitor FE messages, in the real ERSAP wire format.
 *
 * <p>{@code dpe-report.json} is a complete {@code dpeReport} document — the same
 * {@code DPERegistration}/{@code DPERuntime} pair that
 * {@code ErsapSubscriptions.DpeReportSubscription} decodes — describing the
 * three-service pipeline of {@code ObservabilityTest.md}. The user metrics
 * payload is the one {@code EventRateMonitor} publishes.
 */
final class MonitorReports {

    static final String DOCUMENT = read("dpe-report.json");

    /** The exact JSON payload published by {@code EventRateMonitor}. */
    static final String USER_METRICS_JSON =
            "{\"event_rate_hz\":3127.4,\"events_in_window\":3127,\"total_events\":45831}";

    /** The canonical engine name that publishes the user metrics above. */
    static final String ENGINE = "10.1.1.10_java:user:EventRateMonitor";

    /** The session used throughout the fixtures. */
    static final String SESSION = "test";

    private MonitorReports() { }

    static DpeRegistrationData registration() {
        return MonitorReportFactory.registration(DOCUMENT);
    }

    static DpeRuntimeData runtime() {
        return MonitorReportFactory.runtime(DOCUMENT);
    }

    static Map<String, Object> userMetrics() {
        return new JSONObject(USER_METRICS_JSON).toMap();
    }

    static Map<String, Object> userMetrics(String key, Object value) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(key, value);
        return metrics;
    }

    private static String read(String resource) {
        try (InputStream in = MonitorReports.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
