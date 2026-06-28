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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;

import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;

/**
 * Specifications for {@link ProtobufRSocketServerConfiguration}.
 * <p>
 * The configuration's job beyond plain wiring is to translate the
 * {@code sapl.pdp.rsocket.ssl.bundle} property into a Netty
 * {@code SslContext} using a Spring Boot {@link SslBundles} registry.
 * The tests pin the four operator-visible outcomes of that resolution:
 * absent property leaves TLS off, missing registry fails loudly,
 * unknown bundle name fails loudly, and a present bundle produces a
 * lifecycle with TLS wired in.
 */
@DisplayName("ProtobufRSocketServerConfiguration")
@ExtendWith(MockitoExtension.class)
class ProtobufRSocketServerConfigurationTests {

    private static final String BIND_ADDRESS            = "127.0.0.1";
    private static final String BUNDLE_NAME             = "rsocket-tls";
    private static final int    MAX_MULTI_SUBSCRIPTIONS = 256;
    private static final int    PAYLOAD_SIZE            = 65_536;
    private static final int    PORT                    = 7000;

    @Mock
    private BlockingPolicyDecisionPoint blockingPdp;

    @Mock
    private ReactivePolicyDecisionPoint pdp;

    @Mock
    private ObjectProvider<RSocketConnectionAuthenticator> authenticators;

    @Mock
    private ObjectProvider<SslBundles> sslBundlesProvider;

    private final ProtobufRSocketServerConfiguration sut = new ProtobufRSocketServerConfiguration();

    @Nested
    @DisplayName("SSL bundle resolution")
    class SslBundleResolution {

        @Test
        @DisplayName("no bundle name configured leaves TLS off and the lifecycle starts in plain TCP mode")
        void whenNoBundleNameThenLifecycleBuiltWithoutSsl() {
            val lifecycle = sut.protobufRSocketServer(true, BIND_ADDRESS, PORT, null, PAYLOAD_SIZE,
                    MAX_MULTI_SUBSCRIPTIONS, null, blockingPdp, pdp, authenticators, sslBundlesProvider);

            assertThat(lifecycle).isNotNull().satisfies(l -> assertThat(l.isAutoStartup()).isTrue());
        }

        @Test
        @DisplayName("blank bundle name is treated as not configured (operator-friendly default)")
        void whenBlankBundleNameThenLifecycleBuiltWithoutSsl() {
            val lifecycle = sut.protobufRSocketServer(true, BIND_ADDRESS, PORT, null, PAYLOAD_SIZE,
                    MAX_MULTI_SUBSCRIPTIONS, "   ", blockingPdp, pdp, authenticators, sslBundlesProvider);

            assertThat(lifecycle).isNotNull();
        }

        @Test
        @DisplayName("bundle name set but no SslBundles bean available throws so the misconfig is visible")
        void whenBundleNameSetButRegistryAbsentThenThrows() {
            when(sslBundlesProvider.getIfAvailable()).thenReturn(null);

            assertThatThrownBy(() -> sut.protobufRSocketServer(true, BIND_ADDRESS, PORT, null, PAYLOAD_SIZE,
                    MAX_MULTI_SUBSCRIPTIONS, BUNDLE_NAME, blockingPdp, pdp, authenticators, sslBundlesProvider))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(BUNDLE_NAME)
                    .hasMessageContaining("SslBundles");
        }

        @Test
        @DisplayName("bundle name not present in the SslBundles registry throws a clear error")
        void whenBundleNameUnknownInRegistryThenThrows(@Mock SslBundles registry) {
            when(sslBundlesProvider.getIfAvailable()).thenReturn(registry);
            when(registry.getBundle(BUNDLE_NAME))
                    .thenThrow(new NoSuchSslBundleException(BUNDLE_NAME, "not configured"));

            assertThatThrownBy(() -> sut.protobufRSocketServer(true, BIND_ADDRESS, PORT, null, PAYLOAD_SIZE,
                    MAX_MULTI_SUBSCRIPTIONS, BUNDLE_NAME, blockingPdp, pdp, authenticators, sslBundlesProvider))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(BUNDLE_NAME)
                    .hasCauseInstanceOf(NoSuchSslBundleException.class);
        }

        @Test
        @DisplayName("bundle present yields a lifecycle wired with the resolved SslContext")
        void whenBundlePresentThenLifecycleBuiltWithSsl(@Mock SslBundles registry, @Mock SslBundle bundle,
                @Mock SslManagerBundle managers) throws Exception {
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            val keyStore          = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyManagerFactory.init(keyStore, new char[0]);
            lenient().when(sslBundlesProvider.getIfAvailable()).thenReturn(registry);
            lenient().when(registry.getBundle(BUNDLE_NAME)).thenReturn(bundle);
            lenient().when(bundle.getManagers()).thenReturn(managers);
            lenient().when(managers.getKeyManagerFactory()).thenReturn(keyManagerFactory);

            assertThatCode(() -> sut.protobufRSocketServer(true, BIND_ADDRESS, PORT, null, PAYLOAD_SIZE,
                    MAX_MULTI_SUBSCRIPTIONS, BUNDLE_NAME, blockingPdp, pdp, authenticators, sslBundlesProvider))
                    .doesNotThrowAnyException();
        }
    }
}
