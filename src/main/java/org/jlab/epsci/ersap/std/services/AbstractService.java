/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.services;

import java.util.HashSet;
import java.util.Set;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineSpecification;
import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;

/**
 * An base class for service engines that obtains the service information from a
 * YAML file. This service specification file should be a resource named as the
 * concrete service class.
 */
public abstract class AbstractService implements Engine {

    // Experimental specification file
    private final EngineSpecification info = new EngineSpecification(this.getClass());

    /** This service logger. */
    final Logger logger = new LoggerFactory().getLogger(info.name());


    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    /**
     * Returns the name of the engine.
     *
     * @return the engine name
     */
    public String getName() {
        return info.name();
    }

    @Override
    public Set<String> getStates() {
        return new HashSet<>();
    }

    @Override
    public String getDescription() {
        return info.description();
    }

    @Override
    public String getVersion() {
        return info.version();
    }

    @Override
    public String getAuthor() {
        return String.format("%s  <%s>", info.author(), info.email());
    }
}
