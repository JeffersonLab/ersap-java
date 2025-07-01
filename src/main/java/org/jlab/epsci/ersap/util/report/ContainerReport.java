/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

import org.jlab.epsci.ersap.base.core.ErsapBase;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The container report claas.
 */
public class ContainerReport extends BaseReport {

    private Map<String, ServiceReport> services = new ConcurrentHashMap<>();

    public ContainerReport(ErsapBase base, String author) {
        super(base.getName(), author, base.getDescription());
    }

    public int getServiceCount() {
        return services.size();
    }

    public Collection<ServiceReport> getServices() {
        return services.values();
    }

    public ServiceReport addService(ServiceReport sr) {
        return services.putIfAbsent(sr.getName(), sr);
    }

    public ServiceReport removeService(ServiceReport sr) {
        return services.remove(sr.getName());
    }

    public void removeAllServices() {
        services.clear();
    }
}
