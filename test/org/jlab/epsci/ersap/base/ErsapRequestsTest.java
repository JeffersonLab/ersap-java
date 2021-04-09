/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.ErsapRequests.BaseRequest;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ErsapRequestsTest {

    private static final ErsapComponent FRONT_END = ErsapComponent.dpe("10.2.9.1_java");
    private static final String TOPIC = "dpe:10.2.9.6_java";

    private ErsapBase baseMock;
    private TestRequest request;

    @BeforeEach
    public void setUp() throws Exception {
        baseMock = mock(ErsapBase.class);
        request = spy(new TestRequest(baseMock, FRONT_END, TOPIC));
    }


    @Test
    public void requestIsSentToFrontEnd() throws Exception {
        request.run();

        ArgumentCaptor<ErsapComponent> compArg = ArgumentCaptor.forClass(ErsapComponent.class);
        verify(baseMock).send(compArg.capture(), any(xMsgMessage.class));

        assertThat(compArg.getValue().getDpeCanonicalName(), is(FRONT_END.getDpeCanonicalName()));
    }


    @Test
    public void requestIsSentWithMessage() throws Exception {
        request.run();

        verify(request).msg();
    }


    @Test
    public void requestThrowsOnSendFailure() throws Exception {
        doThrow(xMsgException.class).when(baseMock)
                .send(any(ErsapComponent.class), any(xMsgMessage.class));

        assertThrows(ErsapException.class, () -> request.run());
    }


    @Test
    public void requestThrowsOnMessageFailure() throws Exception {
        doThrow(ErsapException.class).when(request).msg();

        assertThrows(ErsapException.class, () -> request.run());
    }


    @Test
    public void syncRequestIsSentToFrontEnd() throws Exception {
        request.syncRun(10, TimeUnit.SECONDS);

        ArgumentCaptor<ErsapComponent> compArg = ArgumentCaptor.forClass(ErsapComponent.class);
        verify(baseMock).syncSend(compArg.capture(), any(xMsgMessage.class), anyLong());

        assertThat(compArg.getValue().getDpeCanonicalName(), is(FRONT_END.getDpeCanonicalName()));
    }


    @Test
    public void syncRequestIsSentWithMessage() throws Exception {
        request.syncRun(10, TimeUnit.SECONDS);

        verify(request).msg();
    }


    @Test
    public void syncRequestIsSentWithTimeoutInMillis() throws Exception {
        request.syncRun(20, TimeUnit.MILLISECONDS);
        verify(baseMock).syncSend(any(ErsapComponent.class), any(xMsgMessage.class), eq(20L));
    }


    @Test
    public void syncRequestIsSentWithTimeoutInOtherUnit() throws Exception {
        request.syncRun(10, TimeUnit.SECONDS);
        verify(baseMock).syncSend(any(ErsapComponent.class), any(xMsgMessage.class), eq(10000L));
    }


    @Test
    public void syncRequestParsesResponse() throws Exception {
        xMsgMessage response = mock(xMsgMessage.class);
        when(baseMock.syncSend(any(ErsapComponent.class), any(xMsgMessage.class), anyLong()))
              .thenReturn(response);

        request.syncRun(10, TimeUnit.SECONDS);

        verify(request).parseData(eq(response));
    }


    @Test
    public void syncRequestReturnsResponse() throws Exception {
        when(request.parseData(any())).thenReturn("test_response");

        assertThat(request.syncRun(10, TimeUnit.SECONDS), is("test_response"));
    }


    @Test
    public void syncRequestThrowsOnSendFailure() throws Exception {
        doThrow(xMsgException.class).when(baseMock)
                .syncSend(any(ErsapComponent.class), any(xMsgMessage.class), anyLong());

        assertThrows(ErsapException.class, () -> request.syncRun(10, TimeUnit.SECONDS));
    }


    @Test
    public void syncRequestThrowsOnMessageFailure() throws Exception {
        doThrow(ErsapException.class).when(request).msg();

        assertThrows(ErsapException.class, () -> request.syncRun(10, TimeUnit.SECONDS));
    }


    @Test
    public void syncRequestThrowsOnResponseFailure() throws Exception {
        doThrow(ErsapException.class).when(request).parseData(any());

        assertThrows(ErsapException.class, () -> request.syncRun(10, TimeUnit.SECONDS));
    }


    @Test
    public void syncRequestThrowsOnTimeout() throws Exception {
        doThrow(TimeoutException.class).when(baseMock)
                .syncSend(any(ErsapComponent.class), any(xMsgMessage.class), anyLong());

        assertThrows(TimeoutException.class, () -> request.syncRun(10, TimeUnit.SECONDS));
    }



    public static class TestRequest extends BaseRequest<TestRequest, String> {

        TestRequest(ErsapBase base, ErsapComponent frontEnd, String topic) {
            super(base, frontEnd, topic);
        }

        @Override
        protected xMsgMessage msg() throws ErsapException {
            return mock(xMsgMessage.class);
        }

        @Override
        protected String parseData(xMsgMessage msg) throws ErsapException {
            return "";
        }
    }
}
