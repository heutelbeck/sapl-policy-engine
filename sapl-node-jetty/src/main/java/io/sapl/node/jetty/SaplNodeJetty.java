/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.jetty;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.PDPComponents;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Reactor-free SAPL PDP server. Embedded Jetty 12 with a
 * virtual-thread executor, no Spring, no Spring Boot. Exposes the same
 * HTTP/JSON authorization endpoints as the reference {@code sapl-node}
 * so wrk2 (and other HTTP load tools) can drive both side-by-side.
 * <p>
 * Configuration via environment variables and system properties (the
 * env var name and the system property name are interchangeable; the
 * system property wins if both are set):
 * <ul>
 * <li>{@code SAPL_POLICIES_PATH} / {@code sapl.policies.path} —
 * directory containing {@code pdp.json} and {@code *.sapl} files.
 * Defaults to {@code ./policies}.</li>
 * <li>{@code SAPL_HTTP_PORT} / {@code sapl.http.port} — listen port.
 * Defaults to {@code 8080}.</li>
 * </ul>
 */
@Slf4j
public final class SaplNodeJetty {

    private SaplNodeJetty() {
    }

    public static void main(String[] args) throws Exception {
        val policiesPath = resolvePath();
        val httpPort     = resolvePort();
        val grpcPort     = resolveGrpcPort();
        val components   = buildPdp(policiesPath);
        val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val httpServer   = startServer(httpPort, components.pdp(), mapper);
        val grpcServer   = new SaplGrpcServer(components.pdp()).start(grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Shutdown hook triggered.");
                httpServer.stop();
                grpcServer.shutdownNow();
                components.close();
            } catch (Exception e) {
                log.warn("Shutdown failure: {}", e.getMessage());
            }
        }));

        log.info("SAPL node (Jetty + virtual threads) http port {} grpc port {} policies {}", httpPort, grpcPort,
                policiesPath);
        httpServer.join();
    }

    private static int resolveGrpcPort() {
        val sys = System.getProperty("sapl.grpc.port");
        if (sys != null) {
            return Integer.parseInt(sys);
        }
        val env = System.getenv("SAPL_GRPC_PORT");
        return env != null ? Integer.parseInt(env) : 9090;
    }

    private static Path resolvePath() {
        val sys = System.getProperty("sapl.policies.path");
        if (sys != null) {
            return Path.of(sys);
        }
        val env = System.getenv("SAPL_POLICIES_PATH");
        return Path.of(env != null ? env : "./policies");
    }

    private static int resolvePort() {
        val sys = System.getProperty("sapl.http.port");
        if (sys != null) {
            return Integer.parseInt(sys);
        }
        val env = System.getenv("SAPL_HTTP_PORT");
        return env != null ? Integer.parseInt(env) : 8080;
    }

    private static PDPComponents buildPdp(Path policiesPath) {
        return PolicyDecisionPointBuilder.withDefaults().withDirectorySource(policiesPath).build();
    }

    private static Server startServer(int port, BlockingPolicyDecisionPoint pdp, JsonMapper mapper) throws Exception {
        val threadPool = new QueuedThreadPool();
        threadPool.setVirtualThreadsExecutor(Executors.newVirtualThreadPerTaskExecutor());
        threadPool.setName("sapl-jetty");

        val server    = new Server(threadPool);
        val connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        val context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new DecideOnceServlet(pdp, mapper)), "/api/pdp/decide-once");
        context.addServlet(new ServletHolder(new DecideStreamServlet(pdp, mapper)), "/api/pdp/decide");
        server.setHandler(context);

        server.start();
        return server;
    }
}
