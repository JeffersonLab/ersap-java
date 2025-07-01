/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.services;

/**
 * A problem in the event writer implementation.
 */
public class EventWriterException extends Exception {

    /**
     * Constructs a new exception.
     *
     * @param message the detail message
     */
    public EventWriterException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception.
     *
     * @param cause the cause of the exception
     */
    public EventWriterException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public EventWriterException(String message, Throwable cause) {
        super(message, cause);
    }
}
