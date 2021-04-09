/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.callbacks;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.orchestrators.EngineReportHandler;

import java.util.Set;

/**
 * Engine report handler example.
 */
public class M1 implements EngineReportHandler {

    @Override
    public void handleEvent(EngineData event) {
        System.out.printf("%s: received %s [%s] from %s%n",
                ErsapUtil.getCurrentTime(),
                event.getExecutionState(),
                event.getMimeType(),
                event.getEngineName());
    }

    @Override
    public Set<EngineDataType> dataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING, EngineDataType.JSON);
    }
}
