/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.engine;

import java.util.Set;

/**
 * Service engine interface.
 *
 *
 */
public interface Engine {

    /**
     * Configures the engine with the given input data.
     * <p>
     * This method can be executed concurrently in several threads.
     *
     * @param input the data to configure the engine with
     * @return an (optional) result and/or status of the configuration request,
     *         it could be null or without output data (but not recommended)
     */
    EngineData configure(EngineData input);

    /**
     * Executes the engine with the given input data.
     * <p>
     * This method can be executed concurrently in several threads.
     *
     * @param input the data to execute the engine with
     * @return the result and/or status of the execution request,
     *         it cannot be null or without data (unless it is an error status),
     *         and it should not be the same input data reference
     */
    EngineData execute(EngineData input);

    /**
     * Executes the engine with the given set of input data.
     * <p>
     * This method can be executed concurrently in several threads.
     *
     * @param inputs the data set to execute the engine with
     * @return the result and/or status of the execution request,
     *         it cannot be null or without data (unless it is an error status),
     *         and it should not be the same as one of the input data references
     */
    EngineData executeGroup(Set<EngineData> inputs);

    /**
     * Gets the set of input data types supported by the engine.
     *
     * @return the allowed input data types,
     *         it cannot be null nor empty
     */
    Set<EngineDataType> getInputDataTypes();

    /**
     * Gets the set of output data types supported by the engine.
     *
     * @return the allowed output data types,
     *         it cannot be null nor empty
     */
    Set<EngineDataType> getOutputDataTypes();

    /**
     * Gets the set of possible states for the engine results.
     * States should be set as the result of processing a request
     * and stored in the result data.
     * Orchestrators and clients of the engine can use the states
     * as part of an application composition
     * to provide advanced routing of the execution outputs.
     *
     * @return a set with the possible states for the engine results,
     *         it can be null or empty if the engine doesn't uses states
     */
    Set<String> getStates();

    /**
     * Gets a description of the engine.
     * <p>
     * A good description should provide an initial summary sentence about the
     * engine, a section with complete details of what the engine does,
     * how to configure the service and the supported data types,
     * all the requests that the engine can handle, with the expected input
     * types and values and the resulting output types and values,
     * the returned errors for failed requests, and a changelog for the current
     * version.
     *
     * @return a string with the description
     */
    String getDescription();

    /**
     * Gets the version of the engine.
     * Examples are: a <i>semver</i> release number, a {@code git describe}
     * output, an <i>alpha</i> or <i>beta</i> testing release, etc.
     *
     * @return a version string
     */
    String getVersion();

    /**
     * Gets the author(s) of the engine. Authors are responsible of developing
     * and maintaining a service engine.
     * <p>
     * This information should provide at least a name and a contact email.
     *
     * @return an string listing the author(s) of the engine.
     */
    String getAuthor();

    /**
     * Resets this engine to its initial setup. It should at least revert
     * any changes done by configuration requests and side-effects of execute
     * requests.
     */
    void reset();

    /**
     * Destroy this engine, closing and cleaning up any opened resources.
     */
    void destroy();
}
