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
package io.sapl.spring.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import io.sapl.pdp.remote.ProtobufRemoteReactivePolicyDecisionPoint;
import io.sapl.pdp.remote.RemoteHttpReactivePolicyDecisionPoint;
import lombok.val;

class RemotePDPAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemotePDPAutoConfiguration.class));

    @Configuration
    static class StubClientRegistrationRepositoryConfiguration {

        @Bean
        ReactiveClientRegistrationRepository clientRegistrationRepository() {
            val registration = ClientRegistration.withRegistrationId("sapl-pdp").clientId("sapl-pdp-client")
                    .clientSecret("secret").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("http://example.invalid/token").build();
            return new InMemoryReactiveClientRegistrationRepository(registration);
        }
    }

    @Test
    void whenValidHttpBasicPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=http",
                "io.sapl.pdp.remote.host=https://localhost:8443", "io.sapl.pdp.remote.key=aKey",
                "io.sapl.pdp.remote.secret=aSecret", "io.sapl.pdp.remote.enabled=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RemoteHttpReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidHttpApiKeyPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=https://localhost:8443", "io.sapl.pdp.remote.bearerToken=anApiKey")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RemoteHttpReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidHttpPropertiesWithIgnoreCertificatesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=https://localhost:8443", "io.sapl.pdp.remote.key=aKey",
                        "io.sapl.pdp.remote.secret=aSecret", "io.sapl.pdp.remote.ignoreCertificates=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RemoteHttpReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidRSocketApiKeyPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000",
                        "io.sapl.pdp.remote.bearerToken=anApiKey", "io.sapl.pdp.remote.allow-insecure-http=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidRSocketBasicPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000", "io.sapl.pdp.remote.key=aKey",
                "io.sapl.pdp.remote.secret=aSecret", "io.sapl.pdp.remote.allow-insecure-http=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidRSocketTlsPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000",
                "io.sapl.pdp.remote.bearerToken=anApiKey", "io.sapl.pdp.remote.tls=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenValidRSocketIgnoreCertificatesPropertiesArePresent_thenTheRemotePdpIsPresent() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000",
                        "io.sapl.pdp.remote.bearerToken=anApiKey", "io.sapl.pdp.remote.ignoreCertificates=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenHttpOauth2WithRegistrationBean_thenTheRemotePdpIsPresent() {
        contextRunner.withUserConfiguration(StubClientRegistrationRepositoryConfiguration.class)
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=https://localhost:8443",
                        "io.sapl.pdp.remote.oauth2.client-registration-id=sapl-pdp")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RemoteHttpReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenRSocketOauth2WithRegistrationBean_thenTheRemotePdpIsPresent() {
        contextRunner.withUserConfiguration(StubClientRegistrationRepositoryConfiguration.class)
                .withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000",
                        "io.sapl.pdp.remote.oauth2.client-registration-id=sapl-pdp",
                        "io.sapl.pdp.remote.allow-insecure-http=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenHttpCredentialsOverPlaintextAndNotAllowed_thenContextFails() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                "io.sapl.pdp.remote.host=http://localhost:8080", "io.sapl.pdp.remote.key=aKey",
                "io.sapl.pdp.remote.secret=aSecret").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenHttpCredentialsOverPlaintextButExplicitlyAllowed_thenTheRemotePdpIsPresent() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=http://localhost:8080", "io.sapl.pdp.remote.key=aKey",
                        "io.sapl.pdp.remote.secret=aSecret", "io.sapl.pdp.remote.allow-insecure-http=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RemoteHttpReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenRSocketCredentialsWithoutTlsAndNotAllowed_thenContextFails() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=rsocket", "io.sapl.pdp.remote.enabled=true",
                "io.sapl.pdp.remote.host=localhost", "io.sapl.pdp.remote.port=7000",
                "io.sapl.pdp.remote.bearerToken=anApiKey").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenOauth2RegistrationIdSetButNoRepositoryBean_thenContextFails() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.enabled=true",
                        "io.sapl.pdp.remote.host=https://localhost:8443",
                        "io.sapl.pdp.remote.oauth2.client-registration-id=sapl-pdp")
                .run(context -> assertThat(context).hasFailed());
    }

}
