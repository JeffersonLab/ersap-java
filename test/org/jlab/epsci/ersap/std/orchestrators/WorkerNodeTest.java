/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.ServiceName;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WorkerNodeTest {

    @Mock
    private CoreOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<DeployInfo> deployCaptor;

    @Captor
    private ArgumentCaptor<DpeName> dpeCaptor;

    @Captor
    private ArgumentCaptor<Set<ServiceName>> namesCaptor;

    private WorkerNode node;


    @BeforeEach
    public void setUp() {
        doReturn(true).when(orchestrator).findServices(any(), any());
    }


    @Test
    public void deployServicesSingleLangSendsAllRequests() throws Exception {
        node = new WorkerNode(orchestrator, SingleLangData.application());

        node.deployServices();

        verify(orchestrator, times(7)).deployService(deployCaptor.capture());

        assertThat(deployCaptor.getAllValues(),
                   containsInAnyOrder(SingleLangData.expectedDeploys()));
    }


    @Test
    public void deployServicesSingleLangChecksSingleDpe() throws Exception {
        node = new WorkerNode(orchestrator, SingleLangData.application());

        node.deployServices();

        verify(orchestrator, times(1)).checkServices(dpeCaptor.capture(), any());

        assertThat(dpeCaptor.getValue(), is(SingleLangData.expectedDpe()));
    }


    @Test
    public void deployServicesSingleLangChecksAllServices() throws Exception {
        node = new WorkerNode(orchestrator, SingleLangData.application());

        node.deployServices();

        verify(orchestrator, times(1)).checkServices(any(), namesCaptor.capture());

        assertThat(namesCaptor.getValue(),
                   containsInAnyOrder(SingleLangData.expectedServices()));
    }


    @Test
    public void checkServicesSingleLangQueriesSingleDpe() throws Exception {
        node = new WorkerNode(orchestrator, SingleLangData.application());

        node.checkServices();

        verify(orchestrator, times(1)).findServices(dpeCaptor.capture(), namesCaptor.capture());

        assertThat(dpeCaptor.getValue(), is(SingleLangData.expectedDpe()));
    }


    @Test
    public void checkServicesSingleLangQueriesAllServices() throws Exception {
        node = new WorkerNode(orchestrator, SingleLangData.application());

        node.checkServices();

        verify(orchestrator, times(1)).findServices(dpeCaptor.capture(), namesCaptor.capture());

        assertThat(namesCaptor.getValue(),
                   containsInAnyOrder(SingleLangData.expectedServices()));
    }


    @Test
    public void deployServicesMultiLangSendsAllRequests() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.deployServices();

        verify(orchestrator, times(8)).deployService(deployCaptor.capture());

        assertThat(deployCaptor.getAllValues(),
                   containsInAnyOrder(MultiLangData.expectedDeploys()));
    }


    @Test
    public void deployServicesMultiLangChecksServicesGroupedByDpe() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.deployServices();

        verify(orchestrator, times(3)).checkServices(dpeCaptor.capture(), namesCaptor.capture());

        for (int i = 0; i < 3; i++) {
            assertThat(flatDpes(namesCaptor.getAllValues().get(i)),
                       contains(dpeCaptor.getAllValues().get(i)));
        }
    }


    @Test
    public void deployServicesMultiLangChecksAllDpes() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.deployServices();

        verify(orchestrator, times(3)).checkServices(dpeCaptor.capture(), any());

        assertThat(dpeCaptor.getAllValues(),
                   containsInAnyOrder(MultiLangData.expectedDpes()));
    }


    @Test
    public void deployServicesMultiLangChecksAllServices() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.deployServices();

        verify(orchestrator, times(3)).checkServices(any(), namesCaptor.capture());

        assertThat(flatServices(namesCaptor.getAllValues()),
                   containsInAnyOrder(MultiLangData.expectedServices()));
    }


    @Test
    public void checkServicesMultiLangQueriesServicesGroupedByDpe() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.checkServices();

        verify(orchestrator, times(3)).findServices(dpeCaptor.capture(), namesCaptor.capture());

        for (int i = 0; i < 3; i++) {
            assertThat(flatDpes(namesCaptor.getAllValues().get(i)),
                       contains(dpeCaptor.getAllValues().get(i)));
        }
    }


    @Test
    public void checkServicesMultiLangQueriesAllDpes() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.checkServices();

        verify(orchestrator, times(3)).findServices(dpeCaptor.capture(), any());

        assertThat(dpeCaptor.getAllValues(),
                   containsInAnyOrder(MultiLangData.expectedDpes()));
    }


    @Test
    public void checkServicesMultiLangQueriesAllServices() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        node.checkServices();

        verify(orchestrator, times(3)).findServices(any(), namesCaptor.capture());

        assertThat(flatServices(namesCaptor.getAllValues()),
                   containsInAnyOrder(MultiLangData.expectedServices()));
    }


    @Test
    public void sendIOConfiguration() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        // mock call
        EngineData events = new EngineData();
        events.setData(EngineDataType.SINT32, 1200);

        EngineData order = new EngineData();
        order.setData("returned_reader_order");

        when(orchestrator.syncSend(any(), any(String.class), anyInt(), any()))
             .thenReturn(events, order);

        // set user configuration
        JSONObject readerConfig = new JSONObject();
        readerConfig.put("block_size", 40000);
        readerConfig.put("action", "unexpected");

        JSONObject writerConfig = new JSONObject();
        writerConfig.put("compression", 2);

        JSONObject ioConfig = new JSONObject();
        ioConfig.put("reader", readerConfig);
        ioConfig.put("writer", writerConfig);

        JSONObject config = new JSONObject();
        config.put("io-services", ioConfig);

        // configure IO services
        WorkerFile file = new WorkerFile("in.dat", "out.dat");
        OrchestratorPaths paths = new OrchestratorPaths
                .Builder("/mnt/data/in.dat", "/mnt/data/out.dat")
                .build();

        node.setConfiguration(config);
        node.setFiles(paths, file);
        node.openFiles();

        // check JSON data
        ArgumentCaptor<JSONObject> configCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(orchestrator, times(2)).syncConfig(any(), configCaptor.capture(), anyInt(), any());

        JSONObject reader = configCaptor.getAllValues().get(0);
        JSONObject writer = configCaptor.getAllValues().get(1);

        assertThat(reader.getString("action"), is("open"));
        assertThat(reader.getString("file"), is("/mnt/data/in.dat"));
        assertThat(reader.getInt("block_size"), is(40000));

        assertThat(writer.getString("action"), is("open"));
        assertThat(writer.getString("file"), is("/mnt/data/out.dat"));
        assertThat(writer.getString("order"), is("returned_reader_order"));
        assertThat(writer.getInt("compression"), is(2));
    }

    @Test
    public void sendGlobalConfiguration() throws Exception {
        node = new WorkerNode(orchestrator, MultiLangData.application());

        JSONObject config = new JSONObject("{ \"global\": { \"foo\": 1, \"bar\": 2 } }");

        node.setConfiguration(config);
        node.configureServices();

        ArgumentCaptor<JSONObject> configCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(orchestrator, times(5)).syncConfig(any(), configCaptor.capture(), anyInt(), any());

        JSONObject globalConfig = config.getJSONObject("global");
        configCaptor.getAllValues().forEach(data -> {
            assertThat(data.similar(globalConfig), is(true));
        });
    }


    private static Set<DpeName> flatDpes(Set<ServiceName> set) {
        return set.stream().map(ServiceName::dpe).collect(Collectors.toSet());
    }


    private static List<ServiceName> flatServices(List<Set<ServiceName>> services) {
        return services.stream().flatMap(Set::stream).collect(Collectors.toList());
    }


    private static class SingleLangData {

        static WorkerApplication application() {
            return AppData.builder()
                          .withServices(AppData.J1, AppData.J2, AppData.K1, AppData.K2)
                          .build();
        }

        static DeployInfo[] expectedDeploys() {
            return new DeployInfo[] {
                deploy("10.1.1.10_java:master:S1", "org.test.S1", 1),
                deploy("10.1.1.10_java:master:R1", "org.test.R1", 1),
                deploy("10.1.1.10_java:master:W1", "org.test.W1", 1),
                deploy("10.1.1.10_java:master:J1", "org.test.J1", AppData.CORES),
                deploy("10.1.1.10_java:master:J2", "org.test.J2", AppData.CORES),
                deploy("10.1.1.10_java:slave:K1", "org.test.K1", AppData.CORES),
                deploy("10.1.1.10_java:slave:K2", "org.test.K2", AppData.CORES),
            };
        }

        static DpeName expectedDpe() {
            return new DpeName("10.1.1.10_java");
        }

        static ServiceName[] expectedServices() {
            return new ServiceName[] {
                new ServiceName("10.1.1.10_java:master:S1"),
                new ServiceName("10.1.1.10_java:master:R1"),
                new ServiceName("10.1.1.10_java:master:W1"),
                new ServiceName("10.1.1.10_java:master:J1"),
                new ServiceName("10.1.1.10_java:master:J2"),
                new ServiceName("10.1.1.10_java:slave:K1"),
                new ServiceName("10.1.1.10_java:slave:K2"),
            };
        }
    }


    private static class MultiLangData {

        static WorkerApplication application() {
            return AppData.builder()
                          .withServices(AppData.J1, AppData.J2, AppData.C1, AppData.C2, AppData.P1)
                          .withDpes(AppData.DPE1, AppData.DPE2, AppData.DPE3)
                          .build();
        }

        static DeployInfo[] expectedDeploys() {
            return new DeployInfo[] {
                deploy("10.1.1.10_java:master:S1", "org.test.S1", 1),
                deploy("10.1.1.10_java:master:R1", "org.test.R1", 1),
                deploy("10.1.1.10_java:master:W1", "org.test.W1", 1),
                deploy("10.1.1.10_java:master:J1", "org.test.J1", AppData.CORES),
                deploy("10.1.1.10_java:master:J2", "org.test.J2", AppData.CORES),
                deploy("10.1.1.10_cpp:master:C1", "org.test.C1", AppData.CORES),
                deploy("10.1.1.10_cpp:master:C2", "org.test.C2", AppData.CORES),
                deploy("10.1.1.10_python:slave:P1", "org.test.P1", AppData.CORES),
            };
        }

        static DpeName[] expectedDpes() {
            return new DpeName[] {
                new DpeName("10.1.1.10_java"),
                new DpeName("10.1.1.10_cpp"),
                new DpeName("10.1.1.10_python")
            };
        }

        static ServiceName[] expectedServices() {
            return new ServiceName[] {
                new ServiceName("10.1.1.10_java:master:S1"),
                new ServiceName("10.1.1.10_java:master:R1"),
                new ServiceName("10.1.1.10_java:master:W1"),
                new ServiceName("10.1.1.10_java:master:J1"),
                new ServiceName("10.1.1.10_java:master:J2"),
                new ServiceName("10.1.1.10_cpp:master:C1"),
                new ServiceName("10.1.1.10_cpp:master:C2"),
                new ServiceName("10.1.1.10_python:slave:P1"),
            };
        }
    }


    private static DeployInfo deploy(String name, String classPath, int poolSize) {
        return new DeployInfo(new ServiceName(name), classPath, poolSize);
    }
}
