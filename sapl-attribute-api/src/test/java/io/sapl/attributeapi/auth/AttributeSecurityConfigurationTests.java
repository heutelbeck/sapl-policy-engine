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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    @DisplayName("when no authentication mode is set then the startup fails")
    void whenNoAuthMechanismEnabledThenStartupFails() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    @DisplayName("when basic auth is used with correct user then activation is logged")
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
    @DisplayName("when username or password are missing in basic auth then a warning is logged")
    void whenBasicAuthEnabledWithoutUsersThenActivationAndWarningIsLogged() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-basic-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Basic authentication activated.",
                                "Basic authentication is enabled. Browsers auto-attach Basic credentials, which exposes a CSRF surface that API key and OAuth2 JWT do not. Prefer Bearer auth for production. See https://sapl.io/docs/latest/7_6_Security.",
                                "Basic authentication is enabled but no users with basic credentials are configured."));
    }

    @Test
    @DisplayName("when an API key has no user then log a warning")
    void whenApiKeyAuthEnabledWithoutApiKeyUserThenWarningIsLogged() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true",
                        "io.sapl.attribute-api.allow-api-key-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("API key authentication activated.",
                                "API key authentication is enabled but no api key is defined."));
    }

    @Test
    @DisplayName("when an api key has no key id then log a warning")
    void whenApiKeyAuthEnabledWithoutApiKeyIdThenWarningIsLogged() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true",
                "io.sapl.attribute-api.allow-api-key-auth=true", "io.sapl.attribute-api.users[0].username=testuser")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .contains("API key authentication is enabled but no api key is defined."));
    }

    @Test
    @DisplayName("when the issuer discovery failed the OAuth2 message is still logged")
    void whenOAuth2EnabledThenActivationLoggedBeforeTheStartupFails() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true",
                "io.sapl.attribute-api.allow-oauth2-auth=true").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                            .contains("OAuth2 authentication activated.");
                });
    }

    @Test
    @DisplayName("when OIDC pdp claim is set then the custom claim overrides the default claim")
    void whenOidcPdpIdClaimIsSetThenCustomClaimNameOverridesDefault() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true")
                .run(context -> assertThat(
                        context.getBean(AttributeApiSecurityProperties.class).getOauth2().getOidcPdpIdClaim())
                        .isEqualTo("pdp_id"));
    }

    @Test
    @DisplayName("when a custom claim is set then the claim name is overwritten")
    void whenOidcPdpIdClaimIsSetThenDefaultClaimNameIsPdpId() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true",
                        "io.sapl.attribute-api.oauth2.oidc-pdp-id-claim=my_claim")
                .run(context -> assertThat(
                        context.getBean(AttributeApiSecurityProperties.class).getOauth2().getOidcPdpIdClaim())
                        .isEqualTo("my_claim"));
    }

    @Test
    @DisplayName("when no auth is configured then permit all requests but log a warning during startup")
    void whenOnlyNoAuthConfiguredThenPermitAllAndLogWarning() {
        contextRunner
                .withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true")
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .contains("Server has been configured to reply to requests without authentication."));
    }

    @Test
    @DisplayName("when API key auth is enab led with a configured user then no warning is logged")
    void whenApiKeyAuthEnabledWithConfiguredUserThenNoWarningIsLogged() {
        var encodedApiKey = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(UUID.randomUUID().toString());

        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true",
                "io.sapl.attribute-api.allow-api-key-auth=true", "io.sapl.attribute-api.users[0].id=test-user",
                "io.sapl.attribute-api.users[0].pdp-id=test-user", "io.sapl.attribute-api.users[0].api-key-id=test",
                "io.sapl.attribute-api.users[0].api-key=" + encodedApiKey)
                .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("API key authentication activated."));
    }

    @Nested
    @DisplayName("UserDetailsService")
    class WhenUserDetailsServiceIsUsed {
        private static final String SECRET = "$argon2id$v=19$m=16384,t=2,p=1$TAKAbikXvm+RzKPUysWxog$Iv+pnfhWQst5SgL63Bu5VML6EruahgZxutrCBcntuAU";

        private UserDetailsService serviceFor(List<AttributeApiSecurityProperties.UserEntry> users) {
            var properties = new AttributeApiSecurityProperties();
            properties.setUsers(users);
            return new AttributeSecurityConfiguration(properties,
                    Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()).userDetailsService();
        }

        @Test
        @DisplayName("when a basic user is known then return matching user details")
        void whenUserExistsThenReturnUserDetails() {
            var user = new AttributeApiSecurityProperties.UserEntry();
            user.setPdpId("tenant-a");
            user.setUsername("alice");
            user.setSecret(SECRET);

            var details = serviceFor(List.of(user)).loadUserByUsername("alice");

            assertThat(details.getUsername()).isEqualTo("alice");
            assertThat(details.getPassword()).isEqualTo(SECRET);
        }

        @Test
        @DisplayName("when no user matches the config then throw exception")
        void whenNoUserMatchesThenThrows() {
            var service = serviceFor(List.of());

            assertThatThrownBy(() -> service.loadUserByUsername("unknownUser"))
                    .isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("unknownUser");
        }

        @Test
        @DisplayName("when the user is API key only and has no username then throw an exception")
        void whenUserHasNoBasicAuthThenNotMatched() {
            var service = serviceFor(List.of(new AttributeApiSecurityProperties.UserEntry()));

            assertThatThrownBy(() -> service.loadUserByUsername("alice")).isInstanceOf(UsernameNotFoundException.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "allow-basic-auth", "allow-api-key-auth", "allow-oauth2-auth" })
        @DisplayName("when no auth is enabled with another auth mode then no auth warning is not sent")
        void whenNoAuthCombinedWithOtherAuthModeThenNoWarnLogging(String authMode) {
            contextRunner
                    .withPropertyValues("io.sapl.attribute-api.enabled=true",
                            "io.sapl.attribute-api.allow-no-auth=true", "io.sapl.attribute-api." + authMode + "=true")
                    .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                            .doesNotContain("Server has been configured to reply to requests without authentication."));
        }

        @Test
        @DisplayName("when user is set without username then log warning")
        void whenUserIsSetWithoutUsernameThenLogWarning() {
            contextRunner
                    .withPropertyValues("io.sapl.attribute-api.enabled=true",
                            "io.sapl.attribute-api.allow-basic-auth=true", "io.sapl.attribute-api.users[0].id=user-1",
                            "io.sapl.attribute-api.users[0].secret=testsecret")
                    .run(context -> assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).contains(
                            "Basic authentication is enabled but no users with basic credentials are configured."));
        }

        @Test
        @DisplayName("when a tenant is resolved then the the pdp id of the user is returned or an empty string")
        void whenTenantIsResolvedThenPdpIdOrEmptyStringIsReturned() {
            var resolver = new AttributeSecurityConfiguration(new AttributeApiSecurityProperties(),
                    Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()).attributeApiBlockingTenantResolver();

            // Empty the auth context
            SecurityContextHolder.clearContext();
            assertThat(resolver.resolve()).isEmpty();

            // with empty pdp id
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AttributeApiUserDetails("without-pdp-id", null, null), null, List.of()));
            assertThat(resolver.resolve()).isEmpty();

            // with a pdp id
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AttributeApiUserDetails("with-pdp-id", null, "pdp-id-a"), null, List.of()));
            assertThat(resolver.resolve()).isEqualTo("pdp-id-a");
        }
    }
}
