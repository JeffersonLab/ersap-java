/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.core.MessageUtil;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgContext;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;
import org.jlab.coda.xmsg.net.xMsgRegAddress;
import org.jlab.coda.xmsg.net.xMsgSocketFactory;
import org.jlab.coda.xmsg.sys.xMsgRegistrar;
import org.jlab.coda.xmsg.sys.regdis.xMsgRegDriver;
import org.jlab.coda.xmsg.sys.regdis.xMsgRegFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@Tag("integration")
public class ErsapQueriesTest {

    private static final xMsgRegistration.OwnerType TYPE = xMsgRegistration.OwnerType.SUBSCRIBER;

    private static TestData data;

    private ErsapBase base = base();
    private ErsapQueries.ErsapQueryBuilder queryBuilder;


    private static class Data<T extends ErsapName> {

        final T name;
        final JSONObject registration;
        final JSONObject runtime;

        Data(T name, JSONObject registration, JSONObject runtime) {
            this.name = name;
            this.registration = registration;
            this.runtime = runtime;
        }
    }


    private static class TestData {

        private static final String DATE = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(ErsapConstants.DATE_FORMAT));

        private final xMsgContext context;
        private final xMsgRegistrar server;
        private final xMsgRegDriver driver;

        private final Map<String, Data<DpeName>> dpes = new HashMap<>();
        private final Map<String, Data<ContainerName>> containers = new HashMap<>();
        private final Map<String, Data<ServiceName>> services = new HashMap<>();

        TestData() throws xMsgException {

            xMsgRegAddress addr = new xMsgRegAddress("localhost", 7775);
            context = xMsgContext.newContext();
            server = new xMsgRegistrar(context, addr);
            driver = new xMsgRegDriver(addr, new xMsgSocketFactory(context.getContext()));

            server.start();
            driver.connect();

            createDpe("dpeJ1", "10.2.9.1", ErsapLang.JAVA);
            createDpe("dpeJ2", "10.2.9.2", ErsapLang.JAVA);
            createDpe("dpeC1", "10.2.9.1", ErsapLang.CPP);
            createDpe("dpeC2", "10.2.9.2", ErsapLang.CPP);

            createContainer("contAJ1", "dpeJ1", "A");
            createContainer("contAJ2", "dpeJ2", "A");
            createContainer("contAC1", "dpeC1", "A");
            createContainer("contAC2", "dpeC2", "A");

            createContainer("contBJ1", "dpeJ1", "B");

            createContainer("contCJ1", "dpeJ1", "C");
            createContainer("contCC1", "dpeC1", "C");

            createService("E1", "contAJ1", "E", "Trevor", "Calculate error");
            createService("E2", "contAJ2", "E", "Trevor", "Calculate error");
            createService("E3", "contBJ1", "E", "Trevor", "Calculate error");

            createService("F1", "contAJ1", "F", "Franklin", "Find term");
            createService("F2", "contAJ2", "F", "Franklin", "Find term");

            createService("G1", "contAJ1", "G", "Michael", "Grep term");

            createService("H1", "contBJ1", "H", "Franklin", "Calculate height");
            createService("H2", "contCJ1", "H", "Franklin", "Calculate height");

            createService("M1", "contAC1", "M", "Michael", "Get max");
            createService("M2", "contAC2", "M", "Michael", "Get max");

            createService("N1", "contAC1", "N", "Michael", "Sum N");
            createService("N2", "contCC1", "N", "Michael", "Sum N");

            dpes.forEach((k, n) -> register(regData(n.name)));
            containers.forEach((k, n) -> register(regData(n.name)));
            services.forEach((k, n) -> register(regData(n.name)));
        }

        private void createDpe(String key, String host, ErsapLang lang) {
            DpeName name = new DpeName(host, lang);

            JSONObject regData = new JSONObject();
            regData.put("name", name.canonicalName());
            regData.put("start_time", DATE);
            regData.put("containers", new JSONArray());

            JSONObject runData = new JSONObject();
            runData.put("name", name.canonicalName());
            runData.put("snapshot_time", DATE);
            runData.put("containers", new JSONArray());

            dpes.put(key, new Data<>(name, regData, runData));
        }

        private void createContainer(String key, String dpeKey, String contName) {
            Data<DpeName> dpe = dpes.get(dpeKey);
            ContainerName name = new ContainerName(dpe.name, contName);

            JSONObject regData = new JSONObject();
            regData.put("name", name.canonicalName());
            regData.put("start_time", DATE);
            regData.put("services", new JSONArray());

            JSONObject runData = new JSONObject();
            runData.put("name", name.canonicalName());
            runData.put("snapshot_time", DATE);
            runData.put("services", new JSONArray());

            dpe.registration.getJSONArray("containers").put(regData);
            dpe.runtime.getJSONArray("containers").put(runData);

            containers.put(key, new Data<>(name, regData, runData));
        }

        private void createService(String key, String contKey, String engine,
                                   String author, String description) {
            Data<ContainerName> container = containers.get(contKey);
            ServiceName name = new ServiceName(container.name, engine);

            JSONObject regData = new JSONObject();
            regData.put("name", name.canonicalName());
            regData.put("class_name", name.canonicalName());
            regData.put("start_time", DATE);
            regData.put("author", author);
            regData.put("description", description);

            JSONObject runData = new JSONObject();
            runData.put("name", name.canonicalName());
            runData.put("snapshot_time", DATE);

            container.registration.getJSONArray("services").put(regData);
            container.runtime.getJSONArray("services").put(runData);

            services.put(key, new Data<>(name, regData, runData));
        }

        void close() {
            driver.close();
            context.close();
            server.shutdown();
        }

        DpeName dpe(String name) {
            return dpes.get(name).name;
        }

        ContainerName cont(String name) {
            return containers.get(name).name;
        }

        ServiceName service(String name) {
            return services.get(name).name;
        }

        Set<DpeName> dpes(String... names) {
            return names(dpes, names);
        }

        Set<ContainerName> containers(String... names) {
            return names(containers, names);
        }

        Set<ServiceName> services(String... names) {
            return names(services, names);
        }

        private <T extends ErsapName> Set<T> names(Map<String, Data<T>> map,
                                                   String... names) {
            Set<T> set = new HashSet<>();
            for (String name : names) {
                set.add(map.get(name).name);
            }
            return set;
        }

        private void register(xMsgRegistration data) {
            try {
                driver.addRegistration("test", data);
            } catch (xMsgException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject json(String dpeName) {
            return dpes.values()
                       .stream()
                       .filter(d -> dpeName.equals(d.name.canonicalName()))
                       .map(d -> {
                           JSONObject r = new JSONObject();
                           r.put(ErsapConstants.REGISTRATION_KEY, d.registration);
                           r.put(ErsapConstants.RUNTIME_KEY, d.runtime);
                           return r;
                       })
                       .findFirst()
                       .get();
        }
    }


    @BeforeAll
    public static void prepare() throws xMsgException {
        data = new TestData();
    }


    @AfterAll
    public static void end() {
        data.close();
    }


    @BeforeEach
    public void setup() throws Exception {
        queryBuilder = new ErsapQueries.ErsapQueryBuilder(base, ErsapComponent.dpe());
    }


    @Test
    public void getNamesOfAllDpes() throws Exception {
        Set<DpeName> result = queryBuilder.canonicalNames(ErsapFilters.allDpes())
                                          .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2", "dpeC1", "dpeC2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfAllContainers() throws Exception {
        Set<ContainerName> result = queryBuilder.canonicalNames(ErsapFilters.allContainers())
                                                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfAllServices() throws Exception {
        Set<ServiceName> result = queryBuilder.canonicalNames(ErsapFilters.allServices())
                                                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3",
                                                  "F1", "F2", "G1", "H1", "H2",
                                                  "M1", "M2", "N1", "N2");
        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfDpesByHost() throws Exception {
        Set<DpeName> result = queryBuilder.canonicalNames(ErsapFilters.dpesByHost("10.2.9.1"))
                                          .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeC1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfDpesByLanguage() throws Exception {
        ErsapLang lang = ErsapLang.JAVA;
        Set<DpeName> result = queryBuilder.canonicalNames(ErsapFilters.dpesByLanguage(lang))
                                          .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfContainersByHost() throws Exception {
        Set<ContainerName> result = queryBuilder
                .canonicalNames(ErsapFilters.containersByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAC1",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfContainersByDpe() throws Exception {
        Set<ContainerName> result = queryBuilder
                .canonicalNames(ErsapFilters.containersByDpe(data.dpe("dpeJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contBJ1", "contCJ1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfContainersByLanguage() throws Exception {
        Set<ContainerName> result = queryBuilder
                .canonicalNames(ErsapFilters.containersByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAC1", "contAC2", "contCC1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfContainersByName() throws Exception {
        Set<ContainerName> result = queryBuilder
                .canonicalNames(ErsapFilters.containersByName("A"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByHost() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E3", "F1", "G1", "H1", "H2",
                                                  "M1", "N1", "N2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByDpe() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByDpe(data.dpe("dpeC1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "N1", "N2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByContainer() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByContainer(data.cont("contAJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "F1", "G1");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByLanguage() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "M2", "N1", "N2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByName() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByName("E"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByAuthor() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByAuthor("Trevor"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfServicesByDescription() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByDescription(".*[Cc]alculate.*"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3", "H1", "H2");

        assertThat(result, is(expected));
    }


    @Test
    public void getNamesOfMissingDpes() throws Exception {
        Set<DpeName> result = queryBuilder
                .canonicalNames(ErsapFilters.dpesByHost("10.2.9.3"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getNamesOfMissingContainers() throws Exception {
        Set<ContainerName> result = queryBuilder
                .canonicalNames(ErsapFilters.containersByName("Z"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getNamesOfMissingServices() throws Exception {
        Set<ServiceName> result = queryBuilder
                .canonicalNames(ErsapFilters.servicesByAuthor("CJ"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void discoverRegisteredDpe() throws Exception {
        Boolean result = queryBuilder.discover(new DpeName("10.2.9.1_java"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(true));
    }


    @Test
    public void discoverRegisteredContainer() throws Exception {
        Boolean result = queryBuilder.discover(new ContainerName("10.2.9.1_cpp:C"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(true));
    }


    @Test
    public void discoverRegisteredService() throws Exception {
        Boolean result = queryBuilder.discover(new ServiceName("10.2.9.1_java:B:H"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(true));
    }


    @Test
    public void discoverMissingDpe() throws Exception {
        Boolean result = queryBuilder.discover(new DpeName("10.2.9.1_python"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(false));
    }


    @Test
    public void discoverMissingContainer() throws Exception {
        Boolean result = queryBuilder.discover(new ContainerName("10.2.9.1_cpp:B"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(false));
    }


    @Test
    public void discoverMissingService() throws Exception {
        Boolean result = queryBuilder.discover(new ServiceName("10.2.9.1_java:A:H"))
                                     .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(false));
    }


    @Test
    public void getRegistrationOfAllDpes() throws Exception {
        Set<DpeRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.allDpes())
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2", "dpeC1", "dpeC2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfAllContainers() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.allContainers())
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfAllServices() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.allServices())
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3",
                                                  "F1", "F2", "G1", "H1", "H2",
                                                  "M1", "M2", "N1", "N2");
        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfDpesByHost() throws Exception {
        Set<DpeRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.dpesByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfDpesByLanguage() throws Exception {
        ErsapLang lang = ErsapLang.JAVA;
        Set<DpeRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.dpesByLanguage(lang))
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfContainersByHost() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.containersByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAC1",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfContainersByDpe() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.containersByDpe(data.dpe("dpeJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contBJ1", "contCJ1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfContainersByLanguage() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.containersByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAC1", "contAC2", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfContainersByName() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.containersByName("A"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByHost() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E3", "F1", "G1", "H1", "H2",
                                                  "M1", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByDpe() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByDpe(data.dpe("dpeC1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByContainer() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByContainer(data.cont("contAJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "F1", "G1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByLanguage() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "M2", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByName() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByName("E"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByAuthor() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByAuthor("Trevor"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfServicesByDescription() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByDescription(".*[Cc]alculate.*"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3", "H1", "H2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRegistrationOfMissingDpes() throws Exception {
        Set<DpeRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.dpesByHost("10.2.9.3"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRegistrationOfMissingContainers() throws Exception {
        Set<ContainerRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.containersByName("Z"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRegistrationOfMissingServices() throws Exception {
        Set<ServiceRegistrationData> result = queryBuilder
                .registrationData(ErsapFilters.servicesByAuthor("CJ"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRegistrationOfDpeByName() throws Exception {
        Optional<DpeRegistrationData> result = queryBuilder
                .registrationData(new DpeName("10.2.9.1_java"))
                .syncRun(3, TimeUnit.SECONDS);
        DpeName expected = data.dpe("dpeJ1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRegistrationOfContainerByName() throws Exception {
        Optional<ContainerRegistrationData> result = queryBuilder
                .registrationData(new ContainerName("10.2.9.1_cpp:C"))
                .syncRun(3, TimeUnit.SECONDS);
        ContainerName expected = data.cont("contCC1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRegistrationOfServiceByName() throws Exception {
        Optional<ServiceRegistrationData> result = queryBuilder
                .registrationData(new ServiceName("10.2.9.1_java:B:H"))
                .syncRun(3, TimeUnit.SECONDS);
        ServiceName expected = data.service("H1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRegistrationOfMissingDpeByName() throws Exception {
        Optional<DpeRegistrationData> result = queryBuilder
                .registrationData(new DpeName("10.2.9.1_python"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    @Test
    public void getRegistrationOfMissingContainerByName() throws Exception {
        Optional<ContainerRegistrationData> result = queryBuilder
                .registrationData(new ContainerName("10.2.9.1_cpp:B"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    @Test
    public void getRegistrationOfMissingServiceByName() throws Exception {
        Optional<ServiceRegistrationData> result = queryBuilder
                .registrationData(new ServiceName("10.2.9.1_java:A:H"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    @Test
    public void getRuntimeOfAllDpes() throws Exception {
        Set<DpeRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.allDpes())
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2", "dpeC1", "dpeC2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfAllContainers() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.allContainers())
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfAllServices() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.allServices())
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3",
                                                  "F1", "F2", "G1", "H1", "H2",
                                                  "M1", "M2", "N1", "N2");
        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfDpesByHost() throws Exception {
        Set<DpeRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.dpesByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfDpesByLanguage() throws Exception {
        ErsapLang lang = ErsapLang.JAVA;
        Set<DpeRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.dpesByLanguage(lang))
                .syncRun(3, TimeUnit.SECONDS);
        Set<DpeName> expected = data.dpes("dpeJ1", "dpeJ2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfContainersByHost() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.containersByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAC1",
                                                      "contBJ1", "contCJ1", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfContainersByDpe() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.containersByDpe(data.dpe("dpeJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contBJ1", "contCJ1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfContainersByLanguage() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.containersByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAC1", "contAC2", "contCC1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfContainersByName() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.containersByName("A"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ContainerName> expected = data.containers("contAJ1", "contAJ2", "contAC1", "contAC2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByHost() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByHost("10.2.9.1"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E3", "F1", "G1", "H1", "H2",
                                                  "M1", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByDpe() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByDpe(data.dpe("dpeC1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByContainer() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByContainer(data.cont("contAJ1")))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "F1", "G1");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByLanguage() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByLanguage(ErsapLang.CPP))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("M1", "M2", "N1", "N2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByName() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByName("E"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByAuthor() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByAuthor("Trevor"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfServicesByDescription() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByDescription(".*[Cc]alculate.*"))
                .syncRun(3, TimeUnit.SECONDS);
        Set<ServiceName> expected = data.services("E1", "E2", "E3", "H1", "H2");

        assertThat(names(result), is(expected));
    }


    @Test
    public void getRuntimeOfMissingDpes() throws Exception {
        Set<DpeRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.dpesByHost("10.2.9.3"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRuntimeOfMissingContainers() throws Exception {
        Set<ContainerRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.containersByName("Z"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRuntimeOfMissingServices() throws Exception {
        Set<ServiceRuntimeData> result = queryBuilder
                .runtimeData(ErsapFilters.servicesByAuthor("CJ"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result, is(empty()));
    }


    @Test
    public void getRuntimeOfDpeByName() throws Exception {
        Optional<DpeRuntimeData> result = queryBuilder
                .runtimeData(new DpeName("10.2.9.1_java"))
                .syncRun(3, TimeUnit.SECONDS);
        DpeName expected = data.dpe("dpeJ1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRuntimeOfContainerByName() throws Exception {
        Optional<ContainerRuntimeData> result = queryBuilder
                .runtimeData(new ContainerName("10.2.9.1_cpp:C"))
                .syncRun(3, TimeUnit.SECONDS);
        ContainerName expected = data.cont("contCC1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRuntimeOfServiceByName() throws Exception {
        Optional<ServiceRuntimeData> result = queryBuilder
                .runtimeData(new ServiceName("10.2.9.1_java:B:H"))
                .syncRun(3, TimeUnit.SECONDS);
        ServiceName expected = data.service("H1");

        assertThat(result.get().name(), is(expected));
    }


    @Test
    public void getRuntimeOfMissingDpeByName() throws Exception {
        Optional<DpeRuntimeData> result = queryBuilder
                .runtimeData(new DpeName("10.2.9.1_python"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    @Test
    public void getRuntimeOfMissingContainerByName() throws Exception {
        Optional<ContainerRuntimeData> result = queryBuilder
                .runtimeData(new ContainerName("10.2.9.1_cpp:B"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    @Test
    public void getRuntimeOfMissingServiceByName() throws Exception {
        Optional<ServiceRuntimeData> result = queryBuilder
                .runtimeData(new ServiceName("10.2.9.1_java:A:H"))
                .syncRun(3, TimeUnit.SECONDS);

        assertThat(result.isPresent(), is(false));
    }


    private static ErsapBase base() {
        return new ErsapBase(ErsapComponent.dpe(), ErsapComponent.dpe()) {
            @Override
            public xMsgMessage syncPublish(xMsgProxyAddress address, xMsgMessage msg, long timeout)
                    throws xMsgException, TimeoutException {
                JSONObject report = data.json(msg.getTopic().subject());
                return MessageUtil.buildRequest(msg.getTopic(), report.toString());
            }
        };
    }


    private static xMsgRegistration regData(DpeName name) {
        return registration(name, xMsgTopic.build("dpe", name.canonicalName()));
    }


    private static xMsgRegistration regData(ContainerName name) {
        return registration(name, xMsgTopic.build("container", name.canonicalName()));
    }


    private static xMsgRegistration regData(ServiceName name) {
        return registration(name, xMsgTopic.wrap(name.canonicalName()));
    }


    private static xMsgRegistration registration(ErsapName name, xMsgTopic topic) {
        return xMsgRegFactory.newRegistration(name.canonicalName(), name.address(), TYPE, topic)
                             .build();
    }


    private static <T extends ErsapName> Set<T> names(Set<? extends ErsapReportData<T>> regData) {
        return regData.stream().map(ErsapReportData::name).collect(Collectors.toSet());
    }
}
