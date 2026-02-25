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
package io.sapl.api.pdp;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Secrets handling in authorization subscriptions")
class AuthorizationSubscriptionSecretsTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Nested
    @DisplayName("AuthorizationSubscription.toString()")
    class ToStringRedaction {

        @Test
        @DisplayName("redacts secrets when present")
        void whenSecretsPresent_thenToStringRedacts() {
            val secrets      = Value.ofObject(Map.of("apiKey", Value.of("super-secret-key-12345")));
            val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                    Value.UNDEFINED, secrets);

            val result = subscription.toString();

            assertThat(result).contains("SECRETS REDACTED").doesNotContain("super-secret-key-12345")
                    .doesNotContain("apiKey");
        }

        @Test
        @DisplayName("omits secrets field when empty")
        void whenSecretsEmpty_thenToStringOmitsSecretsField() {
            val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                    Value.UNDEFINED, Value.EMPTY_OBJECT);

            val result = subscription.toString();

            assertThat(result).doesNotContain("secrets").doesNotContain("REDACTED");
        }
    }

    @Nested
    @DisplayName("MultiAuthorizationSubscription.toString()")
    class MultiToStringRedaction {

        @Test
        @DisplayName("redacts secrets from nested subscriptions")
        void whenNestedSubscriptionsHaveSecrets_thenToStringRedacts() {
            val secrets = Value.ofObject(Map.of("jwt", Value.of("eyJhbGciOi.payload.signature")));
            val multi   = new MultiAuthorizationSubscription().addSubscription("read-data",
                    new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("data"), Value.UNDEFINED,
                            secrets));

            val result = multi.toString();

            assertThat(result).contains("SECRETS REDACTED").contains("read-data").doesNotContain("eyJhbGciOi")
                    .doesNotContain("jwt");
        }

        @Test
        @DisplayName("handles mix of subscriptions with and without secrets")
        void whenMixedSecrets_thenOnlyRedactsThoseWithSecrets() {
            val secrets = Value.ofObject(Map.of("token", Value.of("secret-token-value")));
            val multi   = new MultiAuthorizationSubscription()
                    .addSubscription("with-secrets",
                            new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("sensitive"),
                                    Value.UNDEFINED, secrets))
                    .addSubscription("without-secrets", new AuthorizationSubscription(Value.of("user"),
                            Value.of("read"), Value.of("public"), Value.UNDEFINED, Value.EMPTY_OBJECT));

            val result = multi.toString();

            assertThat(result).contains("SECRETS REDACTED").contains("with-secrets").contains("without-secrets")
                    .doesNotContain("secret-token-value");
        }
    }

    @Nested
    @DisplayName("MultiAuthorizationSubscription convenience methods with secrets")
    class MultiConvenienceMethods {

        @Test
        @DisplayName("Value-based overload passes secrets to inner subscription")
        void whenAddingWithValueSecrets_thenSubscriptionContainsSecrets() {
            val secrets = Value.ofObject(Map.of("key", Value.of("secret-value")));
            val multi   = new MultiAuthorizationSubscription().addAuthorizationSubscription("test-sub",
                    Value.of("user"), Value.of("read"), Value.of("data"), Value.UNDEFINED, secrets);

            assertThat(multi.getSubscription("test-sub").secrets()).isEqualTo(secrets);
        }

        @Test
        @DisplayName("Object-based overload passes secrets to inner subscription")
        void whenAddingWithObjectSecrets_thenSubscriptionContainsSecrets() {
            val multi = new MultiAuthorizationSubscription().addAuthorizationSubscription("test-sub", "user", "read",
                    "data", null, Map.of("key", "secret-value"));

            assertThat(multi.getSubscription("test-sub").secrets()).isNotEqualTo(Value.EMPTY_OBJECT)
                    .satisfies(secrets -> assertThat(secrets.get("key")).isNotNull());
        }

        @Test
        @DisplayName("Object-based overload with mapper passes secrets to inner subscription")
        void whenAddingWithObjectSecretsAndMapper_thenSubscriptionContainsSecrets() {
            val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val multi  = new MultiAuthorizationSubscription().addAuthorizationSubscription("test-sub", "user", "read",
                    "data", null, Map.of("key", "secret-value"), mapper);

            assertThat(multi.getSubscription("test-sub").secrets()).isNotEqualTo(Value.EMPTY_OBJECT);
        }
    }

    @Nested
    @DisplayName("Jackson serialization of secrets")
    class JacksonSecretsSerialization {

        @Test
        @DisplayName("includes secrets in JSON wire format")
        void whenSecretsPresent_thenJacksonSerializesSecrets() throws JacksonException {
            val secrets      = Value.ofObject(Map.of("apiKey", Value.of("wire-secret-123")));
            val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                    Value.UNDEFINED, secrets);

            val json = MAPPER.writeValueAsString(subscription);

            assertThat(json).contains("\"secrets\"").contains("wire-secret-123").contains("apiKey");
        }

        @Test
        @DisplayName("omits secrets from JSON when empty")
        void whenSecretsEmpty_thenJacksonOmitsSecretsField() throws JacksonException {
            val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                    Value.UNDEFINED, Value.EMPTY_OBJECT);

            val json = MAPPER.writeValueAsString(subscription);

            assertThat(json).doesNotContain("\"secrets\"");
        }

        @Test
        @DisplayName("round-trips secrets through serialization and deserialization")
        void whenSecretsPresent_thenRoundTripPreservesSecrets() throws JacksonException {
            val secrets  = Value.ofObject(Map.of("apiKey", Value.of("round-trip-secret")));
            val original = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                    Value.UNDEFINED, secrets);

            val json     = MAPPER.writeValueAsString(original);
            val restored = MAPPER.readValue(json, AuthorizationSubscription.class);

            assertThat(restored.secrets()).isEqualTo(secrets);
        }

        @Test
        @DisplayName("deserializes JSON with secrets field")
        void whenJsonContainsSecrets_thenDeserializesCorrectly() throws JacksonException {
            val json         = """
                    {"subject":"user","action":"read","resource":"data","secrets":{"token":"deserialized-secret"}}""";
            val subscription = MAPPER.readValue(json, AuthorizationSubscription.class);

            assertThat(subscription.secrets())
                    .isEqualTo(Value.ofObject(Map.of("token", Value.of("deserialized-secret"))));
        }

        @Test
        @DisplayName("defaults secrets to empty object when absent in JSON")
        void whenJsonMissingSecrets_thenDefaultsToEmptyObject() throws JacksonException {
            val json         = """
                    {"subject":"user","action":"read","resource":"data"}""";
            val subscription = MAPPER.readValue(json, AuthorizationSubscription.class);

            assertThat(subscription.secrets()).isEqualTo(Value.EMPTY_OBJECT);
        }
    }
}
