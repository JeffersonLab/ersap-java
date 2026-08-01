/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.DataRingAddress;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.MonitorOrchestrator;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;

/**
 * The production {@link MonitorConnection}, backed by {@link MonitorOrchestrator}.
 *
 * <p>This is the same wiring {@code org.jlab.epsci.ersap.examples.TestMonitor}
 * uses: a {@link DataRingAddress} pointing at the Monitor FE proxy, a
 * {@link MonitorOrchestrator} built from it, and the two subscriptions
 * {@code listenDpeReports} and {@code listenUserMetrics}. The only additions are
 * the optional engine filter and the "all sessions" mode, both of which are
 * already supported by {@code MonitorOrchestrator}'s overloads.
 */
public final class ErsapMonitorConnection implements MonitorConnection {

    private final MonitorOrchestrator orchestrator;
    private final String session;
    private final String engine;

    /**
     * Opens the connection.
     *
     * @param config the exporter configuration
     */
    public ErsapMonitorConnection(PrometheusExporterConfig config) {
        this.session = config.session();
        this.engine = config.engine();
        DataRingAddress address =
                new DataRingAddress(config.monitorHost(), config.monitorPort());
        this.orchestrator = new MonitorOrchestrator(address);
    }

    /**
     * A factory that creates {@link ErsapMonitorConnection} instances.
     *
     * @return the production connection factory
     */
    public static Factory factory() {
        return ErsapMonitorConnection::new;
    }

    @Override
    public void subscribe(DpeReportHandler dpeHandler, UserMetricsHandler userHandler)
            throws ErsapException {
        if (isAllSessions()) {
            orchestrator.listenDpeReports(dpeHandler);
        } else {
            orchestrator.listenDpeReports(session, dpeHandler);
        }
        if (isAllSessions()) {
            orchestrator.listenUserMetrics(userHandler);
        } else if (engine == null || engine.isEmpty()) {
            orchestrator.listenUserMetrics(session, userHandler);
        } else {
            orchestrator.listenUserMetrics(session, engine, userHandler);
        }
    }

    @Override
    public void close() {
        orchestrator.close();
    }

    private boolean isAllSessions() {
        return PrometheusExporterConfig.ALL_SESSIONS.equals(session);
    }
}
