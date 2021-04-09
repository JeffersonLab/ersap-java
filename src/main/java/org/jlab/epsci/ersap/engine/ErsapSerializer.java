/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.engine;

import org.jlab.epsci.ersap.base.error.ErsapException;

import java.nio.ByteBuffer;

/**
 * Provides the custom serialization methods to send user defined data through
 * the network.
 */
public interface ErsapSerializer {

    /**
     * Serializes the user object into a byte buffer and returns it.
     *
     * @param data the user object stored on the {@link EngineData}
     * @throws ErsapException if the data could not be serialized
     * @return the serialized user object
     */
    ByteBuffer write(Object data) throws ErsapException;

    /**
     * De-serializes the byte buffer into the user object and returns it.
     *
     * @param buffer the serialized data
     * @throws ErsapException if the data could not be deserialized
     * @return the user-object
     */
    Object read(ByteBuffer buffer) throws ErsapException;
}
