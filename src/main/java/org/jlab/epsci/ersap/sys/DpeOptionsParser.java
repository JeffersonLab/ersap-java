/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.jlab.epsci.ersap.util.OptUtils;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

/**
 * Parses the DPE settings from the command line.
 */
class DpeOptionsParser {

    private final OptionSpec<String> dpeHost;
    private final OptionSpec<Integer> dpePort;
    private final OptionSpec<String> feHost;
    private final OptionSpec<Integer> fePort;

    private final OptionSpec<String> session;

    private final OptionSpec<Integer> poolSize;
    private final OptionSpec<Integer> maxCores;
    private final OptionSpec<Long> reportPeriod;

    private final OptionSpec<Integer> maxSockets;
    private final OptionSpec<Integer> ioThreads;

    private final OptionSpec<String> description;

    private OptionParser parser;
    private OptionSet options;

    private boolean fe;
    private xMsgProxyAddress localAddress;
    private xMsgProxyAddress frontEndAddress;


    DpeOptionsParser() {
        parser = new OptionParser();

        parser.acceptsAll(asList("fe", "frontend"));

        dpeHost = parser.acceptsAll(asList("host")).withRequiredArg();
        dpePort = parser.acceptsAll(asList("port"))
                        .withRequiredArg().ofType(Integer.class);

        feHost = parser.acceptsAll(asList("fe-host")).withRequiredArg();
        fePort = parser.acceptsAll(asList("fe-port"))
                       .withRequiredArg().ofType(Integer.class);

        session = parser.accepts("session").withRequiredArg();

        poolSize = parser.accepts("poolsize").withRequiredArg().ofType(Integer.class);
        maxCores = parser.accepts("max-cores").withRequiredArg().ofType(Integer.class);
        reportPeriod = parser.accepts("report").withRequiredArg().ofType(Long.class);

        maxSockets = parser.accepts("max-sockets").withRequiredArg().ofType(Integer.class);
        ioThreads = parser.accepts("io-threads").withRequiredArg().ofType(Integer.class);

        description = parser.accepts("description").withRequiredArg();

        parser.acceptsAll(asList("version"));
        parser.acceptsAll(asList("h", "help")).forHelp();
    }

    public void parse(String[] args) {
        try {
            options = parser.parse(args);

            // Act as front-end by default but if feHost or fePort are passed
            // act as a worker DPE with remote front-end
            fe = !options.has(feHost) && !options.has(fePort);

            // Get local DPE address
            String localHost = valueOf(dpeHost, Dpe.DEFAULT_PROXY_HOST);
            int localPort = valueOf(dpePort, Dpe.DEFAULT_PROXY_PORT);
            localAddress = new xMsgProxyAddress(localHost, localPort);

            if (fe) {
                // Get local FE address (use same local DPE address)
                frontEndAddress = localAddress;
            } else {
                // Get remote FE address
                if (!options.has(feHost)) {
                    error("The remote front-end host is required");
                }
                String host = options.valueOf(feHost);
                int port = valueOf(fePort, Dpe.DEFAULT_PROXY_PORT);
                frontEndAddress = new xMsgProxyAddress(host, port);
            }

        } catch (OptionException e) {
            throw new DpeOptionsException(e);
        }
    }

    private void error(String msg) {
        throw new DpeOptionsException(msg);
    }

    private <V> V valueOf(OptionSpec<V> spec, V defaultValue) {
        try {
            if (options.has(spec)) {
                return options.valueOf(spec);
            }
            return defaultValue;
        } catch (OptionException e) {
            throw new DpeOptionsException(e);
        }
    }

    public xMsgProxyAddress localAddress() {
        return localAddress;
    }

    public xMsgProxyAddress  frontEnd() {
        return frontEndAddress;
    }

    public String session() {
        return valueOf(session, "");
    }

    public DpeConfig config() {
        int dpeMaxCores = valueOf(maxCores, Dpe.DEFAULT_MAX_CORES);
        int dpePoolSize = valueOf(poolSize, DpeConfig.calculatePoolSize(dpeMaxCores));

        long defaultPeriodSeconds = TimeUnit.MILLISECONDS.toSeconds(Dpe.DEFAULT_REPORT_PERIOD);
        long reportPeriodSeconds = valueOf(reportPeriod, defaultPeriodSeconds);
        long dpeReportPeriod = TimeUnit.SECONDS.toMillis(reportPeriodSeconds);

        return new DpeConfig(dpeMaxCores, dpePoolSize, dpeReportPeriod);
    }

    public int maxSockets() {
        return valueOf(maxSockets, Dpe.DEFAULT_MAX_SOCKETS);
    }

    public int ioThreads() {
        return valueOf(ioThreads, Dpe.DEFAULT_IO_THREADS);
    }

    public String description() {
        return valueOf(description, "");
    }

    public boolean isFrontEnd() {
        return fe;
    }

    public boolean hasVersion() {
        return options.has("version");
    }

    public boolean hasHelp() {
        return options.has("help");
    }

    public String usage() {
        return String.format("usage: j_dpe [options]%n%n  Options:%n")
             + OptUtils.optionHelp(dpeHost, "hostname", "use given host for this DPE")
             + OptUtils.optionHelp(dpePort, "port", "use given port for this DPE")
             + OptUtils.optionHelp(feHost, "hostname", "the host used by the remote front-end")
             + OptUtils.optionHelp(fePort, "port", "the port used by the remote front-end")
             + OptUtils.optionHelp(session, "id", "the session ID of this DPE")
             + OptUtils.optionHelp(description, "string", "a short description of this DPE")
             + String.format("%n  Config options:%n")
             + OptUtils.optionHelp(poolSize, "size", "size of thread pool to handle requests")
             + OptUtils.optionHelp(maxCores, "cores", "how many cores can be used by a service")
             + OptUtils.optionHelp(reportPeriod, "seconds", "the period to publish reports")
             + String.format("%n  Advanced options:%n")
             + OptUtils.optionHelp(maxSockets, "sockets", "maximum number of allowed ZMQ sockets")
             + OptUtils.optionHelp(ioThreads, "threads", "size of ZMQ thread pool to handle I/O");
    }

    static class DpeOptionsException extends RuntimeException {

        DpeOptionsException(String message) {
            super(message);
        }

        DpeOptionsException(String message, Throwable cause) {
            super(message, cause);
        }

        DpeOptionsException(Throwable cause) {
            super(cause);
        }
    }
}
