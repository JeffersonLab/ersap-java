/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.ErsapSubscriptions.BaseSubscription;
import org.jlab.coda.xmsg.core.xMsgCallBack;
import org.jlab.coda.xmsg.core.xMsgSubscription;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ErsapSubscriptionsTest {

    private static final ErsapComponent FRONT_END = ErsapComponent.dpe("10.2.9.1_java");

    private ErsapBase baseMock;
    private EngineCallback callback;
    private Map<String, xMsgSubscription> subscriptions;

    @BeforeEach
    public void setUp() throws Exception {
        baseMock = mock(ErsapBase.class);
        callback = mock(EngineCallback.class);
        subscriptions = new HashMap<>();
    }


    @Test
    public void startSubscriptionUsesFrontEnd() throws Exception {
        build("data:10.2.9.96_java:master:Simple").start(callback);

        ArgumentCaptor<ErsapComponent> compArg = ArgumentCaptor.forClass(ErsapComponent.class);
        verify(baseMock).listen(compArg.capture(), any(), any());
        assertThat(compArg.getValue().getCanonicalName(), is(FRONT_END.getCanonicalName()));
    }


    @Test
    public void startSubscriptionMatchesTopic() throws Exception {
        String topic = "data:10.2.9.96_java:master:Simple";

        build(topic).start(callback);

        verify(baseMock).listen(any(), eq(xMsgTopic.wrap(topic)), any());
    }


    @Test
    public void startSubscriptionWrapsUserCallback() throws Exception {
        TestSubscription sub = build("data:10.2.9.96_java:master:Simple");

        xMsgCallBack xcb = mock(xMsgCallBack.class);
        when(sub.wrap(eq(callback))).thenReturn(xcb);

        sub.start(callback);

        verify(baseMock).listen(any(), any(), eq(xcb));
    }


    @Test
    public void startSubscriptionThrowsOnFailure() throws Exception {
        TestSubscription subscription = build("data:10.2.9.96_java:master:Simple");

        Mockito.doThrow(ErsapException.class).when(baseMock).listen(any(), any(), any());

        assertThrows(ErsapException.class, () -> subscription.start(callback));
    }


    @Test
    public void startSubscriptionStoresSubscriptionHandler() throws Exception {
        String key = "10.2.9.1#ERROR:10.2.9.96_java:master:Simple";
        xMsgSubscription handler = mock(xMsgSubscription.class);
        when(baseMock.listen(any(), any(), any())).thenReturn(handler);

        build("ERROR:10.2.9.96_java:master:Simple").start(callback);

        assertThat(subscriptions, hasEntry(key, handler));
    }


    @Test
    public void startSubscriptionThrowsOnDuplicatedSubscription() throws Exception {
        TestSubscription sub1 = build("data:10.2.9.96_java:master:Simple");
        TestSubscription sub2 = build("data:10.2.9.96_java:master:Simple");

        sub1.start(callback);

        assertThrows(IllegalStateException.class, () -> sub2.start(callback));
    }


    @Test
    public void stopSubscriptionUsesHandler() throws Exception {
        xMsgSubscription handler = mock(xMsgSubscription.class);
        when(baseMock.listen(eq(FRONT_END),
                             eq(xMsgTopic.wrap("ERROR:10.2.9.96_java:master:Simple")),
                             any())).thenReturn(handler);
        build("ERROR:10.2.9.96_java:master:Simple").start(callback);
        build("WARNING:10.2.9.96_java:master:Simple").start(callback);

        build("ERROR:10.2.9.96_java:master:Simple").stop();

        verify(baseMock).unsubscribe(eq(handler));
    }


    @Test
    public void stopSubscriptionRemovesSubscriptionHandler() throws Exception {
        build("ERROR:10.2.9.96_java:master:Simple").start(callback);
        build("WARNING:10.2.9.96_java:master:Simple").start(callback);
        build("INFO:10.2.9.96_java:master:Simple").start(callback);

        build("ERROR:10.2.9.96_java:master:Simple").stop();

        assertThat(subscriptions, not(hasKey("10.2.9.1#ERROR:10.2.9.96_java:master:Simple")));
    }


    private TestSubscription build(String topic) {
        return spy(new TestSubscription(baseMock, subscriptions, FRONT_END, xMsgTopic.wrap(topic)));
    }


    public static class TestSubscription
            extends BaseSubscription<TestSubscription, EngineCallback> {

        TestSubscription(ErsapBase base,
                         Map<String, xMsgSubscription> subscriptions,
                         ErsapComponent frontEnd,
                         xMsgTopic topic) {
            super(base, subscriptions, frontEnd, topic);
        }

        @Override
        protected xMsgCallBack wrap(EngineCallback callback) {
            return mock(xMsgCallBack.class);
        }
    }
}
