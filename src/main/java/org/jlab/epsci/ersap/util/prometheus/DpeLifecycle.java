/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tracks when each DPE last reported and drops its Prometheus series once it
 * has been silent for longer than the configured timeout.
 *
 * <p>Identity is the {@code (session, dpe)} pair: two DPEs in different
 * sessions are independent, and sessions that reuse the same DPE address
 * cannot accidentally expire each other. The last-seen timestamp is bumped
 * by {@link #touch} only, which is called from the code that receives an
 * actual {@code dpeReport}. Prometheus scrapes never bump it — that would
 * defeat the purpose.
 *
 * <p>The public surface is intentionally small: {@link #touch} is called on
 * every DPE report, {@link #expireOnce} runs one sweep (called by the
 * scheduler thread and by tests through an injectable clock), and
 * {@link #start} / {@link #close} bracket the daemon scheduler that runs
 * sweeps at the configured interval. All state is held in a
 * {@link ConcurrentHashMap} so that reports, sweeps and scrapes never
 * block each other.
 */
public final class DpeLifecycle implements AutoCloseable {

    private static final Logger LOGGER =
            new LoggerFactory().getLogger(DpeLifecycle.class.getSimpleName());

    private final PrometheusMetricRegistry registry;
    private final long staleTimeoutMillis;
    private final long sweepIntervalMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<Key, Long> lastSeen = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> sweep;

    /**
     * Creates a lifecycle tracker using {@link System#currentTimeMillis()}
     * as its clock.
     *
     * @param registry              the registry whose series to prune
     * @param staleTimeoutSeconds   silence window before a DPE is expired
     *                              (0 disables expiration; {@link #touch} then
     *                              becomes a no-op and no sweeps run)
     * @param sweepIntervalSeconds  how often to look for expired DPEs
     */
    public DpeLifecycle(PrometheusMetricRegistry registry,
                        int staleTimeoutSeconds,
                        int sweepIntervalSeconds) {
        this(registry, staleTimeoutSeconds, sweepIntervalSeconds,
             System::currentTimeMillis);
    }

    /**
     * Creates a lifecycle tracker with an injectable clock, for tests.
     *
     * @param registry              the registry whose series to prune
     * @param staleTimeoutSeconds   silence window before a DPE is expired
     * @param sweepIntervalSeconds  how often to look for expired DPEs
     * @param clock                 supplies the current time, in milliseconds
     */
    public DpeLifecycle(PrometheusMetricRegistry registry,
                        int staleTimeoutSeconds,
                        int sweepIntervalSeconds,
                        LongSupplier clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (staleTimeoutSeconds < 0) {
            throw new IllegalArgumentException(
                    "dpe stale timeout must not be negative, got " + staleTimeoutSeconds);
        }
        if (sweepIntervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "dpe stale sweep interval must be positive, got " + sweepIntervalSeconds);
        }
        this.staleTimeoutMillis = TimeUnit.SECONDS.toMillis(staleTimeoutSeconds);
        this.sweepIntervalMillis = TimeUnit.SECONDS.toMillis(sweepIntervalSeconds);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Tells whether DPE expiration is enabled. */
    public boolean isEnabled() {
        return staleTimeoutMillis > 0;
    }

    /**
     * Records that a {@code dpeReport} was just received for the given DPE.
     *
     * <p>Called from the Monitor FE handler thread, i.e. once per report.
     * Never call this from the Prometheus scrape path — that would refresh
     * lastSeen without a real report ever arriving and defeat the timeout.
     *
     * @param session the ERSAP session the report belongs to (may be null)
     * @param dpe     the canonical DPE name, e.g. {@code 10.0.0.1%9000_java}
     */
    public void touch(String session, String dpe) {
        if (!isEnabled() || dpe == null || dpe.isEmpty()) {
            return;
        }
        lastSeen.put(new Key(session == null ? "" : session, dpe), clock.getAsLong());
    }

    /**
     * Removes a DPE from the tracker without touching the registry. Useful
     * when a report explicitly signals a shutdown; the exporter does not use
     * this today, but it keeps the tracker composable.
     */
    public void forget(String session, String dpe) {
        if (dpe == null || dpe.isEmpty()) {
            return;
        }
        lastSeen.remove(new Key(session == null ? "" : session, dpe));
    }

    /** Snapshot of the current lastSeen map, for diagnostics and tests. */
    public Map<Key, Long> snapshot() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(lastSeen));
    }

    /** The number of DPEs currently being tracked. */
    public int trackedCount() {
        return lastSeen.size();
    }

    /**
     * Runs one expiration sweep against the tracker's clock, removing every
     * DPE whose last-seen age exceeds the configured timeout. Returns the
     * expired keys for logging and tests.
     */
    public List<Expired> expireOnce() {
        if (!isEnabled() || lastSeen.isEmpty()) {
            return List.of();
        }
        long now = clock.getAsLong();
        List<Expired> expired = new ArrayList<>();
        // computeIfPresent under a snapshot key set makes each removal
        // atomic against a concurrent touch(): if the DPE just reported
        // again its lastSeen moved forward and we leave it alone
        Set<Key> keys = Set.copyOf(lastSeen.keySet());
        for (Key key : keys) {
            lastSeen.computeIfPresent(key, (k, ts) -> {
                long age = now - ts;
                if (age > staleTimeoutMillis) {
                    expired.add(new Expired(k, age));
                    return null;
                }
                return ts;
            });
        }
        for (Expired e : expired) {
            int removed = registry.removeDpe(e.key.session, e.key.dpe);
            LOGGER.info("Removing stale DPE metrics: session={} dpe={} last_seen_age={}s "
                            + "timeout={}s (removed {} series)",
                    e.key.session, e.key.dpe,
                    TimeUnit.MILLISECONDS.toSeconds(e.ageMillis),
                    TimeUnit.MILLISECONDS.toSeconds(staleTimeoutMillis),
                    removed);
        }
        return expired;
    }

    /**
     * Starts the sweep scheduler. No-op when expiration is disabled or when
     * already started.
     */
    public synchronized void start() {
        if (!isEnabled() || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ersap-dpe-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
        // schedule initial delay = interval so the first sweep does not fire
        // before at least one DPE report has had a chance to arrive
        sweep = scheduler.scheduleWithFixedDelay(this::safeSweep,
                sweepIntervalMillis, sweepIntervalMillis, TimeUnit.MILLISECONDS);
        LOGGER.info("DPE stale-metric expiration enabled: timeout={}s sweep={}s",
                TimeUnit.MILLISECONDS.toSeconds(staleTimeoutMillis),
                TimeUnit.MILLISECONDS.toSeconds(sweepIntervalMillis));
    }

    private void safeSweep() {
        try {
            expireOnce();
        } catch (RuntimeException e) {
            LOGGER.error("DPE stale-metric sweep failed: {}", e.toString());
        }
    }

    @Override
    public synchronized void close() {
        if (sweep != null) {
            sweep.cancel(false);
            sweep = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }

    /** Composite key for a per-session DPE. */
    public static final class Key {
        private final String session;
        private final String dpe;

        Key(String session, String dpe) {
            this.session = session;
            this.dpe = dpe;
        }

        public String session() {
            return session;
        }

        public String dpe() {
            return dpe;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key other = (Key) o;
            return session.equals(other.session) && dpe.equals(other.dpe);
        }

        @Override
        public int hashCode() {
            return session.hashCode() * 31 + dpe.hashCode();
        }

        @Override
        public String toString() {
            return "(" + session + "," + dpe + ")";
        }
    }

    /** Result of one expiration, exposed for logging and tests. */
    public static final class Expired {
        private final Key key;
        private final long ageMillis;

        Expired(Key key, long ageMillis) {
            this.key = key;
            this.ageMillis = ageMillis;
        }

        public Key key() {
            return key;
        }

        public long ageMillis() {
            return ageMillis;
        }
    }
}
