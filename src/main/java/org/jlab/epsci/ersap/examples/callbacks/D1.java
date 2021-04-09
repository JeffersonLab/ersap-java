/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.callbacks;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;

/**
 * DPE report handler example.
 */
public class D1 implements DpeReportHandler {

    @Override
    public void handleReport(DpeRegistrationData dpeRegistration, DpeRuntimeData dpeRuntime) {
        System.out.printf("%s: received DPE report from %s%n",
                ErsapUtil.getCurrentTime(), dpeRegistration.name());
    }
}
