/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

/**
 * An interface to handle the JSON reports published by ERSAP.
 */
public interface DpeReportCallback {

    /**
     * Processes the parsed JSON report published by a ERSAP DPE.
     *
     * @param registrationData the DPE registration data
     * @param runtimeData the DPE runtime data
     */
    void callback(DpeRegistrationData registrationData, DpeRuntimeData runtimeData);
}
