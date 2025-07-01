/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.sys.DpeOptionsParser.DpeOptionsException;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DpeOptionsParserTest {

    private static final String DPE_HOST_OPT = "--host";
    private static final String DPE_PORT_OPT = "--port";

    private static final String FE_HOST_OPT = "--fe-host";
    private static final String FE_PORT_OPT = "--fe-port";

    private static final String DESC_OPT = "--description";

    private static final String POOL_OPT = "--poolsize";
    private static final String CORES_OPT = "--max-cores";
    private static final String REPORT_OPT = "--report";

    private static final String SOCKETS_OPT = "--max-sockets";
    private static final String IO_THREADS_OPT = "--io-threads";

    private static final String DEFAULT_HOST = Dpe.DEFAULT_PROXY_HOST;

    private DpeOptionsParser parser;


    @BeforeEach
    public void setUp() throws Exception {
        parser = new DpeOptionsParser();
    }


    @Test
    public void dpeIsFrontEndByDefault() throws Exception {
        parse();

        assertTrue(parser.isFrontEnd());
    }

    @Test
    public void dpeIsWorkerIfReceivesFrontEndHost() throws Exception {
        parse(FE_HOST_OPT, "10.2.9.100");

        assertFalse(parser.isFrontEnd());
    }

    @Test
    public void workerUsesDefaultLocalAddress() throws Exception {
        parse(FE_HOST_OPT, "10.2.9.100");

        assertThat(parser.localAddress(), is(proxy(DEFAULT_HOST)));
    }

    @Test
    public void workerReceivesOptionalLocalHost() throws Exception {
        parse(DPE_HOST_OPT, "10.2.9.4", FE_HOST_OPT, "10.2.9.100");

        assertThat(parser.localAddress(), is(proxy("10.2.9.4")));
    }

    @Test
    public void workerReceivesOptionalLocalPort() throws Exception {
        parse(DPE_PORT_OPT, "8500", FE_HOST_OPT, "10.2.9.100");

        assertThat(parser.localAddress(), is(proxy(DEFAULT_HOST, 8500)));
    }

    @Test
    public void workerReceivesOptionalLocalHostAndPort() throws Exception {
        parse(DPE_HOST_OPT, "10.2.9.4", DPE_PORT_OPT, "8500", FE_HOST_OPT, "10.2.9.100");

        assertThat(parser.localAddress(), is(proxy("10.2.9.4", 8500)));
        assertThat(parser.frontEnd(), is(proxy("10.2.9.100")));
    }

    @Test
    public void workerReceivesRemoteFrontEndAddress() throws Exception {
        parse(FE_HOST_OPT, "10.2.9.100");

        assertThat(parser.frontEnd(), is(proxy("10.2.9.100")));
    }

    @Test
    public void workerReceivesRemoteFrontEndAddressAndPort() throws Exception {
        parse(FE_HOST_OPT, "10.2.9.100", FE_PORT_OPT, "9000");

        assertThat(parser.frontEnd(), is(proxy("10.2.9.100", 9000)));
    }

    @Test
    public void workerRequiresRemoteFrontEndHostWhenPortIsGiven() throws Exception {
        Exception ex = assertThrows(DpeOptionsException.class, () -> parse(FE_PORT_OPT, "9000"));
        assertThat(ex.getMessage(), is("The remote front-end host is required"));
    }

    @Test
    public void frontEndUsesDefaultLocalAddress() throws Exception {
        parse();

        assertThat(parser.localAddress(), is(proxy(DEFAULT_HOST)));
        assertThat(parser.frontEnd(), is(proxy(DEFAULT_HOST)));
    }

    @Test
    public void frontEndReceivesOptionalLocalHost() throws Exception {
        parse(DPE_HOST_OPT, "10.2.9.100");

        assertThat(parser.localAddress(), is(proxy("10.2.9.100")));
        assertThat(parser.frontEnd(), is(proxy("10.2.9.100")));
    }

    @Test
    public void frontEndReceivesOptionalLocalPort() throws Exception {
        parse(DPE_PORT_OPT, "8500");

        assertThat(parser.localAddress(), is(proxy(DEFAULT_HOST, 8500)));
        assertThat(parser.frontEnd(), is(proxy(DEFAULT_HOST, 8500)));
    }

    @Test
    public void frontEndReceivesOptionalLocalHostAndPort() throws Exception {
        parse(DPE_HOST_OPT, "10.2.9.100", DPE_PORT_OPT, "8500");

        assertThat(parser.localAddress(), is(proxy("10.2.9.100", 8500)));
        assertThat(parser.frontEnd(), is(proxy("10.2.9.100", 8500)));
    }

    @Test
    public void dpeUsesDefaultEmptyDescription() throws Exception {
        parse();

        assertThat(parser.description(), is(""));
    }

    @Test
    public void dpeReceivesOptionalDescription() throws Exception {
        parse(DESC_OPT, "A processing DPE");

        assertThat(parser.description(), is("A processing DPE"));
    }

    @Test
    public void dpeUsesDefaultPoolSize() throws Exception {
        parse();

        assertThat(parser.config().poolSize(), is(Dpe.DEFAULT_POOL_SIZE));
    }

    @Test
    public void dpeReceivesOptionalPoolSize() throws Exception {
        parse(POOL_OPT, "10");

        assertThat(parser.config().poolSize(), is(10));
    }

    @Test
    public void dpeUsesDefaultMaxCores() throws Exception {
        parse();

        assertThat(parser.config().maxCores(), is(Dpe.DEFAULT_MAX_CORES));
    }

    @Test
    public void dpeReceivesOptionalMaxCores() throws Exception {
        parse(CORES_OPT, "64");

        assertThat(parser.config().maxCores(), is(64));
    }

    @Test
    public void dpeUsesDefaultReportPeriod() throws Exception {
        parse();

        assertThat(parser.config().reportPeriod(), is(Dpe.DEFAULT_REPORT_PERIOD));
    }

    @Test
    public void dpeReceivesOptionalReportPeriod() throws Exception {
        parse(REPORT_OPT, "20");

        assertThat(parser.config().reportPeriod(), is(20_000L));
    }

    @Test
    public void dpeUsesDefaultMaxSockets() throws Exception {
        parse();

        assertThat(parser.maxSockets(), is(Dpe.DEFAULT_MAX_SOCKETS));
    }

    @Test
    public void dpeReceivesOptionalMaxSockets() throws Exception {
        parse(SOCKETS_OPT, "4096");

        assertThat(parser.maxSockets(), is(4096));
    }

    @Test
    public void dpeUsesDefaultIOThreads() throws Exception {
        parse();

        assertThat(parser.ioThreads(), is(Dpe.DEFAULT_IO_THREADS));
    }

    @Test
    public void dpeReceivesOptionalIOThreads() throws Exception {
        parse(IO_THREADS_OPT, "2");

        assertThat(parser.ioThreads(), is(2));
    }


    private void parse(String... args) throws Exception {
        parser.parse(args);
    }

    private xMsgProxyAddress proxy(String host) throws Exception {
        return new xMsgProxyAddress(host, Dpe.DEFAULT_PROXY_PORT);
    }

    private xMsgProxyAddress proxy(String host, int port) throws Exception {
        return new xMsgProxyAddress(host, port);
    }
}
