/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.DataRingTopic;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.util.ArgUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

final class CallbackInfo {

    private CallbackInfo() { }


    static class BaseCallbackInfo {

        final String classPath;

        BaseCallbackInfo(String classpath) {
            this.classPath = ArgUtils.requireNonEmpty(classpath, "classpath");
        }

        void tryClose(Object object) {
            if (object instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) object).close();
                } catch (Exception e) {
                    String msg = e.getMessage();
                    Logging.error("could not close instance of: " + classPath + ": " + msg);
                }
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + classPath.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof BaseCallbackInfo)) {
                return false;
            }
            BaseCallbackInfo other = (BaseCallbackInfo) obj;
            if (!classPath.equals(other.classPath)) {
                return false;
            }
            return true;
        }
    }


    static final class RingTopic {

        final String state;
        final String session;
        final String engine;

        RingTopic(String state, String session, String engine) {
            this.state = state;
            this.session = session;
            this.engine = engine;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((engine == null) ? 0 : engine.hashCode());
            result = prime * result + ((session == null) ? 0 : session.hashCode());
            result = prime * result + ((state == null) ? 0 : state.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof RingTopic)) {
                return false;
            }
            RingTopic other = (RingTopic) obj;
            if (engine == null) {
                if (other.engine != null) {
                    return false;
                }
            } else if (!engine.equals(other.engine)) {
                return false;
            }
            if (session == null) {
                if (other.session != null) {
                    return false;
                }
            } else if (!session.equals(other.session)) {
                return false;
            }
            if (state == null) {
                if (other.state != null) {
                    return false;
                }
            } else if (!state.equals(other.state)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "RingTopic [state=" + state + ", session=" + session + ", engine=" + engine
                    + "]";
        }
    }


    interface RingListener {

        void listen(@CheckForNull String session,
                    @Nonnull DpeReportHandler handler) throws ErsapException;

        void listen(@CheckForNull DataRingTopic topic,
                    @Nonnull EngineReportHandler handler) throws ErsapException;
    }


    static class RingCallbackInfo extends BaseCallbackInfo {

        final RingTopic topic;

        @Override
        public String toString() {
            return "RingCallbackInfo [topic=" + topic + ", classPath=" + classPath + "]";
        }

        RingCallbackInfo(String classpath, RingTopic topic) {
            super(classpath);
            this.topic = ArgUtils.requireNonNull(topic, "topic");
        }

        AutoCloseable loadCallback(RingListener listener) throws ErsapException {
            try {
                ClassLoader classLoader = getClass().getClassLoader();
                Class<?> klass = classLoader.loadClass(classPath);
                Object object = klass.newInstance();
                try {
                    if (object instanceof EngineReportHandler) {
                        listener.listen(getEngineReportTopic(), (EngineReportHandler) object);
                        return (AutoCloseable) object;
                    } else if (object instanceof DpeReportHandler) {
                        listener.listen(getDpeReportTopic(), (DpeReportHandler) object);
                        return (AutoCloseable) object;
                    } else {
                        throw new ErsapException("invalid monitoring class: " + classPath);
                    }
                } catch (Exception e) {
                    tryClose(object);
                    throw e;
                }
            } catch (ClassNotFoundException e) {
                throw new ErsapException("class not found: " + classPath);
            } catch (IllegalAccessException | InstantiationException e) {
                throw new ErsapException("could not create instance: " + classPath, e);
            }
        }

        DataRingTopic getEngineReportTopic() {
            if (topic.state == null) {
                return null;
            }
            if (topic.session == null) {
                return new DataRingTopic(topic.state);
            }
            if (topic.engine == null) {
                return new DataRingTopic(topic.state, topic.session);
            }
            return new DataRingTopic(topic.state, topic.session, topic.engine);
        }

        String getDpeReportTopic() {
            return topic.session;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + topic.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (!(obj instanceof RingCallbackInfo)) {
                return false;
            }
            RingCallbackInfo other = (RingCallbackInfo) obj;
            if (!topic.equals(other.topic)) {
                return false;
            }
            return true;
        }
    }
}
