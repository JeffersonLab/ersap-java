/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;

import java.util.Set;

/**
 * Handles the reports published by ERSAP services.
 */
public interface EngineReportHandler extends AutoCloseable {

    /**
     * Processes the reported output event.
     *
     * @param event the reported output
     */
    void handleEvent(EngineData event);

    /**
     * Gets the set of output data types reported by the service.
     *
     * @return the expected output data types, it cannot be null nor empty
     */
    Set<EngineDataType> dataTypes();

    @Override
    default void close() throws Exception { }
}
