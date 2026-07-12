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

import javax.net.ssl.SSLException;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.sapl.node.boot.SaplStartupConfigurationException;
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
 * decision call. Expired tokens are then rejected and clients reconnect with
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

    private static final String ERROR_BUNDLE_BUILD  = "SAPL Node refused to start. The SSL bundle '%s' for RSocket TLS could not be loaded.";
    private static final String ACTION_BUNDLE_BUILD = """
            The bundle was found but the keystore behind it could not be
            opened. Common causes:

              keystore file missing or unreadable
              wrong keystore password
              wrong key alias
              keystore type mismatch (PKCS12 vs JKS)

            Cross check the spring.ssl.bundle.<jks|pem>.<name>.* properties
            against the file on disk.""";

    private static final String ERROR_BUNDLE_NOT_FOUND  = "SAPL Node refused to start. The SSL bundle '%s' referenced by sapl.pdp.rsocket.ssl.bundle is not configured.";
    private static final String ACTION_BUNDLE_NOT_FOUND = """
            Define the bundle under spring.ssl.bundle, for example:

              spring:
                ssl:
                  bundle:
                    jks:
                      sapl-bundle:
                        key:
                          alias: sapl-node
                          password: changeit
                        keystore:
                          location: file:/etc/sapl/keystore.p12
                          password: changeit
                          type: PKCS12

            then reference it from sapl.pdp.rsocket.ssl.bundle and (if you
            also want HTTPS) from server.ssl.bundle.""";

    private static final String ERROR_BUNDLE_NO_REGISTRY  = "SAPL Node refused to start. sapl.pdp.rsocket.ssl.bundle='%s' is set but no SslBundles bean is available.";
    private static final String ACTION_BUNDLE_NO_REGISTRY = """
            Either define the bundle under spring.ssl.bundle.<jks|pem>.* in
            application.yml, or unset sapl.pdp.rsocket.ssl.bundle if you do
            not want TLS on the RSocket transport.""";

    @Bean
    ProtobufRSocketServerLifecycle protobufRSocketServer(@Value("${sapl.pdp.rsocket.enabled:true}") boolean enabled,
            @Value("${sapl.pdp.rsocket.address:127.0.0.1}") String bindAddress,
            @Value("${sapl.pdp.rsocket.port:7000}") int port,
            @Value("${sapl.pdp.rsocket.socket-path:#{null}}") @Nullable String socketPath,
            @Value("${sapl.pdp.rsocket.max-inbound-payload-size:16777215}") int maxInboundPayloadSize,
            @Value("${io.sapl.node.max-multi-subscription-count:256}") int maxMultiSubscriptionCount,
            @Value("${sapl.pdp.rsocket.ssl.bundle:#{null}}") @Nullable String sslBundleName,
            @Value("${io.sapl.node.limits.rsocket.max-connections:0}") int maxConnections,
            @Value("${io.sapl.node.limits.rsocket.max-streams-per-connection:0}") int maxStreamsPerConnection,
            @Value("${io.sapl.node.limits.rsocket.requests-per-second:0}") int requestsPerSecond,
            BlockingPolicyDecisionPoint blockingPdp, ReactivePolicyDecisionPoint pdp,
            ObjectProvider<RSocketConnectionAuthenticator> authenticator, ObjectProvider<SslBundles> sslBundles,
            ObjectProvider<MeterRegistry> meterRegistry) {
        val sslContext = resolveSslContext(sslBundleName, sslBundles.getIfAvailable());
        val limiter    = RSocketLimiter.of(maxConnections, maxStreamsPerConnection, requestsPerSecond,
                meterRegistry.getIfAvailable());
        return new ProtobufRSocketServerLifecycle(enabled, bindAddress, port, socketPath, maxInboundPayloadSize,
                maxMultiSubscriptionCount, blockingPdp, pdp, authenticator.getIfAvailable(), limiter, sslContext);
    }

    private static @Nullable SslContext resolveSslContext(@Nullable String bundleName,
            @Nullable SslBundles sslBundles) {
        if (bundleName == null || bundleName.isBlank()) {
            return null;
        }
        if (sslBundles == null) {
            throw new SaplStartupConfigurationException(ERROR_BUNDLE_NO_REGISTRY.formatted(bundleName),
                    ACTION_BUNDLE_NO_REGISTRY);
        }
        try {
            val bundle = sslBundles.getBundle(bundleName);
            return SslContextBuilder.forServer(bundle.getManagers().getKeyManagerFactory()).build();
        } catch (NoSuchSslBundleException e) {
            val ex = new SaplStartupConfigurationException(ERROR_BUNDLE_NOT_FOUND.formatted(bundleName),
                    ACTION_BUNDLE_NOT_FOUND);
            ex.initCause(e);
            throw ex;
        } catch (SSLException e) {
            val ex = new SaplStartupConfigurationException(ERROR_BUNDLE_BUILD.formatted(bundleName),
                    ACTION_BUNDLE_BUILD);
            ex.initCause(e);
            throw ex;
        }
    }

}
