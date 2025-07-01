/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.coda.xmsg.core.xMsg;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;
import org.json.JSONObject;

public final class DpeReportTest {

    public static void main(String[] args) {
        xMsgProxyAddress dpeAddress = new xMsgProxyAddress("localhost");
        if (args.length > 0) {
            int port = Integer.parseInt(args[0]);
            dpeAddress = new xMsgProxyAddress("localhost", port);
        }
        xMsgTopic jsonTopic = xMsgTopic.build(ErsapConstants.DPE_REPORT);
        try (xMsg subscriber = new xMsg("report_subscriber")) {
            subscriber.subscribe(dpeAddress, jsonTopic, (msg) -> {
                try {
                    String data = new String(msg.getData());
                    String output = new JSONObject(data).toString(2);
                    System.out.println(output);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            xMsgUtil.keepAlive();
        } catch (xMsgException e) {
            e.printStackTrace();
        }
    }

    private DpeReportTest() { }
}
