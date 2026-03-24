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

import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.sapl.api.pdp.PolicyDecisionPoint;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the protobuf-based RSocket PDP server. Runs on a separate
 * port from the Spring Messaging-based endpoint for performance comparison.
 *
 * <p>
 * Enable with property {@code sapl.pdp.rsocket.protobuf.enabled=true} and
 * configure the port with {@code sapl.pdp.rsocket.protobuf.port}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "sapl.pdp.rsocket.protobuf.enabled", havingValue = "true")
public class ProtobufRSocketServerConfiguration {

    @Value("${sapl.pdp.rsocket.protobuf.port:7001}")
    private int port;

    private CloseableChannel server;

    @Bean
    ProtobufRSocketAcceptor protobufRSocketAcceptor(PolicyDecisionPoint pdp) {
        return new ProtobufRSocketAcceptor(pdp);
    }

    @Bean
    CloseableChannel protobufRSocketServer(ProtobufRSocketAcceptor acceptor) {
        server = RSocketServer.create(acceptor).bindNow(TcpServerTransport.create(port));
        log.info("Protobuf RSocket PDP server started on port {}", port);
        return server;
    }

    @PreDestroy
    void shutdown() {
        if (server != null) {
            log.info("Shutting down Protobuf RSocket PDP server");
            server.dispose();
        }
    }
}
