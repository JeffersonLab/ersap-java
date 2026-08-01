/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// checkstyle.off: Javadoc
class MetricFilterTest {

    @Test
    void acceptsEverythingByDefault() {
        MetricFilter filter = MetricFilter.of(List.of(), List.of());
        assertTrue(filter.isAcceptAll());
        assertTrue(filter.test("ersap_service_requests_total"));
        assertTrue(filter.test("anything"));
    }

    @Test
    void nullPatternListsAreAcceptAll() {
        assertTrue(MetricFilter.of(null, null).test("ersap_dpe_cpu_usage_percent"));
    }

    @Test
    void includeGlobKeepsOnlyMatchingMetrics() {
        MetricFilter filter = MetricFilter.of(List.of("ersap_service_*"), List.of());
        assertTrue(filter.test("ersap_service_requests_total"));
        assertTrue(filter.test("ersap_service_bytes_sent_total"));
        assertFalse(filter.test("ersap_dpe_cpu_usage_percent"));
        assertFalse(filter.test("ersap_user_event_rate_hz"));
    }

    @Test
    void includeMatchesTheWholeName() {
        MetricFilter filter = MetricFilter.of(List.of("ersap_service_requests_total"), List.of());
        assertTrue(filter.test("ersap_service_requests_total"));
        assertFalse(filter.test("ersap_service_requests_total_extra"));
        assertFalse(filter.test("x_ersap_service_requests_total"));
    }

    @Test
    void severalIncludesAreOred() {
        MetricFilter filter = MetricFilter.of(List.of("ersap_user_*", "ersap_dpe_*"), List.of());
        assertTrue(filter.test("ersap_user_event_rate_hz"));
        assertTrue(filter.test("ersap_dpe_cpu_usage_percent"));
        assertFalse(filter.test("ersap_service_requests_total"));
    }

    @Test
    void excludeDropsMatchingMetrics() {
        MetricFilter filter = MetricFilter.of(List.of(), List.of("ersap_container_*"));
        assertFalse(filter.test("ersap_container_requests_total"));
        assertTrue(filter.test("ersap_service_requests_total"));
    }

    @Test
    void excludeWinsOverInclude() {
        MetricFilter filter = MetricFilter.of(
                List.of("ersap_service_*"), List.of("*_bytes_*"));
        assertTrue(filter.test("ersap_service_requests_total"));
        assertFalse(filter.test("ersap_service_bytes_sent_total"));
        assertFalse(filter.test("ersap_service_bytes_received_total"));
    }

    @Test
    void supportsQuestionMarkGlob() {
        MetricFilter filter = MetricFilter.of(List.of("ersap_v?_rate"), List.of());
        assertTrue(filter.test("ersap_v1_rate"));
        assertTrue(filter.test("ersap_v2_rate"));
        assertFalse(filter.test("ersap_v12_rate"));
    }

    @Test
    void treatsGlobLiteralsAsLiterals() {
        // a dot in a glob is a dot, not "any character"
        MetricFilter filter = MetricFilter.of(List.of("a.b"), List.of());
        assertTrue(filter.test("a.b"));
        assertFalse(filter.test("axb"));
    }

    @Test
    void supportsRegexWrappedInSlashes() {
        MetricFilter filter = MetricFilter.of(
                List.of("/^ersap_(dpe|service)_.*$/"), List.of());
        assertTrue(filter.test("ersap_dpe_cpu_usage_percent"));
        assertTrue(filter.test("ersap_service_requests_total"));
        assertFalse(filter.test("ersap_user_event_rate_hz"));
    }

    @Test
    void regexAlsoMatchesTheWholeName() {
        MetricFilter filter = MetricFilter.of(List.of("/ersap_dpe_.*/"), List.of());
        assertTrue(filter.test("ersap_dpe_cores"));
        assertFalse(filter.test("prefix_ersap_dpe_cores"));
    }

    @Test
    void rejectsNullNames() {
        assertFalse(MetricFilter.of(List.of("*"), List.of()).test(null));
    }

    @Test
    void ignoresBlankPatterns() {
        MetricFilter filter = MetricFilter.of(List.of("", "   "), List.of(""));
        assertTrue(filter.isAcceptAll());
    }

    @Test
    void rejectsInvalidRegex() {
        assertThrows(IllegalArgumentException.class, () -> MetricFilter.compile("/[unclosed/"));
        assertThrows(IllegalArgumentException.class, () -> MetricFilter.compile(""));
    }
}
