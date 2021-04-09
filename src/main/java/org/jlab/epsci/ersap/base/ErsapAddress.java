/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.coda.xmsg.net.xMsgProxyAddress;

/**
 * The address where a ERSAP component is listening messages.
 */
public class ErsapAddress extends xMsgProxyAddress {

    ErsapAddress(String host) {
        super(host);
    }

    ErsapAddress(String host, int port) {
        super(host, port);
    }
}
