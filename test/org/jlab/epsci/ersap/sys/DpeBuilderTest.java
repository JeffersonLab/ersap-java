/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.coda.xmsg.net.xMsgProxyAddress;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DpeBuilderTest {

    private static final String DEFAULT_HOST = Dpe.DEFAULT_PROXY_HOST;

    @Test
    public void dpeIsFrontEndByDefault() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertTrue(builder.isFrontEnd);
    }

    @Test
    public void dpeIsWorkerIfReceivesFrontEndHost() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100");

        assertFalse(builder.isFrontEnd);
    }

    @Test
    public void workerUsesDefaultLocalAddress() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100");

        assertThat(builder.localAddress, is(proxy(DEFAULT_HOST)));
    }

    @Test
    public void workerReceivesOptionalLocalHost() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100").withHost("10.2.9.4");

        assertThat(builder.localAddress, is(proxy("10.2.9.4")));
    }

    @Test
    public void workerReceivesOptionalLocalPort() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100").withPort(8500);

        assertThat(builder.localAddress, is(proxy(DEFAULT_HOST, 8500)));
    }

    @Test
    public void workerReceivesOptionalLocalHostAndPort() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100").withHost("10.2.9.4").withPort(8500);

        assertThat(builder.localAddress, is(proxy("10.2.9.4", 8500)));
        assertThat(builder.frontEndAddress, is(proxy("10.2.9.100")));
    }

    @Test
    public void workerReceivesRemoteFrontEndAddress() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100");

        assertThat(builder.frontEndAddress, is(proxy("10.2.9.100")));
    }

    @Test
    public void workerReceivesRemoteFrontEndAddressAndPort() throws Exception {
        Dpe.Builder builder = new Dpe.Builder("10.2.9.100", 9000);

        assertThat(builder.frontEndAddress, is(proxy("10.2.9.100", 9000)));
    }

    @Test
    public void frontEndUsesDefaultLocalAddress() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertThat(builder.localAddress, is(proxy(DEFAULT_HOST)));
        assertThat(builder.frontEndAddress, is(proxy(DEFAULT_HOST)));
    }

    @Test
    public void frontEndReceivesOptionalLocalHost() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withHost("10.2.9.100");

        assertThat(builder.localAddress, is(proxy("10.2.9.100")));
        assertThat(builder.frontEndAddress, is(proxy("10.2.9.100")));
    }

    @Test
    public void frontEndReceivesOptionalLocalPort() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withPort(8500);

        assertThat(builder.localAddress, is(proxy(DEFAULT_HOST, 8500)));
        assertThat(builder.frontEndAddress, is(proxy(DEFAULT_HOST, 8500)));
    }

    @Test
    public void frontEndReceivesOptionalLocalHostAndPort() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withHost("10.2.9.4").withPort(8500);

        assertThat(builder.localAddress, is(proxy("10.2.9.4", 8500)));
        assertThat(builder.frontEndAddress, is(proxy("10.2.9.4", 8500)));
    }

    @Test
    public void dpeUsesDefaultPoolSize() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertThat(builder.poolSize, is(Dpe.DEFAULT_POOL_SIZE));
    }

    @Test
    public void dpeReceivesOptionalPoolSize() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withPoolSize(10);

        assertThat(builder.poolSize, is(10));
    }

    @Test
    public void dpeUsesDefaultMaxCores() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertThat(builder.maxCores, is(Dpe.DEFAULT_MAX_CORES));
    }

    @Test
    public void dpeReceivesOptionalMaxCores() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withMaxCores(64);

        assertThat(builder.maxCores, is(64));
    }

    @Test
    public void dpeUsesDefaultEmptyDescription() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertThat(builder.description, is(""));
    }

    @Test
    public void dpeReceivesOptionalDescription() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withDescription("A processing DPE");

        assertThat(builder.description, is("A processing DPE"));
    }

    @Test
    public void dpeUsesDefaultReportInterval() throws Exception {
        Dpe.Builder builder = new Dpe.Builder();

        assertThat(builder.reportPeriod, is(Dpe.DEFAULT_REPORT_PERIOD));
    }

    @Test
    public void dpeReceivesOptionalReportInterval() throws Exception {
        Dpe.Builder builder = new Dpe.Builder().withReportPeriod(20, TimeUnit.SECONDS);

        assertThat(builder.reportPeriod, is(20_000L));
    }


    private xMsgProxyAddress proxy(String host) throws Exception {
        return new xMsgProxyAddress(host, Dpe.DEFAULT_PROXY_PORT);
    }

    private xMsgProxyAddress proxy(String host, int port) throws Exception {
        return new xMsgProxyAddress(host, port);
    }
}
