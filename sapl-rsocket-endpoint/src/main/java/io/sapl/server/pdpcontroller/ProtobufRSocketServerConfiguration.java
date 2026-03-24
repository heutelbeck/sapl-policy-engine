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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.pdp.PolicyDecisionPoint;

/**
 * Configuration for the protobuf-based RSocket PDP server.
 * <p>
 * Enable with property {@code sapl.pdp.rsocket.enabled=true} and configure
 * the port with {@code sapl.pdp.rsocket.port} (default: 7000).
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
    ProtobufRSocketServerLifecycle protobufRSocketServer(@Value("${sapl.pdp.rsocket.enabled:false}") boolean enabled,
            @Value("${sapl.pdp.rsocket.port:7000}") int port, PolicyDecisionPoint pdp,
            @Nullable RSocketConnectionAuthenticator authenticator) {
        return new ProtobufRSocketServerLifecycle(enabled, port, pdp, authenticator);
    }

}
