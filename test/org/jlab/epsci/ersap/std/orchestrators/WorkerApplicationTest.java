/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.ContainerName;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.ServiceName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class WorkerApplicationTest {

    @Test
    public void getStageService() throws Exception {
        WorkerApplication app = AppData.builder().build();

        assertThat(app.stageService().canonicalName(), is("10.1.1.10_java:master:S1"));
    }


    @Test
    public void getReaderService() throws Exception {
        WorkerApplication app = AppData.builder().build();

        assertThat(app.readerService().canonicalName(), is("10.1.1.10_java:master:R1"));
    }


    @Test
    public void getWriterService() throws Exception {
        WorkerApplication app = AppData.builder().build();

        assertThat(app.writerService().canonicalName(), is("10.1.1.10_java:master:W1"));
    }


    @Test
    public void getReconstructionServicesForSingleLangApplication() throws Exception {
        WorkerApplication app = AppData.builder().build();

        ServiceName[] expected = toServices("10.1.1.10_java:master:J1",
                                            "10.1.1.10_java:master:J2",
                                            "10.1.1.10_java:master:J3");

        assertThat(app.processingServices(), containsInAnyOrder(expected));
    }


    @Test
    public void getReconstructionServicesForMultiLangApplication() throws Exception {
        WorkerApplication app = AppData.builder()
                .withServices(AppData.J1, AppData.C1, AppData.C2, AppData.P1)
                .withDpes(AppData.DPE1, AppData.DPE2, AppData.DPE3)
                .build();

        ServiceName[] expected = toServices("10.1.1.10_java:master:J1",
                                            "10.1.1.10_cpp:master:C1",
                                            "10.1.1.10_cpp:master:C2",
                                            "10.1.1.10_python:slave:P1");

        assertThat(app.processingServices(), containsInAnyOrder(expected));
    }


    @Test
    public void getUniqueContainer() throws Exception {
        WorkerApplication app = AppData.builder().build();

        ContainerName[] expected = toContainers("10.1.1.10_java:master");

        assertThat(flatContainers(app.allContainers()), containsInAnyOrder(expected));
    }


    @Test
    public void getAllContainersForSingleLangApplication() throws Exception {
        WorkerApplication app = AppData.builder()
                .withServices(AppData.J1, AppData.J2, AppData.K1)
                .build();

        ContainerName[] expected = toContainers("10.1.1.10_java:master", "10.1.1.10_java:slave");

        assertThat(flatContainers(app.allContainers()), containsInAnyOrder(expected));
    }


    @Test
    public void getAllContainersForMultiLangApplication() throws Exception {
        WorkerApplication app = AppData.builder()
                .withServices(AppData.J1, AppData.K1, AppData.C1, AppData.P1)
                .withDpes(AppData.DPE1, AppData.DPE2, AppData.DPE3)
                .build();

        ContainerName[] expected = toContainers("10.1.1.10_java:master",
                                                "10.1.1.10_java:slave",
                                                "10.1.1.10_cpp:master",
                                                "10.1.1.10_python:slave");

        assertThat(flatContainers(app.allContainers()), containsInAnyOrder(expected));
    }


    @Test
    public void getLanguageForSingleLangApplication() throws Exception {
        ApplicationInfo info = AppData.newAppInfo(AppData.J1, AppData.J2, AppData.J3);

        assertThat(info.getLanguages(), containsInAnyOrder(ErsapLang.JAVA));
    }


    @Test
    public void getLanguagesForMultiLangApplication() throws Exception {
        ApplicationInfo info = AppData.newAppInfo(AppData.J1, AppData.C1, AppData.P1);

        ErsapLang[] expected = {ErsapLang.JAVA, ErsapLang.CPP, ErsapLang.PYTHON};

        assertThat(info.getLanguages(), containsInAnyOrder(expected));
    }


    private static ServiceName[] toServices(String... elem) {
        return Stream.of(elem)
                     .map(ServiceName::new)
                     .toArray(ServiceName[]::new);
    }


    private static ContainerName[] toContainers(String... elem) {
        return Stream.of(elem)
                     .map(ContainerName::new)
                     .toArray(ContainerName[]::new);
    }


    private static List<ContainerName> flatContainers(Map<DpeName, Set<ContainerName>> all) {
        return all.values().stream()
                  .flatMap(Set::stream)
                  .collect(Collectors.toList());
    }
}
