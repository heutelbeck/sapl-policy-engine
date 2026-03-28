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
package io.sapl.server.pdpcontroller;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;

import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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

    private final boolean                                  enabled;
    private final int                                      port;
    private final @Nullable Duration                       maxConnectionLifetime;
    private final PolicyDecisionPoint                      pdp;
    private final @Nullable RSocketConnectionAuthenticator authenticator;

    private volatile @Nullable CloseableChannel server;
    private volatile boolean                    running;

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        if (authenticator != null) {
            log.info("RSocket authentication enabled");
        } else {
            log.warn("RSocket server has no authentication configured");
        }
        if (maxConnectionLifetime != null) {
            log.info("RSocket max connection lifetime: {}", maxConnectionLifetime);
        }
        val acceptor = new ProtobufRSocketAcceptor(pdp, authenticator, maxConnectionLifetime);
        server  = RSocketServer.create(acceptor).bindNow(TcpServerTransport.create(port));
        running = true;
        log.info("Protobuf RSocket PDP server started on port {}", port);
    }

    @Override
    public void stop() {
        if (server != null) {
            log.info("Shutting down Protobuf RSocket PDP server");
            server.dispose();
            server = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }

}
