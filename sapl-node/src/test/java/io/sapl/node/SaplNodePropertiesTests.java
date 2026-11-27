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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.UserEntry;
import io.sapl.node.boot.SaplStartupConfigurationException;
import lombok.val;

@DisplayName("SaplNodeProperties")
class SaplNodePropertiesTests {

    @Test
    @DisplayName("every authentication mode defaults to disabled so the node fails closed")
    void whenConstructedThenAllAuthenticationModesDefaultToDisabled() {
        var properties = new SaplNodeProperties();

        assertThat(properties).satisfies(p -> {
            assertThat(p.isAllowNoAuth()).isFalse();
            assertThat(p.isAllowBasicAuth()).isFalse();
            assertThat(p.isAllowApiKeyAuth()).isFalse();
            assertThat(p.isAllowOauth2Auth()).isFalse();
        });
    }

    @Nested
    @DisplayName("pdpId normalization")
    class PdpIdNormalizationTests {

        @Test
        @DisplayName("normalizes missing pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdMissingThenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", null);
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("normalizes blank pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdBlankThenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", "   ");
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("preserves pdpId when explicitly set")
        void whenPdpIdSetThenPreserved() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);

            var user = createUserEntry("user-1", "production");
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("production");
        }

    }

    @Nested
    @DisplayName("pdpId rejection")
    class PdpIdRejectionTests {

        @Test
        @DisplayName("throws on missing pdpId when rejectOnMissingPdpId is true, regardless of setter order")
        void whenRejectEnabledAndPdpIdMissingThenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);
            properties.setUsers(List.of(createUserEntry("user-1", null)));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1").hasMessageContaining("no pdp-id configured");
        }

        @Test
        @DisplayName("throws on blank pdpId when rejectOnMissingPdpId is true")
        void whenRejectEnabledAndPdpIdBlankThenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);
            properties.setUsers(List.of(createUserEntry("user-1", "   ")));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1");
        }

        @Test
        @DisplayName("rejection is order-independent: setUsers before setRejectOnMissingPdpId(true) still throws")
        void whenSetUsersFirstAndRejectFlippedTrueThenStillThrows() {
            var properties = new SaplNodeProperties();
            properties.setUsers(List.of(createUserEntry("user-1", null)));
            properties.setRejectOnMissingPdpId(true);

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1").hasMessageContaining("no pdp-id configured");
        }

    }

    @Nested
    @DisplayName("apiKey requires apiKeyId")
    class ApiKeyIdRequirementTests {

        private static final String VALID_API_KEY = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

        @ParameterizedTest(name = "api-key-id = \"{0}\"")
        @NullSource
        @ValueSource(strings = { "", "   " })
        @DisplayName("throws when a user has an api-key but no usable api-key-id")
        void whenApiKeySetButApiKeyIdMissingThenThrows(String apiKeyId) {
            var user = createUserEntry("api-user", "default");
            user.setApiKey(VALID_API_KEY);
            user.setApiKeyId(apiKeyId);
            var properties = new SaplNodeProperties();
            properties.setUsers(List.of(user));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(SaplStartupConfigurationException.class)
                    .hasMessageContaining("api-user").hasMessageContaining("api-key-id");
        }

        @Test
        @DisplayName("accepts a user that has both an api-key and an api-key-id")
        void whenApiKeyAndApiKeyIdSetThenAccepts() {
            var user = createUserEntry("api-user", "default");
            user.setApiKey(VALID_API_KEY);
            user.setApiKeyId("7A7ByyQd6U");
            var properties = new SaplNodeProperties();
            properties.setUsers(List.of(user));

            assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts a user with no api-key, so the requirement does not affect basic auth")
        void whenUserHasNoApiKeyThenAccepts() {
            var properties = new SaplNodeProperties();
            properties.setUsers(List.of(createUserEntry("basic-user", "default")));

            assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        }

    }

    @Nested
    @DisplayName("keys under io.sapl.node")
    class KeyTests {

        @EnableConfigurationProperties(SaplNodeProperties.class)
        static class Binding {
        }

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(Binding.class);

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "io.sapl.node.allow-basic-authentication=true", "io.sapl.node.oauth.pdp-claim=tenant",
                "io.sapl.node.users[0].secret=x", "io.sapl.node.http.max-body-bytes=1024" })
        @DisplayName("an unknown key refuses the start, so that a misspelt key does not go unnoticed")
        void whenKeyIsUnknownThenStartIsRefused(String property) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                    .isInstanceOf(UnboundConfigurationPropertiesException.class));
        }

        @Test
        @DisplayName("every key of the node is known, also those read outside of these properties")
        void whenEveryKeyOfTheNodeIsGivenThenStartIsAccepted() {
            runner.withPropertyValues("io.sapl.node.allow-no-auth=true", "io.sapl.node.allowBasicAuth=false",
                    "io.sapl.node.reject-on-missing-pdp-id=false", "io.sapl.node.default-pdp-id=default",
                    "io.sapl.node.keep-alive=20", "io.sapl.node.max-multi-subscription-count=10",
                    "io.sapl.node.http.max-request-body-bytes=1024", "io.sapl.node.http.sse.keep-alive-pool-size=2",
                    "io.sapl.node.http.auth-cache.positive-ttl=1m", "io.sapl.node.http.auth-cache.negative-ttl=1s",
                    "io.sapl.node.http.auth-cache.max-size=10", "io.sapl.node.limits.http.requests-per-second=5",
                    "io.sapl.node.limits.http.sse.max-concurrent-streams=5",
                    "io.sapl.node.limits.rsocket.max-connections=5",
                    "io.sapl.node.limits.rsocket.max-streams-per-connection=5",
                    "io.sapl.node.limits.rsocket.requests-per-second=5", "io.sapl.node.scalar.oauth-client-id=sapl",
                    "io.sapl.node.scalar.oauth-redirect-uri=/scalar", "io.sapl.node.openid-authz-api.enabled=false",
                    "io.sapl.node.oauth.pdp-id-claim=tenant", "io.sapl.node.oauth.audiences=sapl-pdp",
                    "io.sapl.node.oauth.required-scopes=sapl:pdp", "io.sapl.node.oauth.allow-jwt-without-expiry=false",
                    "io.sapl.node.oauth.allow-insecure-issuer=false")
                    .run(context -> assertThat(context).hasNotFailed());
        }

        @Test
        @DisplayName("a value that does not fit its key refuses the start")
        void whenValueDoesNotFitItsKeyThenStartIsRefused() {
            runner.withPropertyValues("io.sapl.node.keep-alive=soon").run(context -> assertThat(context).hasFailed());
        }
    }

    @Nested
    @DisplayName("credentials are Argon2 hashes")
    class Argon2CredentialTests {

        private static final String ARGON2_HASH = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

        private static final String NOT_ARGON2 = "SAPL Node refused to start. User 'cultist' has a secret or api-key that is not an Argon2 hash ($argon2id$v=19$...).";

        static Stream<Arguments> credentialsThatAreNotArgon2Hashes() {
            return Stream.of(arguments("a plaintext secret", "hunter2", null),
                    arguments("a secret with the prefix of a delegating encoder", "{argon2id}" + ARGON2_HASH, null),
                    arguments("a bcrypt secret", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy", null),
                    arguments("a blank secret", " ", null),
                    arguments("a plaintext api-key", null, "sapl_7A7ByyQd6U_secret"),
                    arguments("an api-key with the prefix of a delegating encoder", null, "{argon2id}" + ARGON2_HASH));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("credentialsThatAreNotArgon2Hashes")
        @DisplayName("a secret or api-key that is not an Argon2 hash refuses the start, as it could never authenticate")
        void whenCredentialIsNotArgon2HashThenStartIsRefused(String description, String secret, String apiKey) {
            val properties = propertiesWithUser(secret, apiKey);

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(SaplStartupConfigurationException.class)
                    .hasMessage(NOT_ARGON2);
        }

        @Test
        @DisplayName("a secret and an api-key that are Argon2 hashes are accepted")
        void whenCredentialsAreArgon2HashesThenAccepted() {
            val properties = propertiesWithUser(ARGON2_HASH, ARGON2_HASH);

            assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        }

        private SaplNodeProperties propertiesWithUser(String secret, String apiKey) {
            val user = createUserEntry("cultist", "default");
            if (secret != null) {
                val basic = new BasicCredentials();
                basic.setUsername("cultist");
                basic.setSecret(secret);
                user.setBasic(basic);
            }
            user.setApiKey(apiKey);
            user.setApiKeyId("7A7ByyQd6U");
            val properties = new SaplNodeProperties();
            properties.setUsers(List.of(user));
            return properties;
        }
    }

    private UserEntry createUserEntry(String id, String pdpId) {
        var entry = new UserEntry();
        entry.setId(id);
        entry.setPdpId(pdpId);
        return entry;
    }

}
