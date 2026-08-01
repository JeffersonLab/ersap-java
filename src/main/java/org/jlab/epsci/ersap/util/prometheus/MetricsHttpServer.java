/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.prometheus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import org.jlab.epsci.ersap.util.logging.Logger;
import org.jlab.epsci.ersap.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * The HTTP endpoint scraped by Prometheus.
 *
 * <p>Exposition is delegated to the official Prometheus Java client
 * ({@link HTTPServer}), which owns {@code /metrics} and content negotiation
 * between the text and OpenMetrics formats. The exporter only adds a small
 * {@code /health} endpoint on the same server, because the client's built-in
 * {@code /-/healthy} always answers "Exporter is healthy" and says nothing about
 * the Monitor FE subscription.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /metrics} — Prometheus exposition format</li>
 *   <li>{@code GET /health} — {@code 200} with {@code {"status":"ok"}} when the
 *       Monitor FE subscription is active, {@code 503} with
 *       {@code {"status":"degraded"}} when it is not. The exporter keeps serving
 *       metrics in both cases.</li>
 * </ul>
 */
public final class MetricsHttpServer implements AutoCloseable {

    private static final Logger LOGGER =
            new LoggerFactory().getLogger(MetricsHttpServer.class.getSimpleName());

    private static final int BACKLOG = 8;
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;

    private final HTTPServer server;
    private final int port;

    /**
     * Starts the HTTP server.
     *
     * @param registry the Prometheus registry to expose
     * @param host     the bind address
     * @param port     the port to bind to, or 0 for an ephemeral port
     * @param healthy  tells whether the Monitor FE subscription is active
     * @throws IOException if the socket could not be bound
     */
    public MetricsHttpServer(CollectorRegistry registry, String host, int port,
                             BooleanSupplier healthy) throws IOException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
        HttpServer httpServer = HttpServer.create(address, BACKLOG);
        httpServer.createContext("/health", new HealthHandler(healthy));
        this.server = new HTTPServer.Builder()
                .withHttpServer(httpServer)
                .withRegistry(registry)
                .withDaemonThreads(true)
                .build();
        this.port = this.server.getPort();
        LOGGER.info("serving Prometheus metrics on http://{}:{}/metrics", host, this.port);
    }

    /**
     * Gets the port the server is listening on.
     *
     * <p>Useful when the configured port was 0 and the operating system picked
     * an ephemeral one.
     *
     * @return the bound port
     */
    public int port() {
        return port;
    }

    @Override
    public void close() {
        LOGGER.info("stopping the Prometheus HTTP endpoint");
        server.close();
    }

    /**
     * Answers {@code /health} from the current subscription state.
     */
    private static final class HealthHandler implements HttpHandler {

        private final BooleanSupplier healthy;

        HealthHandler(BooleanSupplier healthy) {
            this.healthy = healthy;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())
                        && !"HEAD".equals(exchange.getRequestMethod())) {
                    respond(exchange, HTTP_METHOD_NOT_ALLOWED,
                            "{\"status\":\"error\",\"error\":\"method not allowed\"}");
                    return;
                }
                boolean up = healthy.getAsBoolean();
                String body = "{\"status\":\"" + (up ? "ok" : "degraded")
                        + "\",\"running\":true,\"monitorFeSubscribed\":" + up + "}";
                respond(exchange, up ? HTTP_OK : HTTP_UNAVAILABLE, body);
            } finally {
                exchange.close();
            }
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = (body + "\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
    }
}
