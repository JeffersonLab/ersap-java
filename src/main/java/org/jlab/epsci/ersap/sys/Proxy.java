/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgContext;
import org.jlab.coda.xmsg.sys.xMsgProxy;

class Proxy {

    private final xMsgContext context;
    private final xMsgProxy proxy;

    Proxy(ErsapComponent dpe) throws ErsapException {
        try {
            context = xMsgContext.newContext();
            proxy = new xMsgProxy(context, dpe.getProxyAddress());
            if (System.getenv("XMSG_PROXY_DEBUG") != null) {
                proxy.verbose();
            }
        } catch (xMsgException e) {
            throw new ErsapException("Could not create proxy", e);
        }
    }

    public void start() {
        proxy.start();
        xMsgUtil.sleep(100);
    }

    public void stop() {
        context.destroy();
        proxy.shutdown();
    }
}
