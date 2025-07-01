/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;

/**
 * Handles the reports published by ERSAP DPEs.
 */
public interface DpeReportHandler extends AutoCloseable {

    /**
     * Processes the DPE report.
     *
     * @param dpeRegistration the DPE registration report
     * @param dpeRuntime the DPE runtime report
     */
    void handleReport(DpeRegistrationData dpeRegistration, DpeRuntimeData dpeRuntime);

    @Override
    default void close() throws Exception { }
}
