/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

/**
 * An interface to handle the JSON reports published by ERSAP.
 */
public interface GenericCallback {

    /**
     * Receives and process the data that ERSAP has published.
     * The data is in JSON format and contain registration and runtime
     * information related to the current status of the ERSAP cloud.
     *
     * @param json the published data
     */
    void callback(String json);
}
