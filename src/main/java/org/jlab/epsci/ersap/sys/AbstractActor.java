/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.MessageUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgSubscription;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;
import org.jlab.coda.xmsg.excp.xMsgException;

import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractActor {

    static final AtomicBoolean isShutDown = new AtomicBoolean(); // nocheck: ConstantName
    static final AtomicBoolean isFrontEnd = new AtomicBoolean(); // nocheck: ConstantName

    final ErsapBase base;

    private boolean running = false;
    private final Object lock = new Object();

    AbstractActor(ErsapComponent component, ErsapComponent fe) {
        this.base = new ErsapBase(component, fe);
    }

    public void start() throws ErsapException {
        synchronized (lock) {
            initialize();
            startMsg();
            running = true;
        }
    }

    public void stop() {
        synchronized (lock) {
            end();
            base.close();
            if (running) {
                running = false;
                stopMsg();
            }
        }
    }

    /**
     * Initializes the ERSAP actor.
     */
    abstract void initialize() throws ErsapException;

    /**
     * Runs before closing the actor.
     */
    abstract void end();

    abstract void startMsg();

    abstract void stopMsg();

    /**
     * Listens for messages of given topic published to the address of this component,
     * and registers as a subscriber with the front-end.
     *
     * @param topic topic of interest
     * @param callback the callback action
     * @param description a description for the registration
     * @return a handler to the subscription
     * @throws ErsapException if the subscription could not be started or
     *                        if the registration failed
     */
    xMsgSubscription startRegisteredSubscription(xMsgTopic topic,
                                                 xMsgCallBack callback,
                                                 String description) throws ErsapException {
        xMsgSubscription sub = base.listen(topic, callback);
        try {
            base.register(topic, description);
        } catch (Exception e) {
            base.unsubscribe(sub);
            throw e;
        }
        return sub;
    }

    void sendResponse(xMsgMessage msg, xMsgMeta.Status status, String data) {
        try {
            xMsgMessage repMsg = MessageUtil.buildRequest(msg.getReplyTopic(), data);
            repMsg.getMetaData().setAuthor(base.getName());
            repMsg.getMetaData().setStatus(status);
            base.send(repMsg);
        } catch (xMsgException e) {
            e.printStackTrace();
        }
    }


    static boolean shouldDeregister() {
        return !(isShutDown.get() && isFrontEnd.get());
    }

    static class WrappedException extends RuntimeException {

        private final ErsapException cause;

        WrappedException(ErsapException cause) {
            this.cause = cause;
        }

        @Override
        public ErsapException getCause() {
            return cause;
        }
    }

    static void throwWrapped(ErsapException t) {
        throw new WrappedException(t);
    }
}
