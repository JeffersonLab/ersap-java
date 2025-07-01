/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMetaOrBuilder;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

final class RequestParser {

    private final xMsgMetaOrBuilder cmdMeta;
    private final String cmdData;
    private final StringTokenizer tokenizer;


    private RequestParser(xMsgMetaOrBuilder meta, String data) {
        cmdMeta = meta;
        cmdData = data;
        tokenizer = new StringTokenizer(cmdData, ErsapConstants.DATA_SEP);
    }

    static RequestParser build(xMsgMessage msg) throws RequestException {
        String mimeType = msg.getMimeType();
        if (mimeType.equals("text/string")) {
            return new RequestParser(msg.getMetaData(), new String(msg.getData()));
        }
        throw new RequestException("Invalid mime-type = " + mimeType);
    }

    public String nextString() throws RequestException {
        try {
            return tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new RequestException(invalidRequestMsg() + ": " + cmdData);
        }
    }

    public String nextString(String defaultValue) {
        return tokenizer.hasMoreElements() ? tokenizer.nextToken() : defaultValue;
    }

    public int nextInteger() throws RequestException {
        try {
            return Integer.parseInt(tokenizer.nextToken());
        } catch (NoSuchElementException | NumberFormatException e) {
            throw new RequestException(invalidRequestMsg() + ": " + cmdData);
        }
    }

    public String request() {
        return cmdData;
    }

    private String invalidRequestMsg() {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid request");
        if (cmdMeta.hasAuthor()) {
            sb.append(" from author = ").append(cmdMeta.getAuthor());
        }
        return sb.toString();
    }


    static class RequestException extends Exception {
        RequestException(String msg) {
            super(msg);
        }
    }
}
