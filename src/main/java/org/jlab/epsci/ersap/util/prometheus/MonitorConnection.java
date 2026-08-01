/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;

/**
 * One live set of subscriptions to a Monitor FE.
 *
 * <p>This is the seam that keeps {@link MonitorFeSubscriber} — and therefore the
 * reconnection logic — testable without a running ERSAP data-ring. The
 * production implementation is {@link ErsapMonitorConnection}, which wraps
 * {@code MonitorOrchestrator} exactly as {@code TestMonitor} does.
 */
public interface MonitorConnection extends AutoCloseable {

    /**
     * Subscribes both Monitor FE report streams.
     *
     * @param dpeHandler  receives {@code dpeReport} messages
     * @param userHandler receives {@code userMetrics} messages
     * @throws ErsapException if a subscription could not be started
     */
    void subscribe(DpeReportHandler dpeHandler, UserMetricsHandler userHandler)
            throws ErsapException;

    /**
     * Stops the subscriptions and releases the messaging resources.
     */
    @Override
    void close();

    /**
     * Creates {@link MonitorConnection} instances.
     */
    interface Factory {

        /**
         * Opens a new connection to the Monitor FE described by the configuration.
         *
         * @param config the exporter configuration
         * @return the new connection
         * @throws ErsapException if the connection could not be created
         */
        MonitorConnection connect(PrometheusExporterConfig config) throws ErsapException;
    }
}
