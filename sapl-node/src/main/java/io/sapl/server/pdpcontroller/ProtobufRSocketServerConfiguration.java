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

import javax.net.ssl.SSLException;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;

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
 * TLS is opt-in via {@code sapl.pdp.rsocket.ssl.bundle=<name>}, which
 * resolves a Spring Boot {@link SslBundles SSL bundle} configured under
 * {@code spring.ssl.bundle.*}. The same bundle definition can be shared
 * with the HTTP server, so a single keystore covers both transports.
 * <p>
 * The server lifecycle is managed by {@link ProtobufRSocketServerLifecycle}
 * via Spring's {@link org.springframework.context.SmartLifecycle} interface.
 */
@Configuration
public class ProtobufRSocketServerConfiguration {

    private static final String ERROR_BUNDLE_BUILD       = "Failed to build Netty SslContext from SSL bundle '%s'.";
    private static final String ERROR_BUNDLE_NOT_FOUND   = "SSL bundle '%s' referenced by sapl.pdp.rsocket.ssl.bundle is not configured under spring.ssl.bundle.*.";
    private static final String ERROR_BUNDLE_NO_REGISTRY = "sapl.pdp.rsocket.ssl.bundle='%s' is set but no SslBundles bean is available. Configure spring.ssl.bundle.* or unset the property.";

    @Bean
    ProtobufRSocketServerLifecycle protobufRSocketServer(@Value("${sapl.pdp.rsocket.enabled:true}") boolean enabled,
            @Value("${sapl.pdp.rsocket.port:7000}") int port,
            @Value("${sapl.pdp.rsocket.socket-path:#{null}}") @Nullable String socketPath,
            @Value("${sapl.pdp.rsocket.max-inbound-payload-size:16777215}") int maxInboundPayloadSize,
            @Value("${sapl.pdp.rsocket.ssl.bundle:#{null}}") @Nullable String sslBundleName,
            BlockingPolicyDecisionPoint blockingPdp, ReactivePolicyDecisionPoint pdp,
            ObjectProvider<RSocketConnectionAuthenticator> authenticator, ObjectProvider<SslBundles> sslBundles) {
        val sslContext = resolveSslContext(sslBundleName, sslBundles.getIfAvailable());
        return new ProtobufRSocketServerLifecycle(enabled, port, socketPath, maxInboundPayloadSize, blockingPdp, pdp,
                authenticator.getIfAvailable(), sslContext);
    }

    private static @Nullable SslContext resolveSslContext(@Nullable String bundleName,
            @Nullable SslBundles sslBundles) {
        if (bundleName == null || bundleName.isBlank()) {
            return null;
        }
        if (sslBundles == null) {
            throw new IllegalStateException(ERROR_BUNDLE_NO_REGISTRY.formatted(bundleName));
        }
        try {
            val bundle = sslBundles.getBundle(bundleName);
            return SslContextBuilder.forServer(bundle.getManagers().getKeyManagerFactory()).build();
        } catch (NoSuchSslBundleException e) {
            throw new IllegalStateException(ERROR_BUNDLE_NOT_FOUND.formatted(bundleName), e);
        } catch (SSLException e) {
            throw new IllegalStateException(ERROR_BUNDLE_BUILD.formatted(bundleName), e);
        }
    }

}
