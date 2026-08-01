/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// checkstyle.off: Javadoc
/**
 * An in-process stand-in for a Monitor FE.
 *
 * <p>It plugs into {@link MonitorConnection.Factory}, the seam the exporter uses
 * to reach the data-ring, and can publish the same decoded messages a real
 * Monitor FE subscription delivers — {@link DpeReportHandler#handleReport} and
 * {@link UserMetricsHandler#handleMetrics}. It can also refuse to connect, so
 * the reconnection path can be exercised without a running data-ring.
 */
final class FakeMonitorFe implements MonitorConnection.Factory {

    private final AtomicReference<FakeConnection> current = new AtomicReference<>();
    private final AtomicInteger connectAttempts = new AtomicInteger();
    private final AtomicInteger closedConnections = new AtomicInteger();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    /** Makes the next {@code count} connection attempts fail. */
    void failNextConnections(int count) {
        failuresLeft.set(count);
    }

    int connectAttempts() {
        return connectAttempts.get();
    }

    int closedConnections() {
        return closedConnections.get();
    }

    @Override
    public MonitorConnection connect(PrometheusExporterConfig config) throws ErsapException {
        connectAttempts.incrementAndGet();
        if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new ErsapException("fake Monitor FE is down");
        }
        FakeConnection connection = new FakeConnection();
        current.set(connection);
        return connection;
    }

    /**
     * Waits until the exporter has subscribed.
     *
     * @return true if a subscription is active
     */
    boolean awaitSubscription(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            FakeConnection connection = current.get();
            if (connection != null && connection.subscribed()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return false;
    }

    void publishDpeReport(DpeRegistrationData registration, DpeRuntimeData runtime) {
        connection().dpeHandler.handleReport(registration, runtime);
    }

    void publishUserMetrics(String session, String engine, Map<String, Object> metrics) {
        connection().userHandler.handleMetrics(session, engine, metrics);
    }

    private FakeConnection connection() {
        FakeConnection connection = current.get();
        if (connection == null || !connection.subscribed()) {
            throw new IllegalStateException("nothing is subscribed to the fake Monitor FE");
        }
        return connection;
    }

    /** One "live" subscription to the fake Monitor FE. */
    private final class FakeConnection implements MonitorConnection {

        private volatile DpeReportHandler dpeHandler;
        private volatile UserMetricsHandler userHandler;
        private volatile boolean closed;

        @Override
        public void subscribe(DpeReportHandler dpe, UserMetricsHandler user) {
            this.dpeHandler = dpe;
            this.userHandler = user;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                closedConnections.incrementAndGet();
            }
            current.compareAndSet(this, null);
        }

        boolean subscribed() {
            return !closed && dpeHandler != null && userHandler != null;
        }
    }
}
