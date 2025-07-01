/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jlab.epsci.ersap.base.ErsapLang;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.hasEntry;


@Tag("integration")
public class OrchestratorConfigParserTest {

    private static final String CONT = OrchestratorConfigParser.getDefaultContainer();

    private final List<ServiceInfo> servicesList = new ArrayList<>();

    public OrchestratorConfigParserTest() {
        servicesList.add(service("org.jlab.clas12.ec.services.ECReconstruction",
                                 "ECReconstruction"));
        servicesList.add(service("org.clas12.services.tracking.SeedFinder",
                                 "SeedFinder"));
        servicesList.add(service("org.jlab.clas12.ftof.services.FTOFReconstruction",
                                 "FTOFReconstruction"));
    }


    @Test
    public void parseGoodServicesFileYaml() {
        OrchestratorConfigParser parser = parseFile("/services-ok.yml");

        assertThat(parser.parseDataProcessingServices(), is(servicesList));
    }


    @Test
    public void parseBadServicesFileYaml() {
        OrchestratorConfigParser parser = parseFile("/services-bad.yml");

        Exception ex = assertThrows(OrchestratorConfigException.class, () ->
                parser.parseDataProcessingServices());
        assertThat(ex.getMessage(), is("missing name or class of service"));
    }


    @Test
    public void parseIOServices() throws Exception {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        Map<String, ServiceInfo> services = parser.parseInputOutputServices();

        ServiceInfo reader = new ServiceInfo("org.jlab.clas12.convertors.CustomReader",
                                             CONT, "CustomReader", ErsapLang.JAVA);
        ServiceInfo writer = new ServiceInfo("org.jlab.clas12.convertors.CustomWriter",
                                             CONT, "CustomWriter", ErsapLang.CPP);

        assertThat(services, hasEntry(equalTo("reader"), equalTo(reader)));
        assertThat(services, hasEntry(equalTo("writer"), equalTo(writer)));
    }


    @Test
    public void parseMultiLangServices() throws Exception {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        List<ServiceInfo> expected = Arrays.asList(
                new ServiceInfo("org.jlab.clas12.convertors.ECReconstruction",
                                CONT, "ECReconstruction", ErsapLang.JAVA),
                new ServiceInfo("org.jlab.clas12.convertors.SeedFinder",
                                CONT, "SeedFinder", ErsapLang.JAVA),
                new ServiceInfo("org.jlab.clas12.convertors.HeaderFilter",
                                CONT, "HeaderFilter", ErsapLang.CPP),
                new ServiceInfo("org.jlab.clas12.convertors.FTOFReconstruction",
                                CONT, "FTOFReconstruction", ErsapLang.JAVA)
        );

        assertThat(parser.parseDataProcessingServices(), is(expected));
    }


    @Test
    public void parseMonitoringServices() throws Exception {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        List<ServiceInfo> expected = Arrays.asList(
                service("org.jlab.clas12.services.ECMonitoring", "ECMonitor"),
                service("org.jlab.clas12.services.DCMonitoring", "DCMonitor")
        );

        assertThat(parser.parseMonitoringServices(), is(expected));
    }


    @Test
    public void parseDataRingCallbacks() throws Exception {
        OrchestratorConfigParser parser = parseFile("/service-callbacks.yml");

        List<CallbackInfo.RingCallbackInfo> expected = Arrays.asList(
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.ECHistogramReport",
                                     new CallbackInfo.RingTopic(null, null, null)),
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.ECHistogramReport",
                                     new CallbackInfo.RingTopic("histogram", null, null)),
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.ECHistogramReport",
                                     new CallbackInfo.RingTopic("histogram", "clas12_group1", null)),
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.ECDataReport",
                                     new CallbackInfo.RingTopic("data_filter", "clas12_group1", "ECMonitor")),
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.DpeRegReport",
                                     new CallbackInfo.RingTopic(null, null, null)),
                new CallbackInfo.RingCallbackInfo("org.jlab.clas12.callbacks.DpeRunReport",
                                     new CallbackInfo.RingTopic(null, "clas12_group1", null))
        );

        assertThat(parser.parseDataRingCallbacks(), is(expected));
    }


    @Test
    public void parseLanguagesSingleLang() throws Exception {
        OrchestratorConfigParser parser = parseFile("/services-ok.yml");

        assertThat(parser.parseLanguages(), containsInAnyOrder(ErsapLang.JAVA));
    }


    @Test
    public void parseLanguagesMultiLang() throws Exception {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        assertThat(parser.parseLanguages(), containsInAnyOrder(ErsapLang.JAVA, ErsapLang.CPP));
    }


    @Test
    public void parseEmptyMimeTypes() {
        OrchestratorConfigParser parser = parseFile("/services-ok.yml");

        assertThat(parser.parseDataTypes(), is(empty()));
    }


    @Test
    public void parseUserDefinedMimeTypes() {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        String[] expected = new String[] {"binary/data-evio", "binary/data-hipo"};
        assertThat(parser.parseDataTypes(), containsInAnyOrder(expected));
    }


    @Test
    public void parseEmptyConfig() {
        OrchestratorConfigParser parser = parseFile("/services-ok.yml");

        JSONObject config = parser.parseConfiguration();

        assertThat(config.toString(), is("{}"));
    }


    @Test
    public void parseCustomConfig() {
        OrchestratorConfigParser parser = parseFile("/services-custom.yml");

        JSONObject config = parser.parseConfiguration();

        assertThat(config.keySet(), containsInAnyOrder("global", "io-services", "services"));

        assertThat(config.getJSONObject("global").keySet(),
                   containsInAnyOrder("ccdb", "magnet", "kalman"));
        assertThat(config.getJSONObject("io-services").keySet(),
                   containsInAnyOrder("reader", "writer"));
        assertThat(config.getJSONObject("services").keySet(),
                   containsInAnyOrder("ECReconstruction", "HeaderFilter"));
    }


    @Test
    public void parseInputFilesList() {
        URL files = getClass().getResource("/files.list");

        List<String> expected = Arrays.asList("file1.ev", "file2.ev", "file3.ev",
                                              "file4.ev", "file5.ev");

        assertThat(OrchestratorConfigParser.readInputFiles(files.getPath()), is(expected));
    }


    private OrchestratorConfigParser parseFile(String resource) {
        URL path = getClass().getResource(resource);
        return new OrchestratorConfigParser(path.getPath());
    }


    private static ServiceInfo service(String classPath, String name) {
        return new ServiceInfo(classPath, CONT, name, ErsapLang.JAVA);
    }
}
