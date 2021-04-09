/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

// checkstyle.off: ParameterNumber
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.coda.xmsg.core.xMsgConstants;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;

import java.text.MessageFormat;

/**
 *  ERSAP component. This is used to define
 *  service, container, DPE and orchestrator components.
 *
 *
 */
public final class ErsapComponent {

    /** DPE Name. */
    public static final String DPE_NAME_REGEX;
    /** Container Name. */
    public static final String CONTAINER_NAME_REGEX;
    /** Engine Name. */
    public static final String ENGINE_NAME_REGEX;
    /** Service Name. */
    public static final String SERVICE_NAME_REGEX;
    /** Service canonical name. */
    public static final String CANONICAL_NAME_REGEX;
    /** Name separator. */
    private static final String NAME_SEP = xMsgTopic.SEPARATOR;

    static {
        final String ip = "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}";
        final String port = "[0-9]+";
        final String lang = "(?:"
                + ErsapConstants.JAVA_LANG + "|"
                + ErsapConstants.CPP_LANG + "|"
                + ErsapConstants.PYTHON_LANG + ")";

        final String portPart = ErsapConstants.PORT_SEP + port;
        final String langPart = ErsapConstants.LANG_SEP + lang;

        DPE_NAME_REGEX = ip + "(?:" + portPart + ")?" + langPart;
        CONTAINER_NAME_REGEX = "[\\w-]+";
        ENGINE_NAME_REGEX = "\\w+";

        SERVICE_NAME_REGEX = MessageFormat.format("{1}{0}{2}{0}{3}",
                NAME_SEP, DPE_NAME_REGEX, CONTAINER_NAME_REGEX, ENGINE_NAME_REGEX);

        CANONICAL_NAME_REGEX = MessageFormat.format("({1})(?:{0}({2})(?:{0}({3}))?)?",
                NAME_SEP, DPE_NAME_REGEX, CONTAINER_NAME_REGEX, ENGINE_NAME_REGEX);
    }

    private xMsgTopic topic;

    private String dpeLang;
    private String dpeHost;
    private int dpePort;

    private String dpeCanonicalName;
    private String containerName;
    private String engineName;
    private String engineClass = ErsapConstants.UNDEFINED;

    private String canonicalName;
    private String description;
    private String initialState;

    private int subscriptionPoolSize;

    private boolean isOrchestrator = false;
    private boolean isDpe = false;
    private boolean isContainer = false;
    private boolean isService = false;

    private ErsapComponent(String dpeLang, String dpeHost, int dpePort,
                           String container, String engine, String engineClass,
                           int subscriptionPoolSize, String description,
                           String initialState) {
        this.dpeLang = dpeLang;
        this.subscriptionPoolSize = subscriptionPoolSize;
        this.dpeHost = dpeHost;
        this.dpePort = dpePort;
        if (dpePort == ErsapUtil.getDefaultPort(dpeLang)) {
            this.dpeCanonicalName = dpeHost + ErsapConstants.LANG_SEP + dpeLang;
        } else {
            this.dpeCanonicalName = dpeHost + ErsapConstants.PORT_SEP
                                  + dpePort + ErsapConstants.LANG_SEP
                                  + dpeLang;
        }
        this.containerName = container;
        this.engineName = engine;
        this.engineClass = engineClass;
        if (engine != null && !engine.equalsIgnoreCase(xMsgConstants.ANY)) {
            topic = xMsgTopic.build(dpeCanonicalName, containerName, engineName);
            canonicalName = topic.toString();
        } else if (container != null && !container.equalsIgnoreCase(xMsgConstants.ANY)) {
            topic = xMsgTopic.build(ErsapConstants.CONTAINER, dpeCanonicalName, containerName);
            canonicalName = xMsgTopic.build(dpeCanonicalName, containerName).toString();
        } else {
            topic = xMsgTopic.build(ErsapConstants.DPE, dpeCanonicalName);
            canonicalName = xMsgTopic.build(dpeCanonicalName).toString();
        }
        this.description = description;
        this.initialState = initialState;
    }


    /**
     * Creates and returns ERSAP orchestrator.
     *
     * @param name                 of the orchestrator
     * @param dpeHost              host of the PDE to communicate with
     * @param dpePort              port of the DPE to communicate with
     * @param dpeLang              language of the DPE
     * @param subscriptionPoolSize pool size for the
     *                             orchestrator to be used for subscriptions
     * @param description          textual description of this orchestrator
     * @return orchestrator {@link ErsapComponent} object
     */
    public static ErsapComponent orchestrator(String name,
                                              String dpeHost,
                                              int dpePort,
                                              String dpeLang,
                                              int subscriptionPoolSize,
                                              String description) {
        ErsapComponent a = new ErsapComponent(dpeLang,
                dpeHost,
                dpePort,
                xMsgTopic.ANY,
                xMsgTopic.ANY,
                ErsapConstants.UNDEFINED,
                subscriptionPoolSize,
                description,
                ErsapConstants.UNDEFINED);
        a.setCanonicalName(name);
        a.isOrchestrator = true;
        return a;
    }

    /**
     * Creates and returns ERSAP orchestrator. Uses default DPE port.
     *
     * @param name                 of the orchestrator
     * @param dpeHost              host of the PDE to communicate with
     * @param dpeLang              language of the DPE
     * @param subscriptionPoolSize pool size for the
     *                             orchestrator to be used for subscriptions
     * @param description          textual description of this orchestrator
     * @return the orchestrator component
     */
    public static ErsapComponent orchestrator(String name, String dpeHost, String dpeLang,
                                              int subscriptionPoolSize, String description) {
        return orchestrator(name,
                            dpeHost, ErsapUtil.getDefaultPort(dpeLang), dpeLang,
                            subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP orchestrator. Default port of the DP and Java lang is used.
     *
     * @param name                 of the orchestrator
     * @param dpeHost              host of the PDE to communicate with
     * @param subscriptionPoolSize pool size for the
     *                             orchestrator to be used for subscriptions
     * @param description          textual description of this orchestrator
     * @return the orchestrator component
     */
    public static ErsapComponent orchestrator(String name, String dpeHost,
                                              int subscriptionPoolSize, String description) {
        return orchestrator(name,
                            dpeHost, ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                            subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP orchestrator. DPE on the local host, with
     * the default port and Java lang is used.
     *
     * @param name of the orchestrator
     * @param subscriptionPoolSize pool size for the
     *                             orchestrator to be used for subscriptions
     * @param description textual description of this orchestrator
     * @return the orchestrator component
     */
    public static ErsapComponent orchestrator(String name,
                                              int subscriptionPoolSize,
                                              String description) {
        return orchestrator(name,
                            ErsapUtil.localhost(),
                            ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                            subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP DPE component.
     *
     * @param dpeHost              host where the DPE will run
     * @param dpePort              port of the DPE will use
     * @param dpeLang              language of the DPE
     * @param subscriptionPoolSize pool size for the
     *                             DPE to be used for subscriptions
     * @param description          textual description of the DPE
     * @return the DPE component
     */
    public static ErsapComponent dpe(String dpeHost, int dpePort, String dpeLang,
                                     int subscriptionPoolSize, String description) {
        ErsapComponent a = new ErsapComponent(dpeLang,
                dpeHost,
                dpePort,
                xMsgTopic.ANY,
                xMsgTopic.ANY,
                ErsapConstants.UNDEFINED,
                subscriptionPoolSize, description,
                ErsapConstants.UNDEFINED);
        a.isDpe = true;
        return a;
    }

    /**
     * Creates and returns ERSAP DPE component. The default DPE port is used.
     *
     * @param dpeHost host where the DPE will run
     * @param dpeLang language of the DPE
     * @param subscriptionPoolSize pool size for the
     *                             DPE to be used for subscriptions
     * @param description textual description of the DPE
     * @return the DPE component
     */
    public static ErsapComponent dpe(String dpeHost, String dpeLang,
                                     int subscriptionPoolSize, String description) {
        return dpe(dpeHost, ErsapUtil.getDefaultPort(dpeLang), dpeLang,
                   subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP DPE component. The default DPE port and Java lang is used.
     *
     * @param dpeHost host where the DPE will run
     * @param subscriptionPoolSize pool size for the
     *                             DPE to be used for subscriptions
     * @param description textual description of the DPE
     * @return the DPE component
     */
    public static ErsapComponent dpe(String dpeHost,
                                     int subscriptionPoolSize,
                                     String description) {
        return dpe(dpeHost, ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                   subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP DPE component.
     * The local host, default DPE port and Java lang is used.
     *
     * @param subscriptionPoolSize pool size for the
     *                             DPE to be used for subscriptions
     * @param description textual description of the DPE
     * @return the DPE component
     */
    public static ErsapComponent dpe(int subscriptionPoolSize, String description) {
        return dpe(ErsapUtil.localhost(), ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                   subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP DPE component.
     * DPE default settings are used
     *
     * @return the DPE component
     */
    public static ErsapComponent dpe() {
        return dpe(ErsapUtil.localhost(), ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                   1, ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP DPE component from the ERSAP component
     * canonical name.. DPE default pool-size = 1 is used.
     *
     * @param canonicalName The canonical name of a component
     * @param description textual description of the DPE
     * @return the DPE component
     */
    public static ErsapComponent dpe(String canonicalName, String description) {
        if (!ErsapUtil.isCanonicalName(canonicalName)) {
            throw new IllegalArgumentException("Not a canonical name: " + canonicalName);
        }
        return dpe(ErsapUtil.getDpeHost(canonicalName),
                   ErsapUtil.getDpePort(canonicalName),
                   ErsapUtil.getDpeLang(canonicalName),
                   1, description);
    }

    /**
     * Creates and returns ERSAP DPE component from the ERSAP component
     * canonical name.. DPE default pool-size = 1 is used, leaving description
     * of the DPE undefined.
     *
     * @param canonicalName The canonical name of a component
     * @return the DPE component
     */
    public static ErsapComponent dpe(String canonicalName) {
        return dpe(canonicalName, ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Container component.
     *
     * @param dpeHost              host of the DPE where container is/(will be) deployed
     * @param dpePort              port of the DPE where container is/(will be) deployed
     * @param dpeLang              language of the DPE
     * @param container            the name of the container
     * @param subscriptionPoolSize pool size for the
     *                             container to be used for subscriptions
     * @param description          textual description of the container
     * @return the container component
     */
    public static ErsapComponent container(String dpeHost, int dpePort, String dpeLang,
                                           String container,
                                           int subscriptionPoolSize, String description) {
        ErsapComponent a = new ErsapComponent(dpeLang,
                dpeHost,
                dpePort,
                container,
                xMsgTopic.ANY,
                ErsapConstants.UNDEFINED,
                subscriptionPoolSize, description,
                ErsapConstants.UNDEFINED);
        a.isContainer = true;
        return a;
    }

    /**
     * Creates and returns ERSAP Container component. The default DPE port is used.
     *
     * @param dpeHost host of the DPE where container is/(will be) deployed
     * @param dpeLang language of the DPE
     * @param container the name of the container
     * @param subscriptionPoolSize pool size for the
     *                             container to be used for subscriptions
     * @param description textual description of the container
     * @return the container component
     */
    public static ErsapComponent container(String dpeHost, String dpeLang,
                                           String container, int subscriptionPoolSize,
                                           String description) {
        return container(dpeHost, ErsapUtil.getDefaultPort(dpeLang), dpeLang,
                         container, subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP Container component. The default DPE port and Java lang is used.
     *
     * @param dpeHost host of the DPE where container is/(will be) deployed
     * @param container the name of the container
     * @param subscriptionPoolSize pool size for the
     *                             container to be used for subscriptions
     * @param description textual description of the container
     * @return the container component
     */
    public static ErsapComponent container(String dpeHost, String container,
                                           int subscriptionPoolSize, String description) {
        return container(dpeHost, ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                         container, subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP Container component. The DPE running on a local host,
     * default DPE port and Java lang is used.
     *
     * @param container the name of the container
     * @param subscriptionPoolSize pool size for the
     *                             container to be used for subscriptions
     * @param description textual description of the container
     * @return the container component
     */
    public static ErsapComponent container(String container,
                                           int subscriptionPoolSize,
                                           String description) {
        return container(ErsapUtil.localhost(),
                         ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                         container,
                         subscriptionPoolSize, description);
    }

    /**
     * Creates and returns ERSAP Container component, using the container canonical name.
     * Default subscriptions pool-size = 1 is used.
     *
     * @param containerCanonicalName the canonical name of the container
     * @param description textual description of the container
     * @return the container component
     */
    public static ErsapComponent container(String containerCanonicalName, String description) {
        if (!ErsapUtil.isCanonicalName(containerCanonicalName)) {
            throw new IllegalArgumentException("Not a canonical name: " + containerCanonicalName);
        }
        return container(ErsapUtil.getDpeHost(containerCanonicalName),
                         ErsapUtil.getDpePort(containerCanonicalName),
                         ErsapUtil.getDpeLang(containerCanonicalName),
                         ErsapUtil.getContainerName(containerCanonicalName),
                         xMsgConstants.DEFAULT_POOL_SIZE,
                         description);
    }

    /**
     * Creates and returns ERSAP Container component, using the container canonical name.
     * Default subscriptions pool-size = 1 is used.
     *
     * @param containerCanonicalName the canonical name of the container
     * @return the container component
     */
    public static ErsapComponent container(String containerCanonicalName) {
        return container(containerCanonicalName, ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Service component.
     *
     * @param dpeHost host of the DPE where service is/(will be) deployed
     * @param dpePort port of the DPE where service is/(will be) deployed
     * @param dpeLang language of the DPE
     * @param container the name of the container of the service
     * @param engine the name of the service engine
     * @param engineClass engine full class name (package name)
     * @param subscriptionPoolSize pool size for the
     *                             service to be used for subscriptions
     * @param description textual description of the service
     * @param initialState the initial state of the service
     * @return the service component
     */
    public static ErsapComponent service(String dpeHost, int dpePort, String dpeLang,
                                         String container, String engine, String engineClass,
                                         int subscriptionPoolSize, String description,
                                         String initialState) {
        ErsapComponent a = new ErsapComponent(dpeLang,
                dpeHost,
                dpePort,
                container,
                engine,
                engineClass,
                subscriptionPoolSize,
                description,
                initialState);
        a.isService = true;
        return a;
    }

    /**
     * Creates and returns ERSAP Service component. Default pool-size=1 is used.
     * The description of the service is undefined.
     *
     * @param dpeHost host of the DPE where service is/(will be) deployed
     * @param dpePort port of the DPE where service is/(will be) deployed
     * @param dpeLang language of the DPE
     * @param container the name of the container of the service
     * @param engine the name of the service engine
     * @return the service component
     */
    public static ErsapComponent service(String dpeHost, int dpePort, String dpeLang,
                                         String container, String engine) {
        return service(dpeHost, dpePort, dpeLang,
                       container, engine, ErsapConstants.UNDEFINED,
                       1, ErsapConstants.UNDEFINED,
                       ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Service component.
     * DPE default port and default pool-size=1 is used.
     * The description of the service is undefined.
     *
     * @param dpeHost host of the DPE where service is/(will be) deployed
     * @param dpeLang language of the DPE
     * @param container the name of the container of the service
     * @param engine the name of the service engine
     * @return the service component
     */
    public static ErsapComponent service(String dpeHost, String dpeLang,
                                         String container, String engine) {
        return service(dpeHost, ErsapUtil.getDefaultPort(dpeLang), dpeLang,
                       container, engine, ErsapConstants.UNDEFINED,
                       1, ErsapConstants.UNDEFINED,
                       ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Service component. DPE running on a local host with the
     * default port and default pool-size=1 is used. The description of the service is undefined.
     *
     * @param container the name of the container of the service
     * @param engine the name of the service engine
     * @return the service component
     */
    public static ErsapComponent service(String container, String engine) {
        return service(ErsapUtil.localhost(), ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                       container, engine, ErsapConstants.UNDEFINED,
                       1, ErsapConstants.UNDEFINED,
                       ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Service component. DPE running on a local host with the
     * default port used. The description of the service is undefined.
     *
     * @param container the name of the container of the service
     * @param engine the name of the service engine
     * @param poolSize pool size for the service subscriptions
     * @return the service component
     */
    public static ErsapComponent service(String container, String engine, int poolSize) {
        return service(ErsapUtil.localhost(), ErsapConstants.JAVA_PORT, ErsapConstants.JAVA_LANG,
                       container, engine, ErsapConstants.UNDEFINED,
                       poolSize, ErsapConstants.UNDEFINED,
                       ErsapConstants.UNDEFINED);
    }

    /**
     * Creates and returns ERSAP Service component, using the service canonical name.
     * Default subscriptions pool-size = 1 is used. The description of the service is undefined.
     * @param serviceCanonicalName canonical name of the service
     * @return the service component
     */
    public static ErsapComponent service(String serviceCanonicalName) {
        if (!ErsapUtil.isCanonicalName(serviceCanonicalName)) {
            throw new IllegalArgumentException("Not a canonical name: " + serviceCanonicalName);
        }
        return service(ErsapUtil.getDpeHost(serviceCanonicalName),
                       ErsapUtil.getDpePort(serviceCanonicalName),
                       ErsapUtil.getDpeLang(serviceCanonicalName),
                       ErsapUtil.getContainerName(serviceCanonicalName),
                       ErsapUtil.getEngineName(serviceCanonicalName),
                       ErsapConstants.UNDEFINED,
                       xMsgConstants.DEFAULT_POOL_SIZE, ErsapConstants.UNDEFINED,
                       ErsapConstants.UNDEFINED);
    }

    /**
     * Returns the topic of the ERSAP component, i.e. the topic of the xMsg subscriber.
     * Note that all ERSAP components are registered as xMsg subscribers.
     *
     * @return {@link org.jlab.coda.xmsg.core.xMsgTopic} object
     */
    public xMsgTopic getTopic() {
        return topic;
    }

    public String getDpeLang() {
        return dpeLang;
    }

    public String getDpeHost() {
        return dpeHost;
    }

    public int getDpePort() {
        return dpePort;
    }

    /**
     * Returns DPE canonical, constructed as "dpeHost % dpePort _ dpeLang".
     *
     * @return DPE canonical name
     */
    public String getDpeCanonicalName() {
        return dpeCanonicalName;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getEngineName() {
        return engineName;
    }

    public String getEngineClass() {
        return engineClass;
    }

    /**
     * Sets the engine class which is the package name of the class.
     *
     * @param engineClass package name of the class
     */
    public void setEngineClass(String engineClass) {
        this.engineClass = engineClass;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    /**
     * Note. candidate to be deprecated. Do not use to define the
     * canonical names for DPE, container or service.
     *
     * The canonical name of a ERSAP component is defined internally,
     * yet this method is used to set the name of an orchestrator, which
     * considers to be non critical.
     *
     * @param canonicalName canonical name
     */
    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public int getSubscriptionPoolSize() {
        return subscriptionPoolSize;
    }

    /**
     * Sets the subscription pool-size of the component.
     *
     * @param subscriptionPoolSize pool size
     */
    public void setSubscriptionPoolSize(int subscriptionPoolSize) {
        this.subscriptionPoolSize = subscriptionPoolSize;
    }

    public boolean isOrchestrator() {
        return isOrchestrator;
    }

    public boolean isDpe() {
        return isDpe;
    }

    public boolean isContainer() {
        return isContainer;
    }

    public boolean isService() {
        return isService;
    }

    /**
     * Returns the DPE proxy address.
     *
     * @return {@link org.jlab.coda.xmsg.net.xMsgProxyAddress} object
     */
    public xMsgProxyAddress getProxyAddress() {
        return new xMsgProxyAddress(getDpeHost(), getDpePort());
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInitialState() {
        return initialState;
    }

    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }

    @Override
    public String toString() {
        return canonicalName;
    }
}
