/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;
import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps a Monitor FE subscription alive and feeds the decoded messages to a handler.
 *
 * <p>The subscription itself is the one demonstrated by
 * {@code org.jlab.epsci.ersap.examples.TestMonitor}: {@code dpeReport} for the
 * system metrics and {@code userMetrics} for engine-published values. What this
 * class adds is supervision — a single daemon thread that establishes the
 * subscriptions, watches them, and re-establishes them after a failure.
 *
 * <h2>What "connected" means here</h2>
 *
 * <p>The Monitor FE is a ZeroMQ pub/sub proxy reached through xMsg. Subscribing
 * to a proxy that is down does not fail: the socket simply stays silent and
 * reconnects on its own once the proxy comes back. There is therefore no
 * connection event to observe, and {@code up} reports what can actually be
 * known:
 * <ul>
 *   <li>{@code 1} once both subscriptions were created without error;</li>
 *   <li>{@code 0} while a subscription attempt is failing;</li>
 *   <li>{@code 0} when a stale timeout is configured and no message has arrived
 *       within it — which also triggers a full teardown and resubscribe.</li>
 * </ul>
 * The stale timeout is off by default, because an idle pipeline legitimately
 * publishes nothing and should not cause churn.
 */
public final class MonitorFeSubscriber implements AutoCloseable {

    private static final Logger LOGGER =
            new LoggerFactory().getLogger(MonitorFeSubscriber.class.getSimpleName());

    private static final long HEALTH_CHECK_MILLIS = 1000;
    private static final long MIN_RETRY_MILLIS = 500;
    private static final long JOIN_TIMEOUT_SECONDS = 5;

    private final PrometheusExporterConfig config;
    private final MonitorConnection.Factory factory;
    private final Handler handler;
    private final ExporterSelfMetrics selfMetrics;

    private final CountDownLatch shutdown = new CountDownLatch(1);
    private final AtomicLong lastMessageMillis = new AtomicLong();
    private final AtomicReference<MonitorConnection> connection = new AtomicReference<>();
    private volatile Thread supervisor;
    private volatile boolean everSubscribed;

    /**
     * Creates a subscriber.
     *
     * @param config      the exporter configuration
     * @param factory     creates the underlying Monitor FE connections
     * @param handler     receives the decoded Monitor FE messages
     * @param selfMetrics the exporter self-metrics to update
     */
    public MonitorFeSubscriber(PrometheusExporterConfig config,
                               MonitorConnection.Factory factory,
                               Handler handler,
                               ExporterSelfMetrics selfMetrics) {
        this.config = config;
        this.factory = factory;
        this.handler = handler;
        this.selfMetrics = selfMetrics;
    }

    /**
     * Starts the supervisor thread. Returns immediately; the first subscription
     * attempt happens on the new thread so that a Monitor FE that is not up yet
     * never blocks or aborts exporter startup.
     */
    public void start() {
        Thread thread = new Thread(this::run, "ersap-monitor-fe-subscriber");
        thread.setDaemon(true);
        supervisor = thread;
        thread.start();
    }

    /**
     * Gets the reception time of the last Monitor FE message.
     *
     * @return milliseconds since the epoch, or 0 if no message arrived yet
     */
    public long lastMessageMillis() {
        return lastMessageMillis.get();
    }

    /**
     * Tells whether the Monitor FE subscription is currently considered active.
     *
     * @return true when the exporter reports itself as up
     */
    public boolean isSubscribed() {
        return selfMetrics.isUp();
    }

    @Override
    public void close() {
        if (shutdown.getCount() == 0) {
            return;
        }
        LOGGER.info("stopping Monitor FE subscriber");
        shutdown.countDown();
        Thread thread = supervisor;
        if (thread != null) {
            // the latch alone releases every wait, so give the supervisor a
            // chance to tear the messaging layer down without an interrupt
            // pending: xMsg joins its own threads while closing and would
            // report the interruption as an error
            try {
                thread.join(TimeUnit.SECONDS.toMillis(JOIN_TIMEOUT_SECONDS));
                if (thread.isAlive()) {
                    LOGGER.warn("the Monitor FE subscriber did not stop in {} s; interrupting it",
                            JOIN_TIMEOUT_SECONDS);
                    thread.interrupt();
                    thread.join(TimeUnit.SECONDS.toMillis(JOIN_TIMEOUT_SECONDS));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeConnection();
        selfMetrics.setUp(false);
    }

    private void run() {
        long retryMillis = Math.max(MIN_RETRY_MILLIS,
                TimeUnit.SECONDS.toMillis(config.reconnectIntervalSeconds()));
        while (!isShutdown()) {
            boolean subscribed = false;
            try {
                LOGGER.info("connecting to Monitor FE at {}:{} (session={}{})",
                        config.monitorHost(), config.monitorPort(), config.session(),
                        config.engine().isEmpty() ? "" : ", engine=" + config.engine());
                MonitorConnection open = factory.connect(config);
                connection.set(open);
                open.subscribe(new DpeAdapter(), new UserAdapter());
                subscribed = true;
                selfMetrics.setUp(true);
                if (everSubscribed) {
                    selfMetrics.recordReconnect();
                    LOGGER.info("resubscribed to Monitor FE at {}:{}",
                            config.monitorHost(), config.monitorPort());
                } else {
                    everSubscribed = true;
                    LOGGER.info("subscribed to Monitor FE dpeReport and userMetrics topics");
                }
                awaitFailureOrShutdown();
            } catch (Exception e) {
                selfMetrics.recordConnectionError();
                LOGGER.error("could not subscribe to Monitor FE at {}:{}: {}",
                        config.monitorHost(), config.monitorPort(), describe(e));
            } finally {
                selfMetrics.setUp(false);
                closeConnection();
            }
            if (isShutdown()) {
                break;
            }
            if (subscribed) {
                LOGGER.warn("Monitor FE subscription dropped; retrying in {} s",
                        config.reconnectIntervalSeconds());
            } else {
                LOGGER.info("retrying Monitor FE subscription in {} s",
                        config.reconnectIntervalSeconds());
            }
            // a fixed floor on the retry delay keeps a permanently failing
            // Monitor FE from turning this into a tight loop
            if (sleep(retryMillis)) {
                break;
            }
        }
        LOGGER.info("Monitor FE subscriber stopped");
    }

    private void awaitFailureOrShutdown() {
        long staleMillis = TimeUnit.SECONDS.toMillis(config.staleTimeoutSeconds());
        while (!isShutdown()) {
            if (sleep(HEALTH_CHECK_MILLIS)) {
                return;
            }
            if (staleMillis <= 0) {
                continue;
            }
            long last = lastMessageMillis.get();
            if (last == 0) {
                continue;
            }
            long idle = System.currentTimeMillis() - last;
            if (idle > staleMillis) {
                LOGGER.warn("no Monitor FE message for {} s (stale timeout {} s); "
                                + "tearing down the subscription",
                        TimeUnit.MILLISECONDS.toSeconds(idle), config.staleTimeoutSeconds());
                return;
            }
        }
    }

    private void closeConnection() {
        MonitorConnection open = connection.getAndSet(null);
        if (open == null) {
            return;
        }
        try {
            open.close();
        } catch (Exception e) {
            LOGGER.warn("could not close the Monitor FE connection: {}", describe(e));
        }
    }

    private boolean isShutdown() {
        return shutdown.getCount() == 0;
    }

    /**
     * Waits for the given time or until shutdown.
     *
     * @return true if the wait ended because the subscriber is shutting down
     */
    private boolean sleep(long millis) {
        try {
            return shutdown.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private void touch(String source) {
        long now = System.currentTimeMillis();
        lastMessageMillis.set(now);
        selfMetrics.recordMessage(source, now);
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.toString() : message;
    }

    /**
     * Receives the messages decoded from the two Monitor FE subscriptions.
     */
    public interface Handler {

        /**
         * Called for every {@code dpeReport} message.
         *
         * @param registration the DPE registration report
         * @param runtime      the DPE runtime report
         */
        void onDpeReport(DpeRegistrationData registration, DpeRuntimeData runtime);

        /**
         * Called for every {@code userMetrics} message.
         *
         * @param session the ERSAP session
         * @param engine  the canonical engine name
         * @param metrics the key-value pairs published by the engine
         */
        void onUserMetrics(String session, String engine, Map<String, Object> metrics);
    }

    private final class DpeAdapter implements DpeReportHandler {

        @Override
        public void handleReport(DpeRegistrationData registration, DpeRuntimeData runtime) {
            touch(MonitorMetricParser.SOURCE_DPE_REPORT);
            try {
                handler.onDpeReport(registration, runtime);
            } catch (RuntimeException e) {
                LOGGER.error("could not handle a dpeReport message: {}", describe(e));
            }
        }
    }

    private final class UserAdapter implements UserMetricsHandler {

        @Override
        public Set<EngineDataType> dataTypes() {
            return Set.of(EngineDataType.JSON);
        }

        @Override
        public void handleMetrics(String session, String engine, Map<String, Object> metrics) {
            touch(MonitorMetricParser.SOURCE_USER_METRICS);
            try {
                handler.onUserMetrics(session, engine, metrics);
            } catch (RuntimeException e) {
                LOGGER.error("could not handle a userMetrics message: {}", describe(e));
            }
        }
    }
}
