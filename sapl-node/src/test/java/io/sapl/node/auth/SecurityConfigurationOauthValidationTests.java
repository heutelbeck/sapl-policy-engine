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
package io.sapl.node.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.apikey.ApiKeyService;
import io.sapl.node.boot.SaplStartupConfigurationException;
import lombok.val;

/**
 * Pins the fail-fast contract of the OAuth access configuration: a malformed
 * pdp-id-claim path or a blank required scope refuses startup with an
 * operator-facing message instead of silently rejecting every token at
 * runtime.
 */
@DisplayName("SecurityConfiguration OAuth validation")
@ExtendWith(MockitoExtension.class)
class SecurityConfigurationOauthValidationTests {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserLookupService userLookupService;

    private SecurityConfiguration configurationWith(String pdpIdClaim, List<String> requiredScopes) {
        val properties = new SaplNodeProperties();
        properties.getOauth().setPdpIdClaim(pdpIdClaim);
        properties.getOauth().setRequiredScopes(requiredScopes);
        return new SecurityConfiguration(apiKeyService, properties, userLookupService);
    }

    @Test
    @DisplayName("a well-formed claim path and non-blank scopes pass validation")
    void whenConfigurationWellFormedThenStartupPasses() {
        val sut = configurationWith("resource_access.sapl.tenant", List.of("sapl:pdp"));
        assertThatCode(sut::validateOauthAccessConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a malformed pdp-id-claim path refuses startup with an operator message")
    void whenClaimPathMalformedThenStartupRefused() {
        val sut = configurationWith("resource_access..tenant", List.of());
        assertThatThrownBy(sut::validateOauthAccessConfiguration).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessageContaining("pdp-id-claim");
    }

    @Test
    @DisplayName("a blank required scope refuses startup with an operator message")
    void whenRequiredScopeBlankThenStartupRefused() {
        val sut = configurationWith("sapl_pdp_id", List.of("sapl:pdp", " "));
        assertThatThrownBy(sut::validateOauthAccessConfiguration).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessageContaining("required-scopes");
    }

    @ParameterizedTest(name = "max-size {0}")
    @ValueSource(longs = { 0L, -1L })
    @DisplayName("an authentication cache without room for an outcome refuses the start")
    void whenAuthenticationCacheMaxSizeNotPositiveThenStartRefused(long maxSize) {
        val sut         = configurationWith("sapl_pdp_id", List.of());
        val positiveTtl = Duration.ofMinutes(5);
        val negativeTtl = Duration.ofSeconds(5);
        assertThatThrownBy(() -> sut.authenticationCache(positiveTtl, negativeTtl, maxSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum number of cached authentication outcomes must be positive, got %d."
                        .formatted(maxSize));
    }

    @Nested
    @DisplayName("the scheme of the issuer")
    class IssuerSchemeTests {

        private static final String WARN_INSECURE_ISSUER = "Fetching the keys of the OAuth2 issuer over http because io.sapl.node.oauth.allow-insecure-issuer=true. Anyone on the network path can replace them and sign tokens that the node accepts. Use an https issuer outside of development.";

        private ListAppender<ILoggingEvent> appender;
        private Logger                      logger;
        private Level                       originalLevel;

        @BeforeEach
        void captureWarnings() {
            logger        = (Logger) LoggerFactory.getLogger(SecurityConfiguration.class);
            originalLevel = logger.getLevel();
            logger.setLevel(Level.WARN);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void releaseWarnings() {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }

        @ParameterizedTest(name = "{0}, opt-in {1}")
        @CsvSource({ "https://idp.example.org/realms/sapl, false", "HTTPS://idp.example.org/realms/sapl, false",
                "https://idp.example.org/realms/sapl, true" })
        @DisplayName("an https issuer is accepted without a warning")
        void whenIssuerIsHttpsThenAcceptedWithoutWarning(String issuer, boolean allowInsecureIssuer) {
            assertThatCode(() -> SecurityConfiguration.requireSecureIssuer(issuer, allowInsecureIssuer))
                    .doesNotThrowAnyException();
            assertThat(appender.list).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "http://keycloak:8080/realms/sapl", "ftp://idp.example.org/realms/sapl",
                "idp.example.org/realms/sapl", "http//broken" })
        @DisplayName("an issuer that is not https refuses the start without the opt-in, on every address")
        void whenIssuerIsNotHttpsAndNoOptInThenStartRefused(String issuer) {
            assertThatThrownBy(() -> SecurityConfiguration.requireSecureIssuer(issuer, false))
                    .isInstanceOf(SaplStartupConfigurationException.class).hasMessage("The issuer URI " + issuer
                            + " is not an https URI. An http URI is accepted only with io.sapl.node.oauth.allow-insecure-issuer=true, as its keys would be fetched in cleartext.");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "http://keycloak:8080/realms/sapl", "http://127.0.0.1:9000/realms/sapl" })
        @DisplayName("an http issuer is accepted with the opt-in and warns loudly, also on the loopback address")
        void whenIssuerIsHttpWithOptInThenAcceptedWithWarning(String issuer) {
            assertThatCode(() -> SecurityConfiguration.requireSecureIssuer(issuer, true)).doesNotThrowAnyException();
            assertThat(appender.list).singleElement()
                    .satisfies(event -> assertThat(event).returns(Level.WARN, ILoggingEvent::getLevel)
                            .returns(WARN_INSECURE_ISSUER, ILoggingEvent::getFormattedMessage));
        }

        @Test
        @DisplayName("the opt-in admits http only, no other scheme")
        void whenIssuerIsOtherSchemeWithOptInThenStartRefused() {
            assertThatThrownBy(() -> SecurityConfiguration.requireSecureIssuer("ftp://idp.example.org", true))
                    .isInstanceOf(SaplStartupConfigurationException.class);
        }
    }
}
