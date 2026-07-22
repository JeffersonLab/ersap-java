/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.engine.EngineDataType;

import java.util.Map;
import java.util.Set;

/**
 * Callback interface for receiving user-defined engine metrics published to the
 * Monitor FE via {@code EngineMetricsPublisher}.
 */
public interface UserMetricsHandler {

    /**
     * Data types this handler can deserialize. Typically a singleton set
     * containing {@link EngineDataType#JSON}.
     */
    Set<EngineDataType> dataTypes();

    /**
     * Called once per engine {@code execute()} invocation that published at
     * least one metric.
     *
     * @param session the DPE session
     * @param engine  the canonical engine name
     * @param metrics the key-value pairs published by the engine
     */
    void handleMetrics(String session, String engine, Map<String, Object> metrics);
}
