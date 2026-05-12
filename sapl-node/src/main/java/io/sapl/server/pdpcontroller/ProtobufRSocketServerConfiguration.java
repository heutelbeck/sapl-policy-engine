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

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;

/**
 * Configuration for the protobuf-based RSocket PDP server.
 * <p>
 * Enabled by default on port 7000. Set
 * {@code sapl.pdp.rsocket.enabled=false} to disable, or
 * {@code sapl.pdp.rsocket.port} to override the port.
 * <p>
 * Connection lifetime is soft. JWT credentials are validated at the next
 * decision call; expired tokens are then rejected and clients reconnect with
 * fresh credentials. There is no separate per-connection hard-disconnect
 * timer.
 * <p>
 * If a {@link RSocketConnectionAuthenticator} bean is present, connections
 * are authenticated via the RSocket setup frame. Otherwise, all connections
 * are accepted without authentication.
 * <p>
 * The server lifecycle is managed by {@link ProtobufRSocketServerLifecycle}
 * via Spring's {@link org.springframework.context.SmartLifecycle} interface.
 */
@Configuration
public class ProtobufRSocketServerConfiguration {

    @Bean
    ProtobufRSocketServerLifecycle protobufRSocketServer(@Value("${sapl.pdp.rsocket.enabled:true}") boolean enabled,
            @Value("${sapl.pdp.rsocket.port:7000}") int port,
            @Value("${sapl.pdp.rsocket.socket-path:#{null}}") @Nullable String socketPath,
            @Value("${sapl.pdp.rsocket.max-inbound-payload-size:16777215}") int maxInboundPayloadSize,
            BlockingPolicyDecisionPoint blockingPdp, ReactivePolicyDecisionPoint pdp,
            ObjectProvider<RSocketConnectionAuthenticator> authenticator) {
        return new ProtobufRSocketServerLifecycle(enabled, port, socketPath, maxInboundPayloadSize, blockingPdp, pdp,
                authenticator.getIfAvailable());
    }

}
