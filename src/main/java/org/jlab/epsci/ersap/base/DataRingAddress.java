/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;

/**
 * The address of a ERSAP data-ring.
 */
public class DataRingAddress extends ErsapAddress {

    /**
     * Identify a ERSAP data-ring.
     *
     * @param host the host address of the data ring.
     */
    public DataRingAddress(String host) {
        super(host, ErsapConstants.MONITOR_PORT);
    }

    /**
     * Identify a ERSAP data-ring.
     *
     * @param host the host address of the data ring.
     * @param port the port used by the data ring.
     */
    public DataRingAddress(String host, int port) {
        super(host, port);
    }

    /**
     * Identify a ERSAP data-ring.
     *
     * @param dpe the DPE acting as a data ring.
     */
    public DataRingAddress(DpeName dpe) {
        super(dpe.address().host(), dpe.address().pubPort());
    }
}
