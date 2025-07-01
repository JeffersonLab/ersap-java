/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.ErsapLang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ApplicationInfo {

    static final String STAGE = "stage";
    static final String READER = "reader";
    static final String WRITER = "writer";

    private final Map<String, ServiceInfo> ioServices;
    private final List<ServiceInfo> dataServices;
    private final List<ServiceInfo> monServices;
    private final Set<ErsapLang> languages;

    ApplicationInfo(Map<String, ServiceInfo> ioServices,
                    List<ServiceInfo> dataServices,
                    List<ServiceInfo> monServices) {
        this.ioServices = copyServices(ioServices);
        this.dataServices = copyServices(dataServices, true);
        this.monServices = copyServices(monServices, false);
        this.languages = parseLanguages();
    }

    private static Map<String, ServiceInfo> copyServices(Map<String, ServiceInfo> ioServices) {
        if (ioServices.get(STAGE) == null) {
            throw new IllegalArgumentException("missing stage service");
        }
        if (ioServices.get(READER) == null) {
            throw new IllegalArgumentException("missing reader service");
        }
        if (ioServices.get(WRITER) == null) {
            throw new IllegalArgumentException("missing writer service");
        }
        return new HashMap<>(ioServices);
    }

    private static List<ServiceInfo> copyServices(List<ServiceInfo> chain, boolean checkEmpty) {
        if (chain == null) {
            throw new IllegalArgumentException("null chain of services");
        }
        if (checkEmpty && chain.isEmpty()) {
            throw new IllegalArgumentException("empty chain of services");
        }
        return new ArrayList<>(chain);
    }

    private Set<ErsapLang> parseLanguages() {
        return allServices().map(s -> s.lang).collect(Collectors.toSet());
    }

    private Stream<ServiceInfo> allServices() {
        return stream(ioServices.values(), dataServices, monServices);
    }

    @SafeVarargs
    private final Stream<ServiceInfo> stream(Collection<ServiceInfo>... values) {
        return Stream.of(values).flatMap(Collection::stream);
    }

    ServiceInfo getStageService() {
        return ioServices.get(STAGE);
    }

    ServiceInfo getReaderService() {
        return ioServices.get(READER);
    }

    ServiceInfo getWriterService() {
        return ioServices.get(WRITER);
    }

    List<ServiceInfo> getInputOutputServices() {
        return Arrays.asList(getStageService(), getReaderService(), getWriterService());
    }

    List<ServiceInfo> getDataProcessingServices() {
        return dataServices;
    }

    List<ServiceInfo> getMonitoringServices() {
        return monServices;
    }

    Set<ServiceInfo> getServices() {
        return stream(dataServices, monServices).collect(Collectors.toSet());
    }

    Set<ServiceInfo> getAllServices() {
        return allServices().collect(Collectors.toSet());
    }

    Set<ErsapLang> getLanguages() {
        return languages;
    }
}
