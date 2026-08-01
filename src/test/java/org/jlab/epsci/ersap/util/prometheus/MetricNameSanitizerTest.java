/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class MetricNameSanitizerTest {

    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");

    private final MetricNameSanitizer sanitizer = new MetricNameSanitizer("ersap");

    @Test
    void sanitizesDocumentedExamples() {
        assertEquals("ersap_event_rate", sanitizer.sanitize("Event Rate"));
        assertEquals("ersap_input_bytes_sec", sanitizer.sanitize("input.bytes/sec"));
        assertEquals("ersap_7_second_average", sanitizer.sanitize("7_second_average"));
        assertEquals("ersap_event_rate_hz", sanitizer.sanitize("event_rate_hz"));
        assertEquals("ersap_total_events", sanitizer.sanitize("total_events"));
        assertEquals("ersap_dpe_cpu_usage_percent", sanitizer.sanitize("dpe.cpu_usage_percent"));
    }

    @Test
    void collapsesRunsOfSeparators() {
        assertEquals("ersap_a_b", sanitizer.sanitize("a  --  b"));
        assertEquals("ersap_a_b", sanitizer.sanitize("a...b"));
    }

    @Test
    void stripsLeadingAndTrailingSeparators() {
        assertEquals("ersap_rate", sanitizer.sanitize("  rate  "));
        assertEquals("ersap_rate", sanitizer.sanitize("__rate__"));
        assertEquals("ersap_rate", sanitizer.sanitize("//rate//"));
    }

    @Test
    void lowerCasesNames() {
        assertEquals("ersap_eventrate", sanitizer.sanitize("EventRate"));
        assertEquals("ersap_event_rate", sanitizer.sanitize("EVENT RATE"));
    }

    @Test
    void handlesWhitespaceAndSpecialCharacters() {
        assertEquals("ersap_a_b_c", sanitizer.sanitize("a\tb\nc"));
        assertEquals("ersap_99_9th_percentile", sanitizer.sanitize("99.9th percentile"));
        assertEquals("ersap_bytes_sec", sanitizer.sanitize("bytes/sec"));
        assertEquals("ersap_queue_size", sanitizer.sanitize("queue[size]"));
        assertEquals("ersap_a_b", sanitizer.sanitize("a+b"));
    }

    @Test
    void replacesColonsBecauseTheyAreReservedForRecordingRules() {
        assertEquals("ersap_host_container_engine",
                sanitizer.sanitize("host:container:Engine"));
    }

    @Test
    void handlesNamesStartingWithADigit() {
        assertEquals("ersap_7_second_average", sanitizer.sanitize("7_second_average"));

        MetricNameSanitizer noPrefix = new MetricNameSanitizer("");
        assertEquals("_7_second_average", noPrefix.sanitize("7_second_average"));
        assertEquals("_1", noPrefix.sanitize("1"));
        assertTrue(VALID_NAME.matcher(noPrefix.sanitize("7_second_average")).matches());
    }

    @Test
    void alwaysProducesAValidPrometheusName() {
        List<String> sources = List.of(
                "Event Rate", "input.bytes/sec", "7up", "!!!", "  ", "a-b-c",
                "us latency", "99.9th percentile", "___", "-", "0", "\u00b5s/event");
        MetricNameSanitizer noPrefix = new MetricNameSanitizer("");
        for (String source : sources) {
            String prefixed = sanitizer.sanitize(source);
            String bare = noPrefix.sanitize(source);
            assertTrue(VALID_NAME.matcher(prefixed).matches(), "invalid name: " + prefixed);
            assertTrue(VALID_NAME.matcher(bare).matches(), "invalid name: " + bare);
        }
    }

    @Test
    void fallsBackWhenNothingSurvivesSanitization() {
        assertEquals("ersap_unnamed", sanitizer.sanitize("!!!"));
        assertEquals("ersap_unnamed", sanitizer.sanitize("   "));
        assertEquals("ersap_unnamed", sanitizer.sanitize("-"));
        assertEquals("ersap_unnamed", sanitizer.sanitize(null));
    }

    @Test
    void isDeterministic() {
        for (int i = 0; i < 100; i++) {
            assertEquals("ersap_input_bytes_sec", sanitizer.sanitize("input.bytes/sec"));
        }
    }

    @Test
    void differentSourceNamesCanCollide() {
        // the collision the registry must detect and report
        assertEquals(sanitizer.sanitize("Event Rate"), sanitizer.sanitize("event.rate"));
        assertEquals(sanitizer.sanitize("Event Rate"), sanitizer.sanitize("EVENT-RATE"));
        assertNotEquals(sanitizer.sanitize("Event Rate"), sanitizer.sanitize("event_rates"));
    }

    @Test
    void normalizesThePrefix() {
        assertEquals("ersap_", new MetricNameSanitizer("ersap").prefix());
        assertEquals("ersap_", new MetricNameSanitizer("ersap_").prefix());
        assertEquals("ersap_", new MetricNameSanitizer("ERSAP").prefix());
        assertEquals("my_app_", new MetricNameSanitizer("my app").prefix());
        assertEquals("", new MetricNameSanitizer("").prefix());
        assertEquals("", new MetricNameSanitizer(null).prefix());
        assertEquals("", new MetricNameSanitizer("   ").prefix());
    }

    @Test
    void sanitizesLabelNames() {
        assertEquals("host", MetricNameSanitizer.sanitizeLabelName("Host"));
        assertEquals("data_center", MetricNameSanitizer.sanitizeLabelName("data-center"));
        assertEquals("_1st", MetricNameSanitizer.sanitizeLabelName("1st"));
        assertEquals("unnamed", MetricNameSanitizer.sanitizeLabelName("***"));
    }
}
