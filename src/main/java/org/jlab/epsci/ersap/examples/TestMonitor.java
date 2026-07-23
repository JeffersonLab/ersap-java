/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples;

import org.jlab.epsci.ersap.base.ContainerRuntimeData;
import org.jlab.epsci.ersap.base.DataRingAddress;
import org.jlab.epsci.ersap.base.DpeRegistrationData;
import org.jlab.epsci.ersap.base.DpeRuntimeData;
import org.jlab.epsci.ersap.base.ServiceRuntimeData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.orchestrators.DpeReportHandler;
import org.jlab.epsci.ersap.std.orchestrators.MonitorOrchestrator;
import org.jlab.epsci.ersap.std.orchestrators.UserMetricsHandler;

import java.util.Map;
import java.util.Set;

/**
 * Standalone monitor for the three-service observability test pipeline:
 *
 * <pre>
 *   SourceOfDoubles  →  EventRateMonitor  →  DoubleDumpSink
 * </pre>
 *
 * <p>Connects to the Monitor FE proxy and subscribes to:
 * <ol>
 *   <li>{@code dpeReport} — standard system metrics (CPU, memory, load, per-service
 *       request counts, execution time, shared-memory I/O)</li>
 *   <li>{@code userMetrics} — custom metrics published by {@code EventRateMonitor}
 *       via {@code EngineMetricsPublisher} (event rate, window count, total events)</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   java -cp "$ERSAP_HOME/lib/*:$ERSAP_HOME/services/*" \
 *        org.jlab.epsci.ersap.examples.TestMonitor [monfe-host] [session]
 * </pre>
 *
 * <p>Arguments:
 * <ul>
 *   <li>{@code monfe-host} — hostname of the Monitor FE DPE (default: {@code localhost})</li>
 *   <li>{@code session}    — DPE session to filter reports on (default: {@code test})</li>
 * </ul>
 *
 * @see org.jlab.epsci.ersap.examples.engines.test.EventRateMonitor
 * @see ObservabilityTest.md for the full step-by-step setup guide
 */
public class TestMonitor {

    public static void main(String[] args) throws Exception {
        String monFeHost = args.length > 0 ? args[0] : "localhost";
        String session   = args.length > 1 ? args[1] : "test";

        DataRingAddress monitorFe = new DataRingAddress(monFeHost);
        MonitorOrchestrator monitor = new MonitorOrchestrator(monitorFe);

        // ── DPE system metrics (published every report-period seconds) ──────
        monitor.listenDpeReports(session, new DpeReportHandler() {
            @Override
            public void handleReport(DpeRegistrationData reg, DpeRuntimeData runtime) {
                System.out.printf("%n=== DPE Report: %s  session=%s ===%n",
                        runtime.name(), reg.session());
                System.out.printf("  CPU usage   : %.1f%%%n",  runtime.cpuUsage() * 100);
                System.out.printf("  Memory      : %.1f MB%n", runtime.memoryUsage() / 1_048_576.0);
                System.out.printf("  System load : %.2f%n",    runtime.systemLoad());
                System.out.printf("  Cores       : %d%n",      reg.numCores());

                for (ContainerRuntimeData container : runtime.containers()) {
                    System.out.printf("  Container: %s%n", container.name());
                    for (ServiceRuntimeData svc : container.services()) {
                        long execMs = svc.executionTime() / 1_000;
                        System.out.printf("    Service        : %s%n", svc.name());
                        System.out.printf("      requests     : %d%n",    svc.numRequests());
                        System.out.printf("      failures     : %d%n",    svc.numFailures());
                        System.out.printf("      exec time    : %d ms (cumulative)%n", execMs);
                        System.out.printf("      shm reads    : %d%n",    svc.sharedMemoryReads());
                        System.out.printf("      shm writes   : %d%n",    svc.sharedMemoryWrites());
                        System.out.printf("      bytes recv   : %d%n",    svc.bytesReceived());
                        System.out.printf("      bytes sent   : %d%n",    svc.bytesSent());
                    }
                }
            }
        });

        // ── User metrics from EventRateMonitor (published every ~1 second) ──
        monitor.listenUserMetrics(session, new UserMetricsHandler() {
            @Override
            public Set<EngineDataType> dataTypes() {
                return Set.of(EngineDataType.JSON);
            }

            @Override
            public void handleMetrics(String session, String engine,
                                      Map<String, Object> metrics) {
                System.out.printf("%n--- User Metrics: %s  session=%s ---%n",
                        engine, session);
                metrics.forEach((k, v) -> System.out.printf("  %-22s: %s%n", k, v));
            }
        });

        System.out.printf("TestMonitor listening on %s  (session=%s) — press Ctrl-C to stop%n",
                monFeHost, session);
        Thread.currentThread().join();
    }
}
