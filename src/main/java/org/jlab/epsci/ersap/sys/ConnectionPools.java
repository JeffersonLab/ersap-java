/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.coda.xmsg.core.xMsgConnectionPool;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;

class ConnectionPools implements AutoCloseable {

    final xMsgConnectionPool mainPool;
    final xMsgConnectionPool uncheckedPool;

    ConnectionPools(xMsgProxyAddress defaultProxy) {
        mainPool = xMsgConnectionPool.newBuilder()
                .withProxy(defaultProxy)
                .withPreConnectionSetup(s -> {
                    s.setRcvHWM(0);
                    s.setSndHWM(0);
                })
                .withPostConnectionSetup(() -> xMsgUtil.sleep(100))
                .build();

        uncheckedPool = xMsgConnectionPool.newBuilder()
                .withProxy(defaultProxy)
                .withPreConnectionSetup(s -> {
                    s.setRcvHWM(0);
                    s.setSndHWM(0);
                })
                .withPostConnectionSetup(() -> xMsgUtil.sleep(100))
                .checkConnection(false)
                .checkSubscription(false)
                .build();
    }

    @Override
    public void close() {
        mainPool.close();
        uncheckedPool.close();
    }
}
