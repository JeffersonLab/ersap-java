/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

/**
 * An error configuring the orchestrator.
 */
public class OrchestratorConfigException extends OrchestratorException {

    private static final long serialVersionUID = 7169655555225259425L;

    /**
     * Constructs a new exception.
     *
     * @param message the detail message
     */
    public OrchestratorConfigException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception.
     *
     * @param cause the cause of the exception
     */
    public OrchestratorConfigException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public OrchestratorConfigException(String message, Throwable cause) {
        super(message, cause);
    }

}
