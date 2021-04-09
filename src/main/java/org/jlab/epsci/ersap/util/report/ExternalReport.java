/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

/**
 * The external report class.
 */
public interface ExternalReport {
    String generateReport(DpeReport dpeData);
}
