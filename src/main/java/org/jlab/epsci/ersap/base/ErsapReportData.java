/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

interface ErsapReportData<T extends ErsapName> {

    /**
     * Gets the ERSAP canonical name.
     *
     * @return the canonical name
     */
    T name();
}
