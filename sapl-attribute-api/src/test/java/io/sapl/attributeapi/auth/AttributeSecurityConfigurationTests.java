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
package io.sapl.attributeapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class AttributeSecurityConfigurationTests {
    private Logger                      securityConfigLogger;
    private ListAppender<ILoggingEvent> appender;

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(AttributeSecurityConfiguration.class);

    @BeforeEach
    void setUp() {
        securityConfigLogger = loggerFor(AttributeSecurityConfiguration.class);
        appender             = new ListAppender<>();
        appender.start();
        securityConfigLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        securityConfigLogger.detachAppender(appender);
    }

    private static Logger loggerFor(Class<?> append) {
        return (Logger) LoggerFactory.getLogger(append);
    }

    @Test
    @DisplayName("If no authentication is set the startup fails")
    void whenNoAuthMechanismEnabledThenStartupFails() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    @DisplayName("Basic auth with configured users logs activation")
    void whenBasicAuthEnabledWithUsersThenActivationIsLogged() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-basic-auth=true",
                        "io.sapl.attribute-api.users[0].username=testuser",
                        "io.sapl.attribute-api.users[0].secret=testsecret")

                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Basic authentication activated.",
                                "Basic authentication is enabled. Browsers auto-attach Basic credentials, which exposes a CSRF surface that API key and OAuth2 JWT do not. Prefer Bearer auth for production. See https://sapl.io/docs/latest/7_6_Security."));
    }

    @Test
    @DisplayName("Basic auth without configured users logs activation and a warning for missing username and password")
    void whenBasicAuthEnabledWithoutUsersThenActivationAndWarningIsLogged() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-basic-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Basic authentication activated.",
                                "Basic authentication is enabled. Browsers auto-attach Basic credentials, which exposes a CSRF surface that API key and OAuth2 JWT do not. Prefer Bearer auth for production. See https://sapl.io/docs/latest/7_6_Security.",
                                "Basic authentication is enabled but no users with basic credentials are configured."));
    }

    @Test
    @DisplayName("API key without a api key user logs a warning")
    void whenApiKeyAuthEnabledWithoutApiKeyUserThenWarningIsLogged() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true",
                        "io.sapl.attribute-api.allow-api-key-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("API key authentication activated.",
                                "API key authentication is enabled but no api key is defined."));
    }

    @Test
    @DisplayName("OAuth2 activation is logged before the issuer discovery is failing")
    void whenOAuth2EnabledThenActivationLoggedBeforeTheStartupFails() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true",
                "io.sapl.attribute-api.allow-oauth2-auth=true").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                            .contains("OAuth2 authentication activated.");
                });
    }

    @Test
    @DisplayName("The default claim for OIDC is pdp_id when not overriden by a user's configuration")
    void whenOidcPdpIdClaimIsSetThenCustomClaimNameOverridesDefault() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true")
                .run(context -> assertThat(
                        context.getBean(AttributeApiSecurityProperties.class).getOauth2().getOidcPdpIdClaim())
                        .isEqualTo("pdp_id"));
    }

    @Test
    @DisplayName("When a custom claim is set then the claim name is overwritten")
    void whenOidcPdpIdClaimIsSetThenDefaultClaimNameIsPdpId() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true",
                        "io.sapl.attribute-api.oauth2.oidc-pdp-id-claim=my_claim")
                .run(context -> assertThat(
                        context.getBean(AttributeApiSecurityProperties.class).getOauth2().getOidcPdpIdClaim())
                        .isEqualTo("my_claim"));
    }

    @Test
    @DisplayName("When no auth is configured then permit all requests but log a warning during startup")
    void whenOnlyNoAuthConfiguredThenPermitAllAndLogWarning() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .contains("Server has been configured to reply to requests without authentication."));
    }

    @Test
    @DisplayName("When API key auth is enab led with a configured user then no warning is logged")
    void whenApiKeyAuthEnabledWithConfiguredUserThenNoWarningIsLogged() {
        var encodedApiKey = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(UUID.randomUUID().toString());

        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true",
                "io.sapl.attribute-api.allow-api-key-auth=true", "io.sapl.attribute-api.users[0].id=test-user",
                "io.sapl.attribute-api.users[0].pdp-id=test-user", "io.sapl.attribute-api.users[0].api-key-id=test",
                "io.sapl.attribute-api.users[0].api-key=" + encodedApiKey)
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("API key authentication activated."));
    }
}
