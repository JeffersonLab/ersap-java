/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.Composition;
import org.jlab.epsci.ersap.base.ContainerName;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.ServiceName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class WorkerApplication {

    private final ApplicationInfo application;
    private final Map<ErsapLang, DpeInfo> dpes;


    WorkerApplication(ApplicationInfo application, DpeInfo dpe) {
        this.application = application;
        this.dpes = new HashMap<>();
        this.dpes.put(dpe.name.language(), dpe);
    }


    WorkerApplication(ApplicationInfo application, Map<ErsapLang, DpeInfo> dpes) {
        this.application = application;
        this.dpes = new HashMap<>(dpes);
    }


    public Set<ErsapLang> languages() {
        return application.getLanguages();
    }


    public ServiceName stageService() {
        return toName(application.getStageService());
    }


    public ServiceName readerService() {
        return toName(application.getReaderService());
    }


    public ServiceName writerService() {
        return toName(application.getWriterService());
    }


    public List<ServiceName> processingServices() {
        return application.getDataProcessingServices().stream()
                .map(this::toName)
                .collect(Collectors.toList());
    }


    public List<ServiceName> monitoringServices() {
        return application.getMonitoringServices().stream()
                .map(this::toName)
                .collect(Collectors.toList());
    }


    public List<ServiceName> services() {
        return application.getServices().stream()
                .map(this::toName)
                .collect(Collectors.toList());
    }


    public Composition composition() {
        List<ServiceName> dataServices = processingServices();

        // main chain
        String composition = readerService().canonicalName();
        for (ServiceName service : dataServices) {
            composition += "+" + service.canonicalName();
        }
        composition += "+" + writerService().canonicalName();
        composition += "+" + readerService().canonicalName();
        composition += ";";

        List<ServiceName> monServices = monitoringServices();
        if (!monServices.isEmpty()) {
            // monitoring chain
            composition += dataServices.get(dataServices.size() - 1).canonicalName();
            for (ServiceName service : monServices) {
                composition += "+" + service.canonicalName();
            }
            composition += ";";
        }

        return new Composition(composition);
    }


    private ServiceName toName(ServiceInfo service) {
        DpeInfo dpe = dpes.get(service.lang);
        if (dpe == null) {
            String msg = String.format("Missing %s DPE for service %s", service.lang, service.name);
            throw new IllegalStateException(msg);
        }
        return new ServiceName(dpe.name, service.cont, service.name);
    }


    Stream<DeployInfo> getInputOutputServicesDeployInfo() {
        return application.getInputOutputServices().stream()
                          .map(s -> new DeployInfo(toName(s), s.classpath, 1));
    }


    Stream<DeployInfo> getProcessingServicesDeployInfo() {
        int maxCores = maxCores();
        return application.getDataProcessingServices().stream()
                          .distinct()
                          .map(s -> new DeployInfo(toName(s), s.classpath, maxCores));
    }


    Stream<DeployInfo> getMonitoringServicesDeployInfo() {
        return application.getMonitoringServices().stream()
                          .distinct()
                          .map(s -> new DeployInfo(toName(s), s.classpath, 1));
    }


    Map<DpeName, Set<ServiceName>> allServices() {
        return application.getAllServices().stream()
                          .map(this::toName)
                          .collect(Collectors.groupingBy(ServiceName::dpe, Collectors.toSet()));
    }


    Map<DpeName, Set<ContainerName>> allContainers() {
        return application.getAllServices().stream()
                          .map(this::toName)
                          .map(ServiceName::container)
                          .collect(Collectors.groupingBy(ContainerName::dpe, Collectors.toSet()));
    }


    Set<DpeName> dpes() {
        return dpes.values().stream()
                   .map(dpe -> dpe.name)
                   .collect(Collectors.toSet());
    }


    public int maxCores() {
        return dpes.values().stream()
                   .mapToInt(dpe -> dpe.cores)
                   .min()
                   .orElseThrow(() -> new IllegalStateException("Empty list of DPEs"));
    }


    public String hostName() {
        DpeInfo firstDpe = dpes.values().iterator().next();
        return firstDpe.name.address().host();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dpes.hashCode();
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
        WorkerApplication other = (WorkerApplication) obj;
        if (!dpes.equals(other.dpes)) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        String dpeNames = dpes.values().stream()
                .map(dpe -> dpe.name.canonicalName())
                .collect(Collectors.joining(","));
        return "[" + dpeNames + "]";
    }
}
