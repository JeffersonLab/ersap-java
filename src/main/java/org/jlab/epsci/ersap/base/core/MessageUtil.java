/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

import org.jlab.coda.xmsg.core.xMsgConstants;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgMimeType;

public final class MessageUtil {

    private MessageUtil() { }

    public static  xMsgTopic buildTopic(Object... args) {
        StringBuilder topic  = new StringBuilder();
        topic.append(args[0]);
        for (int i = 1; i < args.length; i++) {
            topic.append(xMsgConstants.TOPIC_SEP);
            topic.append(args[i]);
        }
        return xMsgTopic.wrap(topic.toString());
    }

    public static String buildData(Object... args) {
        StringBuilder topic  = new StringBuilder();
        topic.append(args[0]);
        for (int i = 1; i < args.length; i++) {
            topic.append(ErsapConstants.DATA_SEP);
            topic.append(args[i]);
        }
        return topic.toString();
    }

    public static xMsgMessage buildRequest(xMsgTopic topic, String data) {
        return new xMsgMessage(topic, xMsgMimeType.STRING, data.getBytes());
    }
}
