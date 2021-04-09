/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.engine.EngineData;

/**
 * An interface to handle the reports published by ERSAP services.
 */
public interface EngineCallback {

    /**
     * Receives and process the data that a service has published.
     * The data can contain the actual result of the service and/or status
     * information about the execution that generated the data.
     *
     * @param data the published data
     */
    void callback(EngineData data);
}
