/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.util.report.ServiceReport;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgSubscription;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A ERSAP service listening and executing requests.
 * <p>
 * An internal object pool contains N number of {@link ServiceEngine} objects,
 * where N is user specified value (usually equals to the number of cores).
 * A thread pool contains threads to run each object within.
 * Number of threads in the pool is equal to the size of the object pool.
 * Thread pool is fixed size, however object pool is capable of expanding.
 */
class Service extends AbstractActor {

    private final String name;
    private final Engine userEngine;

    private final ExecutorService executionPool;
    private final ServiceEngine[] enginePool;
    private final ServiceSysConfig sysConfig;
    private final ServiceReport sysReport;

    private xMsgSubscription subscription;

    /**
     * Constructor of a service.
     * <p>
     * Create thread pool to run requests to this service.
     * Create object pool to hold the engines this service.
     * Object pool size is set to be 2 in case it was requested
     * to be 0 or negative number.
     *
     * @throws ErsapException
     */
    Service(ErsapComponent comp,
            ErsapComponent frontEnd,
            ConnectionPools connectionPools,
            String session) throws ErsapException {
        super(comp, frontEnd);

        name = comp.getCanonicalName();
        sysConfig = new ServiceSysConfig(name, comp.getInitialState());

        // Dynamic loading of the ERSAP engine class
        // Note: using system class loader
        EngineLoader cl = new EngineLoader(ClassLoader.getSystemClassLoader());
        userEngine = cl.load(comp.getEngineClass());

        sysReport = new ServiceReport(comp, userEngine, session);

        // Creating thread pool
        executionPool = xMsgUtil.newThreadPool(comp.getSubscriptionPoolSize(), name);

        // Creating service object pool
        enginePool = new ServiceEngine[comp.getSubscriptionPoolSize()];

        // Fill the object pool
        ServiceActor engineActor = new  ServiceActor(comp, frontEnd, connectionPools);
        for (int i = 0; i < comp.getSubscriptionPoolSize(); i++) {
            enginePool[i] = new ServiceEngine(userEngine, engineActor, sysConfig, sysReport);
        }

        // Register with the shared memory
        SharedMemory.addReceiver(name);
    }


    @Override
    void initialize() throws ErsapException {
        base.cacheLocalConnection();

        // start the engines
        try {
            Arrays.stream(enginePool).parallel().forEach(s -> {
                try {
                    s.start();
                } catch (ErsapException e) {
                    throwWrapped(e);
                }
            });
        } catch (WrappedException e) {
            throw e.getCause();
        }

        // subscribe and register
        xMsgTopic topic = base.getMe().getTopic();
        xMsgCallBack callback = new ServiceCallBack();
        String description = base.getDescription();
        subscription = startRegisteredSubscription(topic, callback, description);
    }


    @Override
    void end() {
        stopSubscription();
        destroyEngines();
    }


    @Override
    void startMsg() {
        Logging.info("started service = %s  pool_size = %d", name, base.getPoolSize());
    }


    @Override
    void stopMsg() {
        Logging.info("removed service = %s", name);
    }


    private void configure(final xMsgMessage msg) throws Exception {
        while (true) {
            for (final ServiceEngine engine : enginePool) {
                if (engine.tryAcquire()) {
                    executionPool.submit(() -> {
                        try {
                            engine.configure(msg);
                        } catch (Exception e) {
                            printUnhandledException(e);
                        } finally {
                            engine.release();
                        }
                    });
                    return;
                }
            }
        }
    }


    private void execute(final xMsgMessage msg) {
        while (true) {
            for (final ServiceEngine engine : enginePool) {
                if (engine.tryAcquire()) {
                    executionPool.submit(() -> {
                        try {
                            engine.execute(msg);
                        } catch (Exception e) {
                            printUnhandledException(e);
                        } finally {
                            engine.release();
                        }
                    });
                    return;
                }
            }
        }
    }


    private void setup(xMsgMessage msg) throws RequestParser.RequestException {
        RequestParser setup = RequestParser.build(msg);
        String report = setup.nextString();
        int value = setup.nextInteger();
        boolean publishReport = value > 0; // 0 is used to cancel reports
        switch (report) {
            case ErsapConstants.SERVICE_REPORT_DONE:
                sysConfig.setDoneRequest(publishReport);
                sysConfig.setDoneReportThreshold(value);
                sysConfig.resetDoneRequestCount();
                break;
            case ErsapConstants.SERVICE_REPORT_DATA:
                sysConfig.setDataRequest(publishReport);
                sysConfig.setDataReportThreshold(value);
                sysConfig.resetDataRequestCount();
                break;
            case ErsapConstants.SERVICE_REPORT_RING:
                sysConfig.setRingRequest(publishReport);
                break;
            default:
                throw new RequestParser.RequestException("Invalid report request: " + report);
        }
        if (msg.hasReplyTopic()) {
            sendResponse(msg, xMsgMeta.Status.INFO, setup.request());
        }
    }


    private void printUnhandledException(Exception e) {
        StringWriter errors = new StringWriter();
        errors.write(name + ": ERSAP error: ");
        e.printStackTrace(new PrintWriter(errors));
        System.err.println(errors.toString());
    }


    void setFrontEnd(ErsapComponent frontEnd) {
        base.setFrontEnd(frontEnd);
    }

    ServiceReport getReport() {
        return sysReport;
    }


    private void stopSubscription() {
        if (subscription != null) {
            base.stopListening(subscription);
            base.stopCallbacks();
            try {
                if (shouldDeregister()) {
                    base.removeRegistration(base.getMe().getTopic());
                }
            } catch (ErsapException e) {
                Logging.error("service = %s: %s", name, e.getMessage());
            }
        }
    }


    private void destroyEngines() {
        destroyPool();
        Arrays.stream(enginePool).parallel().forEach(ServiceEngine::stop);
        userEngine.destroy();
    }


    private void destroyPool() {
        executionPool.shutdown();
        try {
            if (!executionPool.awaitTermination(10, TimeUnit.SECONDS)) {
                executionPool.shutdownNow();
                if (!executionPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    Logging.error("service = %s: execution pool did not terminate", name);
                }
            }
        } catch (InterruptedException ie) {
            executionPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    private class ServiceCallBack implements xMsgCallBack {

        @Override
        public void callback(xMsgMessage msg) {
            try {
                xMsgMeta.Builder metadata = msg.getMetaData();
                if (!metadata.hasAction()) {
                    setup(msg);
                } else if (metadata.getAction().equals(xMsgMeta.ControlAction.CONFIGURE)) {
                    configure(msg);
                } else {
                    execute(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (msg.hasReplyTopic()) {
                    sendResponse(msg, xMsgMeta.Status.ERROR, e.getMessage());
                }
            }
        }
    }
}
