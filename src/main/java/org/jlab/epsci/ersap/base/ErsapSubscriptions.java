/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.core.DataUtil;
import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.MessageUtil;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.util.ArgUtils;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgSubscription;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Subscriptions for running ERSAP components.
 */
public class ErsapSubscriptions {

    /**
     * Starts and stops a ERSAP subscription.
     *
     * @param <D> The specific subclass
     * @param <C> The user callback
     */
    abstract static class BaseSubscription<D extends BaseSubscription<D, C>, C> {

        final ErsapBase base;
        final ErsapComponent frontEnd;
        final xMsgTopic topic;

        final Map<String, xMsgSubscription> subscriptions;

        BaseSubscription(ErsapBase base,
                         Map<String, xMsgSubscription> subscriptions,
                         ErsapComponent frontEnd,
                         xMsgTopic topic) {
            this.base = base;
            this.frontEnd = frontEnd;
            this.topic = topic;
            this.subscriptions = subscriptions;
        }

        /**
         * A background thread is started to receive messages from the service.
         * Every time a report is received, the provided callback will be executed.
         * The messages are received sequentially, but the callback may run
         * in extra background threads, so it must be thread-safe.
         *
         * @param callback the callback to be executed for every received message
         * @throws ErsapException if the subscription failed to start
         */
        public void start(C callback) throws ErsapException {
            String key = frontEnd.getDpeHost() + ErsapConstants.MAPKEY_SEP + topic;
            if (subscriptions.containsKey(key)) {
                throw new IllegalStateException("duplicated subscription to: " + frontEnd);
            }
            xMsgCallBack wrapperCallback = wrap(callback);
            xMsgSubscription handler = base.listen(frontEnd, topic, wrapperCallback);
            subscriptions.put(key, handler);
        }

        public void stop() {
            String key = frontEnd.getDpeHost() + ErsapConstants.MAPKEY_SEP + topic;
            xMsgSubscription handler = subscriptions.remove(key);
            if (handler != null) {
                base.unsubscribe(handler);
            }
        }

        @SuppressWarnings("unchecked")
        D self() {
            return (D) this;
        }

        abstract xMsgCallBack wrap(C callback);
    }


    /**
     * A subscription to listen for service reports (data, done, status).
     */
    public static class ServiceSubscription
            extends BaseSubscription<ServiceSubscription, EngineCallback> {

        private Set<EngineDataType> dataTypes;

        ServiceSubscription(ErsapBase base,
                            Map<String, xMsgSubscription> subscriptions,
                            Set<EngineDataType> dataTypes,
                            ErsapComponent frontEnd,
                            xMsgTopic topic) {
            super(base, subscriptions, frontEnd, topic);
            this.dataTypes = dataTypes;
        }

        /**
         * Overwrites the data types used for deserializing the data from the
         * service.
         *
         * @param dataTypes the custom data-type of the service reports
         * @return this object, so methods can be chained
         */
        public ServiceSubscription withDataTypes(Set<EngineDataType> dataTypes) {
            this.dataTypes = dataTypes;
            return this;
        }

        /**
         * Overwrites the data types used for deserializing the data from the
         * service.
         *
         * @param dataTypes the custom data-type of the service reports
         * @return this object, so methods can be chained
         */
        public ServiceSubscription withDataTypes(EngineDataType... dataTypes) {
            Set<EngineDataType> newTypes = new HashSet<>();
            Collections.addAll(newTypes, dataTypes);
            this.dataTypes = newTypes;
            return this;
        }

        @Override
        xMsgCallBack wrap(final EngineCallback userCallback) {
            return msg -> {
                try {
                    userCallback.callback(DataUtil.deserialize(msg, dataTypes));
                } catch (ErsapException e) {
                    System.out.println("Error receiving data to " + msg.getTopic());
                    e.printStackTrace();
                }
            };
        }
    }


    /**
     * A subscription to listen for JSON reports from the DPEs.
     */
    public static class JsonReportSubscription
            extends BaseSubscription<JsonReportSubscription, GenericCallback> {

        JsonReportSubscription(ErsapBase base,
                               Map<String, xMsgSubscription> subscriptions,
                               ErsapComponent frontEnd,
                               xMsgTopic topic) {
            super(base, subscriptions, frontEnd, topic);
        }

        @Override
        xMsgCallBack wrap(final GenericCallback userCallback) {
            return msg -> {
                try {
                    String mimeType = msg.getMimeType();
                    if (mimeType.equals(EngineDataType.JSON.mimeType())) {
                        userCallback.callback(new String(msg.getData()));
                    } else {
                        throw new ErsapException("Unexpected mime-type: " + mimeType);
                    }
                } catch (ErsapException e) {
                    System.out.println("Error receiving data to " + msg.getTopic());
                    e.printStackTrace();
                }
            };
        }
    }


    /**
     * A subscription to listen for JSON reports from the DPEs.
     */
    public static class BaseDpeReportSubscription extends JsonReportSubscription {

        BaseDpeReportSubscription(ErsapBase base,
                                  Map<String, xMsgSubscription> subscriptions,
                                  ErsapComponent frontEnd, xMsgTopic topic) {
            super(base, subscriptions, frontEnd, topic);
        }

        /**
         * Parse the JSON report as {@link DpeRegistrationData} and
         * {@link DpeRuntimeData} objects.
         *
         * @return A subscription to listen for reports from the DPEs.
         */
        public DpeReportSubscription parseJson() {
            return new DpeReportSubscription(base, subscriptions, frontEnd, topic);
        }
    }


    /**
     * A subscription to listen for JSON reports from the DPEs.
     */
    public static class DpeReportSubscription
            extends BaseSubscription<DpeReportSubscription, DpeReportCallback> {

        DpeReportSubscription(ErsapBase base,
                              Map<String, xMsgSubscription> subscriptions,
                              ErsapComponent frontEnd,
                              xMsgTopic topic) {
            super(base, subscriptions, frontEnd, topic);
        }

        @Override
        xMsgCallBack wrap(final DpeReportCallback userCallback) {
            return msg -> {
                try {
                    String mimeType = msg.getMimeType();
                    if (mimeType.equals(EngineDataType.JSON.mimeType())) {
                        String source = new String(msg.getData());
                        JSONObject data = new JSONObject(source);
                        JSONObject regObj = data.getJSONObject(ErsapConstants.REGISTRATION_KEY);
                        JSONObject runObj = data.getJSONObject(ErsapConstants.RUNTIME_KEY);
                        userCallback.callback(new DpeRegistrationData(regObj),
                                              new DpeRuntimeData(runObj));
                    } else {
                        throw new ErsapException("Unexpected mime-type: " + mimeType);
                    }
                } catch (ErsapException e) {
                    System.out.println("Error receiving data to " + msg.getTopic());
                    e.printStackTrace();
                }
            };
        }
    }


    /**
     * Builds a subscription to listen the different ERSAP service reports.
     */
    public static class ServiceSubscriptionBuilder {
        private final ErsapBase base;
        private final Map<String, xMsgSubscription> subscriptions;
        private final Set<EngineDataType> dataTypes;
        private final ErsapComponent frontEnd;
        private final ErsapName component;

        ServiceSubscriptionBuilder(ErsapBase base,
                                   Map<String, xMsgSubscription> subscriptions,
                                   Set<EngineDataType> dataTypes,
                                   ErsapComponent frontEnd,
                                   ErsapName service) {
            this.base = base;
            this.subscriptions = subscriptions;
            this.dataTypes = dataTypes;
            this.frontEnd = frontEnd;
            this.component = service;
        }

        /**
         * A subscription to the specified status reports of the selected service.
         * <p>
         * Services will publish status reports after every execution that results
         * on error or warning.
         *
         * @param status the status to be listened
         * @return a service subscription to listen the given status
         */
        public ServiceSubscription status(EngineStatus status) {
            return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd,
                                           getTopic(status.toString(), component));
        }

        /**
         * A subscription to the "done" reports of the selected service.
         * <p>
         * Services will publish "done" reports if they are configured to do so
         * with a given event count. The messages will not contain the full
         * output result of the service, but just a few stats about the execution.
         *
         * @return a service subscription to listen "done" reports
         */
        public ServiceSubscription done() {
            return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd,
                                           getTopic(ErsapConstants.DONE, component));
        }

        /**
         * A subscription to the data reports of the selected service.
         * <p>
         * Services will publish "data" reports if they are configured to do so
         * with a given event count. The messages will contain the full
         * output result of the service.
         *
         * @return a service subscription to listen data reports
         */
        public ServiceSubscription data() {
            return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd,
                                           getTopic(ErsapConstants.DATA, component));
        }

        private xMsgTopic getTopic(String prefix, ErsapName service) {
            return MessageUtil.buildTopic(prefix, service.canonicalName());
        }
    }


    /**
     * Builds a subscription to listen the different ERSAP DPE reports.
     */
    public static class GlobalSubscriptionBuilder {
        private final ErsapBase base;
        private final Map<String, xMsgSubscription> subscriptions;
        private final Set<EngineDataType> dataTypes;
        private final ErsapComponent frontEnd;

        GlobalSubscriptionBuilder(ErsapBase base,
                                  Map<String, xMsgSubscription> subscriptions,
                                  Set<EngineDataType> dataTypes,
                                  ErsapComponent frontEnd) {
            this.base = base;
            this.subscriptions = subscriptions;
            this.dataTypes = dataTypes;
            this.frontEnd = frontEnd;
        }

        /**
         * A subscription to the periodic alive message reported by
         * all running DPEs.
         *
         * @return a subscription to listen DPE alive reports
         */
        public JsonReportSubscription aliveDpes() {
            xMsgTopic topic = MessageUtil.buildTopic(ErsapConstants.DPE_ALIVE, "");
            return new JsonReportSubscription(base, subscriptions, frontEnd, topic);
        }

        /**
         * A subscription to the periodic alive message reported by
         * the running DPEs with the given session.
         * <p>
         * If the session is empty, only DPEs with no session will be listened.
         *
         * @param session the session to select with DPEs to monitor
         * @return a subscription to listen DPE alive reports
         */
        public JsonReportSubscription aliveDpes(String session) {
            if (session == null) {
                throw new IllegalArgumentException("null session argument");
            }
            xMsgTopic topic = buildMatchingTopic(ErsapConstants.DPE_ALIVE, session);
            return new JsonReportSubscription(base, subscriptions, frontEnd, topic);
        }

        /**
         * A subscription to the periodic runtime and registration reports of
         * all running DPEs.
         *
         * @return a subscription to listen DPE reports
         */
        public BaseDpeReportSubscription dpeReport() {
            xMsgTopic topic = MessageUtil.buildTopic(ErsapConstants.DPE_REPORT, "");
            return new BaseDpeReportSubscription(base, subscriptions, frontEnd, topic);
        }

        /**
         * A subscription to the periodic runtime and registration reports of
         * the running DPEs with the given session.
         * <p>
         * If the session is empty, only DPEs with no session will be listened.
         *
         * @param session the session to select with DPEs to monitor
         * @return a subscription to listen DPE reports
         */
        public BaseDpeReportSubscription dpeReport(String session) {
            ArgUtils.requireNonNull(session, "session");
            xMsgTopic topic = buildMatchingTopic(ErsapConstants.DPE_REPORT, session);
            return new BaseDpeReportSubscription(base, subscriptions, frontEnd, topic);
        }

        /**
         * A subscription for all events published to the ERSAP data-ring.
         *
         * @return a subscription to listen all events in the data-ring
         */
        public ServiceSubscription dataRing() {
            xMsgTopic topic = MessageUtil.buildTopic(ErsapConstants.MONITOR_REPORT, "");
            return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd, topic);
        }

        /**
         * A subscription for events published with the given topic to the ERSAP
         * data-ring.
         *
         * @param ringTopic the data-ring topic to filter events
         * @return a subscription to listen events in the data-ring
         */
        public ServiceSubscription dataRing(DataRingTopic ringTopic) {
            ArgUtils.requireNonNull(ringTopic, "topic");
            xMsgTopic topic = buildMatchingTopic(ErsapConstants.MONITOR_REPORT, ringTopic.topic());
            return new ServiceSubscription(base, subscriptions, dataTypes, frontEnd, topic);
        }
    }


    private static xMsgTopic buildMatchingTopic(String prefix, String keyword) {
        if (keyword.endsWith("*")) {
            keyword = keyword.substring(0, keyword.length() - 1);
            return MessageUtil.buildTopic(prefix, keyword);
        }
        return MessageUtil.buildTopic(prefix, keyword, "");
    }
}
