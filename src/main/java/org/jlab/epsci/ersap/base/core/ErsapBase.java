/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.util.EnvUtils;
import org.jlab.epsci.ersap.util.report.ReportType;
import org.jlab.coda.xmsg.core.xMsg;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgConnection;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgSetup;
import org.jlab.coda.xmsg.core.xMsgSubscription;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.data.xMsgRegInfo;
import org.jlab.coda.xmsg.data.xMsgRegQuery;
import org.jlab.coda.xmsg.data.xMsgRegRecord;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgRegAddress;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 *  ERSAP base class providing methods build services,
 *  service container and orchestrator.
 *
 */
public class ErsapBase extends xMsg {

    private final String ersapHome;
    // reference to this component description
    private final ErsapComponent me;

    // reference to the front end DPE
    private ErsapComponent frontEnd;

    /**
     * A ERSAP component that can send and receives messages.
     *
     * @param me        definition of the component
     * @param frontEnd  definition of the front-end
     */
    public ErsapBase(ErsapComponent me, ErsapComponent frontEnd) {
        super(me.getCanonicalName(), setup(me, frontEnd));
        this.me = me;
        this.frontEnd = frontEnd;
        this.ersapHome = EnvUtils.ersapHome();
    }

    private static xMsgSetup setup(ErsapComponent me, ErsapComponent frontEnd) {
        xMsgSetup.Builder builder = xMsgSetup.newBuilder()
                        .withProxy(me.getProxyAddress())
                        .withRegistrar(getRegAddress(frontEnd))
                        .withPoolSize(me.getSubscriptionPoolSize())
                        .withPreConnectionSetup(s -> {
                            s.setRcvHWM(0);
                            s.setSndHWM(0);
                        })
                        .withPostConnectionSetup(() -> xMsgUtil.sleep(100));
        if (me.isOrchestrator()) {
            builder.checkSubscription(false);
        }
        return builder.build();
    }

    /**
     * Get ERSAP_HOME env variable.
     * @return the path to the ersap_home defined
     */
    public String getErsapHome() {
        return ersapHome;
    }

    /**
     * Returns the definition of this component.
     * @return ErsapComponent
     */
    public ErsapComponent getMe() {
        return me;
    }

    /**
     * Returns the description of this component.
     * @return ErsapComponent description
     */
    public String getDescription() {
        return me.getDescription();
    }

    /**
     * Stores a connection to the default proxy in the connection pool.
     *
     * @throws ErsapException if a connection could not be created or connected
     */
    public void cacheLocalConnection() throws ErsapException {
        try {
            cacheConnection();
        } catch (xMsgException e) {
            throw new ErsapException("could not connect to local proxy", e);
        }
    }

    /**
     * Sends a message to the address of the given ERSAP component.
     *
     * @param component the component that shall receive the message
     * @param msg the message to be published
     * @throws xMsgException if the message could not be sent
     */
    public void send(ErsapComponent component, xMsgMessage msg)
            throws xMsgException {
        msg.getMetaData().setSender(myName);
        publish(component.getProxyAddress(), msg);
    }

    /**
     * Sends a string to the given ERSAP component.
     *
     * @param component the component that shall receive the message
     * @param requestText string of the message
     * @throws xMsgException if the message could not be sent
     */
    public void send(ErsapComponent component, String requestText)
            throws xMsgException {
        xMsgMessage msg = MessageUtil.buildRequest(component.getTopic(), requestText);
        send(component, msg);
    }

    /**
     * Sends a message using the specified connection.
     *
     * @param con the connection that shall be used to publish the message
     * @param msg the message to be published
     * @throws xMsgException if the message could not be sent
     */
    public void send(xMsgConnection con, xMsgMessage msg)
            throws xMsgException {
        msg.getMetaData().setSender(myName);
        publish(con, msg);
    }

    /**
     * Sends a message to the address of this ERSAP component.
     *
     * @param msg the message to be published
     * @throws xMsgException if the message could not be sent
     */
    public void send(xMsgMessage msg)
            throws xMsgException {
        send(me, msg);
    }

    /**
     * Sends a text message to this ERSAP component.
     *
     * @param msgText string of the message
     * @throws xMsgException if the message could not be sent
     */
    public void send(String msgText)
            throws xMsgException {
        send(me, msgText);
    }

    /**
     * Synchronous sends a message to the address of the given ERSAP component.
     *
     * @param component the component that shall receive the message
     * @param msg the message to be published
     * @param timeout in milliseconds
     * @throws xMsgException if the message could not be sent
     * @throws TimeoutException if a response was not received
     * @return xMsgMessage
     */
    public xMsgMessage syncSend(ErsapComponent component, xMsgMessage msg, long timeout)
            throws xMsgException, TimeoutException {
        msg.getMetaData().setSender(myName);
        return syncPublish(component.getProxyAddress(), msg, timeout);
    }

    /**
     * Synchronous sends a string to the given ERSAP component.
     *
     * @param component the component that shall receive the message
     * @param requestText string of the message
     * @param timeout in milli seconds
     * @throws xMsgException if the message could not be sent
     * @throws TimeoutException if a response was not received
     * @return xMsgMessage
     */
    public xMsgMessage syncSend(ErsapComponent component, String requestText, long timeout)
            throws xMsgException, TimeoutException {
        xMsgMessage msg = MessageUtil.buildRequest(component.getTopic(), requestText);
        return syncSend(component, msg, timeout);
    }

    /**
     * Listens for messages published to the given component.
     *
     * @param component a component defining the topic of interest
     * @param callback the callback action
     * @return a handler to the subscription
     * @throws ErsapException if the subscription could not be started
     */
    public xMsgSubscription listen(ErsapComponent component, xMsgCallBack callback)
            throws ErsapException {
        return listen(me, component.getTopic(), callback);
    }

    /**
     * Listens for messages of given topic published to the address of the given
     * component.
     *
     * @param component a component defining the address to connect
     * @param topic topic of interest
     * @param callback the callback action
     * @return a handler to the subscription
     * @throws ErsapException if the subscription could not be started
     */
    public xMsgSubscription listen(ErsapComponent component, xMsgTopic topic, xMsgCallBack callback)
            throws ErsapException {
        try {
            return subscribe(component.getProxyAddress(), topic, callback);
        } catch (xMsgException e) {
            throw new ErsapException("could not subscribe to " + topic);
        }
    }

    /**
     * Listens for messages of given topic published to the address of this
     * component.
     *
     * @param topic topic of interest
     * @param callback the callback action
     * @return a handler to the subscription
     * @throws ErsapException if the subscription could not be started
     */
    public xMsgSubscription listen(xMsgTopic topic, xMsgCallBack callback)
            throws ErsapException {
        return listen(me, topic, callback);
    }

    /**
     * Stops listening to a subscription defined by the handler.
     *
     * @param handle the subscription handler
     */
    public void stopListening(xMsgSubscription handle) {
        unsubscribe(handle);
    }

    /**
     * Terminates all callbacks.
     */
    public void stopCallbacks() {
        terminateCallbacks();
    }

    /**
     * Registers this component with the front-end as subscriber to the given topic.
     *
     * @param topic the subscribed topic
     * @param description a description of the component
     * @throws ErsapException if registration failed
     */
    public void register(xMsgTopic topic, String description) throws ErsapException {
        xMsgRegAddress regAddress = getRegAddress(frontEnd);
        try {
            register(xMsgRegInfo.subscriber(topic, description), regAddress);
        } catch (xMsgException e) {
            throw new ErsapException("could not register with front-end = " + regAddress, e);
        }
    }

    /**
     * Remove the registration of this component from the front-end as
     * subscriber to the given topic.
     *
     * @param topic the subscribed topic
     * @throws ErsapException if removing the registration failed
     */
    public void removeRegistration(xMsgTopic topic) throws ErsapException {
        xMsgRegAddress regAddress = getRegAddress(frontEnd);
        try {
            deregister(xMsgRegInfo.subscriber(topic), regAddress);
        } catch (xMsgException e) {
            throw new ErsapException("could not deregister from front-end = " + regAddress, e);
        }
    }

    /**
     * Retrieves ERSAP actor registration information from the xMsg registrar service.
     *
     * @param regHost registrar server host
     * @param regPort registrar server port
     * @param topic   the canonical name of an actor: {@link org.jlab.coda.xmsg.core.xMsgTopic}
     * @return set of {@link org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration} objects
     * @throws IOException exception
     * @throws xMsgException exception
     */
    public Set<xMsgRegRecord> discover(String regHost, int regPort, xMsgTopic topic)
            throws IOException, xMsgException {
        xMsgRegAddress regAddress = new xMsgRegAddress(regHost, regPort);
        return discover(xMsgRegQuery.subscribers(topic), regAddress, 1000);
    }

    /**
     * Retrieves ERSAP actor registration information from the xMsg registrar service,
     * assuming registrar is running using the default port.
     *
     * @param regHost registrar server host
     * @param topic   the canonical name of an actor: {@link org.jlab.coda.xmsg.core.xMsgTopic}
     * @return set of {@link org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration} objects
     * @throws IOException exception
     * @throws xMsgException exception
     */
    public Set<xMsgRegRecord> discover(String regHost, xMsgTopic topic)
            throws IOException, xMsgException {
        xMsgRegAddress regAddress = new xMsgRegAddress(regHost);
        return discover(xMsgRegQuery.subscribers(topic), regAddress);
    }

    /**
     * Retrieves ERSAP actor registration information from the xMsg registrar service,
     * assuming registrar is running on a local host, using the default port.
     *
     * @param topic the canonical name of an actor: {@link org.jlab.coda.xmsg.core.xMsgTopic}
     * @return set of {@link org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration} objects
     * @throws IOException exception
     * @throws xMsgException exception
     */
    public Set<xMsgRegRecord> discover(xMsgTopic topic)
            throws IOException, xMsgException {
        return discover(xMsgRegQuery.subscribers(topic));
    }

    public static xMsgRegAddress getRegAddress(ErsapComponent fe) {
        return new xMsgRegAddress(fe.getDpeHost(), fe.getDpePort() + ErsapConstants.REG_PORT_SHIFT);
    }

    /**
     * Sync asks DPE to report.
     *
     * @param component dpe as a {@link ErsapComponent#dpe()} object
     * @param timeout sync request timeout
     * @return message {@link org.jlab.coda.xmsg.core.xMsgMessage}
     *         back from a dpe.
     * @throws IOException exception
     * @throws xMsgException exception
     * @throws TimeoutException exception
     */
    public xMsgMessage pingDpe(ErsapComponent component, int timeout)
            throws IOException, xMsgException, TimeoutException {

        if (component.isDpe()) {
            String data = MessageUtil.buildData(ReportType.INFO.getValue());
            xMsgTopic topic = component.getTopic();
            xMsgMessage msg = MessageUtil.buildRequest(topic, data);
            return syncSend(component, msg, timeout);
        }
        return null;
    }


    /**
     * Returns the reference to the front-end DPE.
     *
     * @return {@link ErsapComponent} object
     */
    public ErsapComponent getFrontEnd() {
        return frontEnd;
    }

    /**
     * Sets a DPE ERSAP component as a front-end.
     *
     * @param frontEnd {@link ErsapComponent} object
     */
    public void setFrontEnd(ErsapComponent frontEnd) {
        this.frontEnd = frontEnd;
    }
}
