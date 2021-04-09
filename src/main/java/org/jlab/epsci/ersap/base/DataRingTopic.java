/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.util.ArgUtils;
import org.jlab.coda.xmsg.core.xMsgConstants;

/**
 * The address of a ERSAP data-ring.
 */
public class DataRingTopic {

    private static final String ANY = xMsgConstants.ANY;

    private final String state;
    private final String session;
    private final String engine;

    /**
     * A topic to listen all events of the given state.
     *
     * @param state the output state of interest
     */
    public DataRingTopic(String state) {
        this(state, ANY, ANY);
    }

    /**
     * A topic to listen all events of the given state and session.
     *
     * @param state the output state of interest
     * @param session the data-processing session of interest
     */
    public DataRingTopic(String state, String session) {
        this(state, session, ANY);
    }

    /**
     * A topic to listen all events of the given state, session and engine.
     *
     * @param state the output state of interest
     * @param session the data-processing session of interest
     * @param engine the name of the engine of interest
     */
    public DataRingTopic(String state, String session, String engine) {
        this.state = ArgUtils.requireNonEmpty(state, "state");
        this.session = ArgUtils.requireNonNull(session, "session");
        this.engine = ArgUtils.requireNonEmpty(engine, "engine");

        if (state.equals(ANY)) {
            throw new IllegalArgumentException("state is not defined");
        }
        if (session.equals(ANY) && !engine.equals(ANY)) {
            throw new IllegalArgumentException("session is not defined");
        }
    }

    // checkstyle.off: Javadoc
    public String state() {
        return state;
    }

    public String session() {
        return session;
    }

    public String engine() {
        return engine;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + engine.hashCode();
        result = prime * result + session.hashCode();
        result = prime * result + state.hashCode();
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
        if (getClass() != obj.getClass()) {
            return false;
        }
        DataRingTopic other = (DataRingTopic) obj;
        if (!engine.equals(other.engine)) {
            return false;
        }
        if (!session.equals(other.session)) {
            return false;
        }
        if (!state.equals(other.state)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DataRingTopic[");
        sb.append("state='").append(state).append('\'');
        if (!session.equals(ANY)) {
            sb.append(", session='").append(session).append('\'');
        }
        if (!engine.equals(ANY)) {
            sb.append(", engine='").append(engine).append('\'');
        }
        sb.append(']');
        return sb.toString();
    }

    String topic() {
        StringBuilder sb = new StringBuilder();
        sb.append(state);
        if (!session.equals(ANY)) {
            sb.append(xMsgConstants.TOPIC_SEP).append(session);
        }
        if (!engine.equals(ANY)) {
            sb.append(xMsgConstants.TOPIC_SEP).append(engine);
            if (!engine.endsWith(ANY)) {
                sb.append('*');
            }
        }
        return sb.toString();
    }
}
