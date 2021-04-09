/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

import org.jlab.epsci.ersap.base.core.ErsapConstants;

public enum ReportType {
    /** INFO report type.*/
    INFO(ErsapConstants.SERVICE_REPORT_INFO),
    /** DONE report type.*/
    DONE(ErsapConstants.SERVICE_REPORT_DONE),
    /** DATA report type.*/
    DATA(ErsapConstants.SERVICE_REPORT_DATA),
    /** RING report type.*/
    RING(ErsapConstants.SERVICE_REPORT_RING);

    private final String stringValue;

    ReportType(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getValue() {
        return stringValue;
    }

}
