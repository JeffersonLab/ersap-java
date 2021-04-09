/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.sys.RequestParser.RequestException;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta.Builder;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgContext;
import org.jlab.coda.xmsg.sys.xMsgRegistrar;

class FrontEnd {

    private final ErsapBase base;

    private final xMsgContext context;
    private final xMsgRegistrar registrar;

    FrontEnd(ErsapComponent frontEnd)
            throws ErsapException {
        try {
            // create the xMsg registrar
            context = xMsgContext.newContext();
            registrar = new xMsgRegistrar(context, ErsapBase.getRegAddress(frontEnd));

            // create the xMsg actor
            base = new ErsapBase(frontEnd, frontEnd);
        } catch (xMsgException e) {
            throw new ErsapException("Could not create front-end", e);
        }
    }


    public void start() throws ErsapException {
        // start registrar service
        registrar.start();

        // subscribe to forwarding requests
        xMsgTopic topic = xMsgTopic.build(ErsapConstants.DPE,
                                          base.getFrontEnd().getCanonicalName());
        base.listen(topic, new GatewayCallback());
        base.register(topic, base.getMe().getDescription());

        xMsgUtil.sleep(100);
    }


    public void stop() {
        context.destroy();
        registrar.shutdown();
        base.destroy();
    }


    private void startDpe(RequestParser parser, xMsgMeta.Builder meta)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void stopDpe(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void setFrontEnd(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void pingDpe(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void startContainer(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void stopContainer(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void startService(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    private void stopService(RequestParser parser, Builder metadata)
            throws RequestException, ErsapException {
        // TODO implement this
    }


    /**
     * DPE callback.
     * <p>
     * The topic of this subscription is:
     * topic = CConstants.DPE + ":" + dpeCanonicalName
     * <p>
     * The following are accepted message data:
     * <li>
     *     CConstants.START_DPE ?
     *     dpeHost ? dpePort ? dpeLang ? poolSize ? regHost ? regPort ? description
     * </li>
     * <li>
     *     CConstants.STOP_REMOTE_DPE ?
     *     dpeHost ? dpePort ? dpeLang
     * </li>
     * <li>
     *     CConstants.SET_FRONT_END_REMOTE ?
     *     dpeHost ? dpePort ? dpeLang ? frontEndHost ? frontEndPort ? frontEndLang
     * </li>
     * <li>
     *     CConstants.PING_REMOTE_DPE ?
     *     dpeHost ? dpePort ? dpeLang
     * </li>
     * <li>
     *     CConstants.START_REMOTE_CONTAINER ?
     *     dpeHost ? dpePort ? dpeLang ? containerName ? poolSize ? description
     * </li>
     * <li>
     *     CConstants.STOP_REMOTE_CONTAINER ?
     *     dpeHost ? dpePort ? dpeLang ? containerName
     * </li>
     * <li>
     *     CConstants.START_REMOTE_SERVICE ?
     *     dpeHost ? dpePort ? dpeLang ? containerName ? engineName ? engineClass ?
     *     poolSize ? description ? initialState
     * </li>
     * <li>
     *     CConstants.STOP_REMOTE_SERVICE ?
     *     dpeHost ? dpePort ? dpeLang ? containerName ? engineName
     * </li>
     */
    private class GatewayCallback implements xMsgCallBack {

        @Override
        public void callback(xMsgMessage msg) {
            xMsgMeta.Builder metadata = msg.getMetaData();
            try {
                RequestParser parser = RequestParser.build(msg);
                String cmd = parser.nextString();

                switch (cmd) {
                    case ErsapConstants.START_DPE:
                        startDpe(parser, metadata);
                        break;

                    case ErsapConstants.STOP_REMOTE_DPE:
                        stopDpe(parser, metadata);
                        break;

                    case ErsapConstants.SET_FRONT_END_REMOTE:
                        setFrontEnd(parser, metadata);
                        break;

                    case ErsapConstants.PING_REMOTE_DPE:
                        pingDpe(parser, metadata);
                        break;

                    case ErsapConstants.START_REMOTE_CONTAINER:
                        startContainer(parser, metadata);
                        break;

                    case ErsapConstants.STOP_REMOTE_CONTAINER:
                        stopContainer(parser, metadata);
                        break;

                    case ErsapConstants.START_REMOTE_SERVICE:
                        startService(parser, metadata);
                        break;

                    case ErsapConstants.STOP_REMOTE_SERVICE:
                        stopService(parser, metadata);
                        break;

                    default:
                        break;
                }
            } catch (RequestException e) {
                e.printStackTrace();
            } catch (ErsapException e) {
                e.printStackTrace();
            }
        }
    }


    static DpeName getMonitorFrontEnd() {
        String monName = System.getenv(ErsapConstants.ENV_MONITOR_FE);
        if (monName != null) {
            try {
                return new DpeName(monName);
            } catch (IllegalArgumentException e) {
                Logging.error("Cannot use $%s: %s", ErsapConstants.ENV_MONITOR_FE, e.getMessage());
            }
        }
        return null;
    }
}
