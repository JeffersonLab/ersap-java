/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.ErsapUtil;

/**
 * An error in the orchestrator.
 */
public class OrchestratorException extends RuntimeException {

    private static final long serialVersionUID = -5459481851420223735L;

    /**
     * Constructs a new exception.
     *
     * @param message the detail message
     */
    public OrchestratorException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception.
     *
     * @param cause the cause of the exception
     */
    public OrchestratorException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception.
     *
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public OrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getMessage());
        for (Throwable e: ErsapUtil.getThrowableList(getCause())) {
            sb.append(": ").append(e.getMessage());
        }
        return sb.toString();
    }
}
