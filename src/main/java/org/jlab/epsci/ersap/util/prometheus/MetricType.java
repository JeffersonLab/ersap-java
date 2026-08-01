/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

/**
 * The Prometheus metric type inferred for a Monitor FE value.
 *
 * <p>Only the two types that can be reconstructed safely from the Monitor FE
 * wire format are supported. The Monitor FE never publishes bucket or quantile
 * information, so histograms and summaries cannot be built correctly and are
 * intentionally not part of this enum.
 */
public enum MetricType {

    /** A value that may increase and decrease (rates, loads, sizes, gauges). */
    GAUGE,

    /** A cumulative value that only increases while the source component lives. */
    COUNTER
}
