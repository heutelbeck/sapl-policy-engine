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
package io.sapl.spring.config;

import io.sapl.api.model.Value;
import io.sapl.spring.subscriptions.SubscriptionSecretsInjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JWT secrets auto-configuration")
class JwtSecretsAutoConfigurationTests {

    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations
            .of(JwtSecretsAutoConfiguration.class);

    private static final Jwt TEST_JWT = new Jwt("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature", Instant.now(),
            Instant.now().plusSeconds(3600), Map.of("alg", "RS256"), Map.of("sub", "user"));

    @Nested
    @DisplayName("when disabled")
    class WhenDisabled {

        @Test
        @DisplayName("no injector bean when property is absent")
        void whenPropertyAbsent_thenNoInjectorBean() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .run(context -> assertThat(context).doesNotHaveBean(SubscriptionSecretsInjector.class));
        }

        @Test
        @DisplayName("no injector bean when property is false")
        void whenPropertyFalse_thenNoInjectorBean() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=false")
                    .run(context -> assertThat(context).doesNotHaveBean(SubscriptionSecretsInjector.class));
        }

        @Test
        @DisplayName("no injector bean when JwtAuthenticationToken class is absent")
        void whenJwtClassAbsent_thenNoInjectorBean() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true")
                    .withClassLoader(new FilteredClassLoader(JwtAuthenticationToken.class))
                    .run(context -> assertThat(context).doesNotHaveBean(SubscriptionSecretsInjector.class));
        }

    }

    @Nested
    @DisplayName("when enabled")
    class WhenEnabled {

        @Test
        @DisplayName("creates injector bean when property is true and class is present")
        void whenEnabledAndClassPresent_thenInjectorBeanCreated() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true")
                    .run(context -> assertThat(context).hasSingleBean(SubscriptionSecretsInjector.class));
        }

        @Test
        @DisplayName("extracts token from JwtAuthenticationToken")
        void whenJwtAuthentication_thenTokenExtracted() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true").run(context -> {
                        var injector = context.getBean(SubscriptionSecretsInjector.class);
                        var authentication = new JwtAuthenticationToken(TEST_JWT,
                                AuthorityUtils.createAuthorityList("ROLE_USER"));
                        var secrets = injector.injectSecrets(authentication);
                        assertThat(secrets.get("jwt")).isEqualTo(Value.of(TEST_JWT.getTokenValue()));
                    });
        }

        @Test
        @DisplayName("returns empty object for non-JWT authentication")
        void whenNonJwtAuthentication_thenEmptySecrets() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true").run(context -> {
                        var injector = context.getBean(SubscriptionSecretsInjector.class);
                        var authentication = new org.springframework.security.authentication.TestingAuthenticationToken("user",
                                "password");
                        assertThat(injector.injectSecrets(authentication)).isEqualTo(Value.EMPTY_OBJECT);
                    });
        }

        @Test
        @DisplayName("uses custom secrets key name")
        void whenCustomSecretsKey_thenUsesCustomKey() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true", "io.sapl.jwt.secrets-key=accessToken")
                    .run(context -> {
                        var injector = context.getBean(SubscriptionSecretsInjector.class);
                        var authentication = new JwtAuthenticationToken(TEST_JWT,
                                AuthorityUtils.createAuthorityList("ROLE_USER"));
                        var secrets = injector.injectSecrets(authentication);
                        assertThat(secrets.get("jwt")).isNull();
                        assertThat(secrets.get("accessToken")).isEqualTo(Value.of(TEST_JWT.getTokenValue()));
                    });
        }

        @Test
        @DisplayName("when authentication causes runtime exception then returns empty object")
        void whenRuntimeExceptionThenReturnsEmptyObject() {
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS)
                    .withPropertyValues("io.sapl.jwt.inject-token=true").run(context -> {
                        var injector = context.getBean(SubscriptionSecretsInjector.class);
                        var authentication = mock(JwtAuthenticationToken.class);
                        when(authentication.getToken()).thenReturn(null);
                        assertThat(injector.injectSecrets(authentication)).isEqualTo(Value.EMPTY_OBJECT);
                    });
        }

    }

}
