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
package io.sapl.node.rsocket.pdp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContext;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.sapl.node.SaplStartupConfigurationException;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.netty.tcp.TcpServer;

/**
 * Manages the lifecycle of the protobuf RSocket PDP server. Participates in
 * Spring's {@link SmartLifecycle} for ordered startup and shutdown alongside
 * the HTTP server.
 * <p>
 * When {@code enabled} is false, the server is not started and all lifecycle
 * methods are no-ops.
 */
@Slf4j
@RequiredArgsConstructor
public class ProtobufRSocketServerLifecycle implements SmartLifecycle {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private static final String ERROR_PAYLOAD_SIZE  = "SAPL Node refused to start. sapl.pdp.rsocket.max-inbound-payload-size is %d, must be positive.";
    private static final String ACTION_PAYLOAD_SIZE = """
            Set sapl.pdp.rsocket.max-inbound-payload-size to at least 16777215,
            the RSocket protocol per frame ceiling. Lower values are not legal
            because a single decision frame can already reach that size.""";

    private final boolean                                  enabled;
    private final int                                      port;
    private final @Nullable String                         socketPath;
    private final int                                      maxInboundPayloadSize;
    private final BlockingPolicyDecisionPoint              blockingPdp;
    private final ReactivePolicyDecisionPoint              pdp;
    private final @Nullable RSocketConnectionAuthenticator authenticator;
    // Optional TLS context. When set, the TCP server is wrapped in TLS via
    // Reactor Netty's secure() before being handed to TcpServerTransport.
    // Resolution from a Spring Boot SslBundle happens in the @Configuration.
    private final @Nullable SslContext sslContext;

    // ReentrantLock instead of synchronized to avoid carrier-thread pinning if
    // start()/stop() ever runs on a virtual thread under JDK 21.
    private final ReentrantLock        lifecycleLock = new ReentrantLock();
    private @Nullable CloseableChannel server;
    private boolean                    running;

    @Override
    public void start() {
        lifecycleLock.lock();
        try {
            if (!enabled || running) {
                return;
            }
            if (maxInboundPayloadSize <= 0) {
                throw new SaplStartupConfigurationException(ERROR_PAYLOAD_SIZE.formatted(maxInboundPayloadSize),
                        ACTION_PAYLOAD_SIZE);
            }
            if (authenticator != null) {
                log.info("RSocket authentication enabled");
            } else {
                log.warn("RSocket server has no authentication configured");
            }
            log.debug("RSocket max inbound payload size: {} bytes", maxInboundPayloadSize);
            val acceptor  = new ProtobufRSocketAcceptor(blockingPdp, pdp, authenticator);
            val transport = createTransport();
            server  = RSocketServer.create(acceptor).maxInboundPayloadSize(maxInboundPayloadSize).bindNow(transport);
            running = true;
            val scheme = sslContext != null ? "tls" : "tcp";
            if (socketPath != null) {
                log.debug("Protobuf RSocket PDP server started on Unix socket {} ({})", socketPath, scheme);
            } else {
                log.debug("Protobuf RSocket PDP server started on port {} ({})", port, scheme);
            }
            if (sslContext == null && socketPath == null) {
                log.warn("RSocket server bound on port {} without TLS. Connection-setup credentials "
                        + "(basic auth, API key, JWT) and decision payloads traverse the network in cleartext. "
                        + "Configure sapl.pdp.rsocket.ssl.bundle, or terminate TLS at an upstream load balancer.",
                        port);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void stop() {
        lifecycleLock.lock();
        try {
            if (!running) {
                return;
            }
            val current = server;
            if (current != null) {
                current.dispose();
                try {
                    current.onClose().block(SHUTDOWN_TIMEOUT);
                } catch (RuntimeException e) {
                    log.warn("RSocket server did not close within {}s: {}", SHUTDOWN_TIMEOUT.toSeconds(),
                            e.getMessage());
                }
                server = null;
            }
            if (socketPath != null) {
                deleteSocketFile(socketPath);
            }
            running = false;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private TcpServerTransport createTransport() {
        TcpServer tcpServer;
        if (socketPath != null) {
            if (Files.exists(Path.of(socketPath))) {
                log.warn("Unix socket file {} already exists. Removing it before binding. "
                        + "If another SAPL Node instance is bound to this path it will be displaced; "
                        + "verify your deployment if this is unexpected.", socketPath);
            }
            deleteSocketFile(socketPath);
            val path = socketPath;
            tcpServer = TcpServer.create().bindAddress(() -> new DomainSocketAddress(path));
        } else {
            tcpServer = TcpServer.create().port(port);
        }
        if (sslContext != null) {
            tcpServer = tcpServer.secure(spec -> spec.sslContext(sslContext));
        }
        return TcpServerTransport.create(tcpServer);
    }

    /**
     * Deletes the Unix socket file. Best-effort: a leftover file from a
     * previous instance must be removed before bind succeeds. If deletion
     * fails (file does not exist, or filesystem error), the subsequent bind
     * call will surface the real error.
     */
    private static void deleteSocketFile(String path) {
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            log.debug("Unable to delete Unix socket file {}: {}", path, e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        lifecycleLock.lock();
        try {
            return running;
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }

}
