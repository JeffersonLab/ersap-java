/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.ErsapRequests.BaseRequest;
import org.jlab.epsci.ersap.base.ErsapSubscriptions.BaseSubscription;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.ParametersAreNonnullByDefault;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseOrchestratorTest {

    private static final String FE_HOST = "10.2.9.1_java";

    private static final Composition COMPOSITION =
            new Composition("10.2.9.96_java:master:E1+10.2.9.96_java:master:E2;");

    private ErsapBase baseMock;
    private BaseOrchestrator orchestrator;

    private BaseRequest<?, ?> request;
    private BaseSubscription<?, ?> subscription;

    @BeforeEach
    public void setUp() throws Exception {
        baseMock = mock(ErsapBase.class);
        orchestrator = new OrchestratorMock();

        when(baseMock.getFrontEnd()).thenReturn(ErsapComponent.dpe(FE_HOST));
        when(baseMock.getName()).thenReturn("test_orchestrator");
    }


    @Test
    public void exitDpe() throws Exception {
        DpeName dpe = new DpeName("10.2.9.96_java");
        request = orchestrator.exit(dpe);

        assertRequest("10.2.9.96", "dpe:10.2.9.96_java", "stopDpe");
    }



    @Test
    public void deployContainer() throws Exception {
        ContainerName container = new ContainerName("10.2.9.96_java:master");
        request = orchestrator.deploy(container).withPoolsize(5);

        assertRequest("10.2.9.96", "dpe:10.2.9.96_java", "startContainer?master?5?undefined");
    }


    @Test
    public void exitContainer() throws Exception {
        ContainerName container = new ContainerName("10.2.9.96_java:master");
        request = orchestrator.exit(container);

        assertRequest("10.2.9.96", "dpe:10.2.9.96_java", "stopContainer?master");
    }



    @Test
    public void deployService() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.deploy(service, "org.example.service.E1").withPoolsize(10);

        assertRequest("10.2.9.96", "dpe:10.2.9.96_java",
                "startService?master?E1?org.example.service.E1?10?undefined?undefined");
    }


    @Test
    public void exitService() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.exit(service);

        assertRequest("10.2.9.96", "dpe:10.2.9.96_java", "stopService?master?E1");
    }



    @Test
    public void configureService() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        EngineData data = new EngineData();
        data.setData(EngineDataType.STRING.mimeType(), "example");

        request = orchestrator.configure(service)
                              .withData(data)
                              .withDataTypes(EngineDataType.STRING);

        assertRequest("10.2.9.96",
                       "10.2.9.96_java:master:E1",
                       "10.2.9.96_java:master:E1;",
                       xMsgMeta.ControlAction.CONFIGURE);
    }



    @Test
    public void executeService() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        EngineData data = new EngineData();
        data.setData(EngineDataType.STRING.mimeType(), "example");

        request = orchestrator.execute(service)
                              .withData(data)
                              .withDataTypes(EngineDataType.STRING);

        assertRequest("10.2.9.96",
                       "10.2.9.96_java:master:E1",
                       "10.2.9.96_java:master:E1;",
                       xMsgMeta.ControlAction.EXECUTE);
    }


    @Test
    public void executeComposition() throws Exception {
        EngineData data = new EngineData();
        data.setData(EngineDataType.STRING.mimeType(), "example");

        request = orchestrator.execute(COMPOSITION)
                              .withData(data)
                              .withDataTypes(EngineDataType.STRING);

        assertRequest("10.2.9.96",
                       "10.2.9.96_java:master:E1",
                       "10.2.9.96_java:master:E1+10.2.9.96_java:master:E2;",
                       xMsgMeta.ControlAction.EXECUTE);
    }



    @Test
    public void startReportingDone() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.configure(service).startDoneReporting(1000);

        assertRequest("10.2.9.96", "10.2.9.96_java:master:E1", "serviceReportDone?1000");
    }


    @Test
    public void stopReportingDone() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.configure(service).stopDoneReporting();

        assertRequest("10.2.9.96", "10.2.9.96_java:master:E1", "serviceReportDone?0");
    }



    @Test
    public void startReportingData() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.configure(service).startDataReporting(1000);

        assertRequest("10.2.9.96", "10.2.9.96_java:master:E1", "serviceReportData?1000");
    }


    @Test
    public void stopReportingData() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:E1");
        request = orchestrator.configure(service).stopDataReporting();

        assertRequest("10.2.9.96", "10.2.9.96_java:master:E1", "serviceReportData?0");
    }



    @Test
    public void listenServiceStatus() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:SimpleEngine");
        subscription = orchestrator.listen(service).status(EngineStatus.ERROR);

        assertSubscription("ERROR:10.2.9.96_java:master:SimpleEngine");
    }


    @Test
    public void listenServiceData() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:SimpleEngine");
        subscription = orchestrator.listen(service).data();

        assertSubscription("data:10.2.9.96_java:master:SimpleEngine");
    }


    @Test
    public void listenServiceDone() throws Exception {
        ServiceName service = new ServiceName("10.2.9.96_java:master:SimpleEngine");
        subscription = orchestrator.listen(service).done();

        assertSubscription("done:10.2.9.96_java:master:SimpleEngine");
    }



    @Test
    public void listenDpesAlive() throws Exception {
        subscription = orchestrator.listen().aliveDpes();

        assertSubscription("dpeAlive:");
    }


    @Test
    public void listenDpesAliveWithSession() throws Exception {
        subscription = orchestrator.listen().aliveDpes("");
        assertSubscription("dpeAlive::");

        subscription = orchestrator.listen().aliveDpes("*");
        assertSubscription("dpeAlive:");

        subscription = orchestrator.listen().aliveDpes("foobar");
        assertSubscription("dpeAlive:foobar:");

        subscription = orchestrator.listen().aliveDpes("foobar*");
        assertSubscription("dpeAlive:foobar");
    }



    @Test
    public void listenDpesReport() throws Exception {
        subscription = orchestrator.listen().dpeReport();

        assertSubscription("dpeReport:");
    }


    @Test
    public void listenDpesReportWithSession() throws Exception {
        subscription = orchestrator.listen().dpeReport("");
        assertSubscription("dpeReport::");

        subscription = orchestrator.listen().dpeReport("*");
        assertSubscription("dpeReport:");

        subscription = orchestrator.listen().dpeReport("foobar");
        assertSubscription("dpeReport:foobar:");

        subscription = orchestrator.listen().dpeReport("foobar*");
        assertSubscription("dpeReport:foobar");
    }



    @Test
    public void listenDataRing() throws Exception {
        subscription = orchestrator.listen().dataRing();

        assertSubscription("ring:");
    }


    @Test
    public void listenDataRingWithTopic() throws Exception {
        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo"));
        assertSubscription("ring:foo:");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo*"));
        assertSubscription("ring:foo");


        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", ""));
        assertSubscription("ring:foo::");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "bar"));
        assertSubscription("ring:foo:bar:");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "bar*"));
        assertSubscription("ring:foo:bar");


        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "", "liz"));
        assertSubscription("ring:foo::liz");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "", "liz*"));
        assertSubscription("ring:foo::liz");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "bar", "liz"));
        assertSubscription("ring:foo:bar:liz");

        subscription = orchestrator.listen().dataRing(new DataRingTopic("foo", "bar", "liz*"));
        assertSubscription("ring:foo:bar:liz");
    }



    private void assertRequest(String host, String topic, String data) throws Exception {
        assertThat(request.frontEnd.getDpeHost(), is(host));
        assertMessage(request.msg(), topic, data);
    }


    private void assertRequest(String host, String topic,
                                String composition, xMsgMeta.ControlAction action)
            throws Exception {
        assertThat(request.frontEnd.getDpeHost(), is(host));
        assertMessage(request.msg(), topic, composition, action);
    }


    private void assertMessage(xMsgMessage msg, String topic, String data)
            throws Exception {
        xMsgMeta.Builder msgMeta = msg.getMetaData();
        String msgData = new String(msg.getData());

        assertThat(msg.getTopic().toString(), is(topic));
        assertThat(msgMeta.getAuthor(), is("test_orchestrator"));
        assertThat(msgData, is(data));
    }


    private void assertMessage(xMsgMessage msg, String topic,
                               String composition, xMsgMeta.ControlAction action) {
        xMsgMeta.Builder msgMeta = msg.getMetaData();

        assertThat(msg.getTopic().toString(), is(topic));
        assertThat(msgMeta.getAuthor(), is("test_orchestrator"));
        assertThat(msgMeta.getComposition(), is(composition));
        assertThat(msgMeta.getAction(), is(action));
    }


    private void assertSubscription(String topic) throws Exception {
        assertThat(subscription.frontEnd.getDpeCanonicalName(), is(FE_HOST));
        assertThat(subscription.topic, is(xMsgTopic.wrap(topic)));
    }



    @ParametersAreNonnullByDefault
    private class OrchestratorMock extends BaseOrchestrator {

        OrchestratorMock() {
            super();
        }

        @Override
        ErsapBase getErsapBase(String name, DpeName frontEnd, int poolSize) {
            return baseMock;
        }
    }
}
