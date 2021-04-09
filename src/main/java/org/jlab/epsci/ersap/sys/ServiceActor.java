/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.coda.xmsg.core.xMsgConnection;
import org.jlab.coda.xmsg.core.xMsgConnectionPool;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;

class ServiceActor {

    private final ErsapBase base;
    private final ConnectionPools connectionPools;

    ServiceActor(ErsapComponent me, ErsapComponent frontEnd, ConnectionPools connectionPools) {
        this.base = new ErsapBase(me, frontEnd);
        this.connectionPools = connectionPools;
    }

    public void close() {
        base.close();
    }

    public void start() throws ErsapException {
        base.cacheLocalConnection();
    }

    public void send(xMsgMessage msg) throws ErsapException {
        sendMsg(connectionPools.mainPool, getLocal(), msg);
    }

    public void send(xMsgProxyAddress address, xMsgMessage msg) throws ErsapException {
        sendMsg(connectionPools.mainPool, address, msg);
    }

    public void sendUncheck(xMsgMessage msg) throws ErsapException {
        sendMsg(connectionPools.uncheckedPool, getLocal(), msg);
    }

    public void sendUncheck(xMsgProxyAddress address, xMsgMessage msg) throws ErsapException {
        sendMsg(connectionPools.uncheckedPool, address, msg);
    }

    private void sendMsg(xMsgConnectionPool pool, xMsgProxyAddress address, xMsgMessage msg)
            throws ErsapException {
        try (xMsgConnection con = pool.getConnection(address)) {
            base.send(con, msg);
        } catch (xMsgException e) {
            throw new ErsapException("Could not send message", e);
        }
    }

    public String getName() {
        return base.getName();
    }

    public String getEngine() {
        return base.getMe().getEngineName();
    }

    public xMsgProxyAddress getLocal() {
        return base.getDefaultProxyAddress();
    }

    public xMsgProxyAddress getFrontEnd() {
        return base.getFrontEnd().getProxyAddress();
    }
}
