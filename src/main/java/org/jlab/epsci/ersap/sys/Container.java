/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.core.MessageUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.util.report.ContainerReport;
import org.jlab.epsci.ersap.util.EnvUtils;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.excp.xMsgException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service container.
 *
 *
 *
 */
class Container extends AbstractActor {

    private final ConcurrentHashMap<String, Service> myServices = new ConcurrentHashMap<>();
    private final ContainerReport myReport;

    private boolean isRegistered = false;

    Container(ErsapComponent comp, ErsapComponent frontEnd) {
        super(comp, frontEnd);

        myReport = new ContainerReport(base, EnvUtils.userName());
    }

    @Override
    void initialize() throws ErsapException {
        register();
    }

    @Override
    void end() {
        removeAllServices();
        removeRegistration();
    }

    @Override
    void startMsg() {
        Logging.info("started container = %s", base.getName());
    }

    @Override
    void stopMsg() {
        Logging.info("removed container = %s", base.getName());
    }

    public void addService(ErsapComponent comp,
                           ErsapComponent frontEnd,
                           ConnectionPools connectionPools,
                           String session) throws ErsapException {
        String serviceName = comp.getCanonicalName();
        Service service = myServices.get(serviceName);
        if (service == null) {
            service = new Service(comp, frontEnd, connectionPools, session);
            Service result = myServices.putIfAbsent(serviceName, service);
            if (result == null) {
                try {
                    service.start();
                    myReport.addService(service.getReport());
                } catch (ErsapException e) {
                    service.stop();
                    myServices.remove(serviceName, service);
                    throw e;
                }
            } else {
                service.stop();    // destroy the extra engine object
            }
        } else {
            Logging.error("service = %s already exists. No new service is deployed", serviceName);
        }
    }

    public boolean removeService(String serviceName) {
        Service service = myServices.remove(serviceName);
        if (service != null) {
            service.stop();
            myReport.removeService(service.getReport());
            return true;
        }
        return false;
    }

    private void removeAllServices() {
        myServices.values().parallelStream().forEach(Service::stop);
        myServices.clear();
    }

    private void register() throws ErsapException {
        xMsgTopic topic = xMsgTopic.build(ErsapConstants.CONTAINER, base.getName());
        base.register(topic, base.getDescription());
        isRegistered = true;
    }

    private void removeRegistration() {
        if (isRegistered && shouldDeregister()) {
            try {
                reportDown();
                base.removeRegistration(base.getMe().getTopic());
            } catch (ErsapException e) {
                Logging.error("container = %s: %s", base.getName(), e.getMessage());
            } finally {
                isRegistered = false;
            }
        }
    }

    private void reportDown() {
        try {
            // broadcast to the local proxy
            String data = MessageUtil.buildData(ErsapConstants.CONTAINER_DOWN, base.getName());
            base.send(base.getFrontEnd(), data);
        } catch (xMsgException e) {
            Logging.error("container = %s: could not send down report: %s",
                          base.getName(), e.getMessage());
        }
    }

    public void setFrontEnd(ErsapComponent frontEnd) {
        base.setFrontEnd(frontEnd);
    }

    public ConcurrentHashMap<String, Service> geServices() {
        return myServices;
    }

    public ContainerReport getReport() {
        return myReport;
    }
}
