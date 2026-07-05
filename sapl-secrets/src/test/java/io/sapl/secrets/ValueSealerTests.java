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
package io.sapl.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.nimbusds.jose.jwk.OctetKeyPair;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;

@DisplayName("Value sealing")
class ValueSealerTests {

    private final OctetKeyPair recipient = SecretSealing.generateRecipientKey();

    private OctetKeyPair sealingKey() {
        return recipient.toPublicJWK();
    }

    private static ObjectValue sampleSecrets() {
        return ObjectValue.builder()
                .put("odoo",
                        ObjectValue.builder().put("api_key", Value.of("TOP-SECRET-VALUE")).put("port", Value.of(8069L))
                                .put("tls", Value.of(true)).build())
                .put("scopes", Value.ofArray(Value.of("openid"), Value.of("profile"))).build();
    }

    static Stream<Value> scalars() {
        return Stream.of(Value.of("a-string"), Value.of(8069L), Value.of(true), Value.NULL);
    }

    static Stream<Value> unsealableValues() {
        return Stream.of(Value.error("boom"), Value.UNDEFINED,
                ObjectValue.builder().put("bad", Value.error("boom")).build());
    }

    static Stream<String> nonScalarLeafPayloads() {
        return Stream.of("[1,2,3]", "{}", "{ broken");
    }

    @MethodSource("scalars")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a scalar leaf seals to an ENC token and unseals to its original type and value")
    void whenScalarSealedThenTypeAndValueRestored(Value scalar) {
        var sealed = ValueSealer.seal(sealingKey(), scalar);
        assertThat(sealed).isInstanceOfSatisfying(TextValue.class,
                token -> assertThat(token.value()).startsWith("ENC[").endsWith("]"));
        assertThat(ValueSealer.unseal(recipient, sealed)).isEqualTo(scalar);
    }

    @Test
    @DisplayName("hasSealedShape is true for a fully sealed object, false when a leaf is plaintext, true when empty")
    void hasSealedShapeReflectsLeafState() {
        var sealed = ValueSealer.seal(sealingKey(), sampleSecrets());
        assertThat(ValueSealer.hasSealedShape(sealed)).isTrue();
        assertThat(ValueSealer.hasSealedShape(sampleSecrets())).isFalse();
        assertThat(ValueSealer.hasSealedShape(Value.EMPTY_OBJECT)).isTrue();
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("a nested object round-trips with structure, keys and scalar types intact")
        void whenObjectSealedThenRoundTrips() {
            var secrets = sampleSecrets();
            var sealed  = ValueSealer.seal(sealingKey(), secrets);
            assertThat(sealed).isInstanceOf(ObjectValue.class);
            assertThat(ValueSealer.unseal(recipient, sealed)).isEqualTo(secrets);
        }

        @Test
        @DisplayName("array elements are sealed and round-trip")
        void whenArraySealedThenRoundTrips() {
            var array = Value.ofArray(Value.of("a"), Value.of("b"));
            assertThat(ValueSealer.unseal(recipient, ValueSealer.seal(sealingKey(), array))).isEqualTo(array);
        }

        @Test
        @DisplayName("an empty object round-trips")
        void whenEmptyObjectThenRoundTrips() {
            assertThat(ValueSealer.unseal(recipient, ValueSealer.seal(sealingKey(), Value.EMPTY_OBJECT)))
                    .isEqualTo(Value.EMPTY_OBJECT);
        }
    }

    @Nested
    @DisplayName("confidentiality and structure")
    class Structure {

        @Test
        @DisplayName("keys and structure remain in cleartext while values are sealed")
        void whenSealedThenKeysRemainAndValuesAreSealed() {
            var json = ValueJsonMarshaller.toPrettyString(ValueSealer.seal(sealingKey(), sampleSecrets()));
            assertThat(json).contains("odoo", "api_key", "port", "scopes", "ENC[").doesNotContain("TOP-SECRET-VALUE");
        }
    }

    @MethodSource("unsealableValues")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a value that cannot legitimately appear in a secrets section is refused (fail closed)")
    void whenSealingUnsupportedValueThenThrows(Value value) {
        var key = sealingKey();
        assertThatThrownBy(() -> ValueSealer.seal(key, value)).isInstanceOf(SecretSealingException.class);
    }

    @MethodSource("nonScalarLeafPayloads")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a sealed leaf that decrypts to a non-scalar or unparseable payload is refused (fail closed)")
    void whenSealedLeafIsNotScalarThenThrows(String payload) {
        var token   = SecretSealing.seal(sealingKey(), payload);
        var secrets = ObjectValue.builder().put("field", Value.of("ENC[" + token + "]")).build();
        assertThatThrownBy(() -> ValueSealer.unseal(recipient, secrets)).isInstanceOf(SecretSealingException.class);
    }

    @Nested
    @DisplayName("unseal")
    class Unseal {

        @Test
        @DisplayName("a value with no sealed leaves is returned unchanged")
        void whenNothingSealedThenPassedThrough() {
            var plain = sampleSecrets();
            assertThat(ValueSealer.unseal(recipient, plain)).isEqualTo(plain);
        }

        @Test
        @DisplayName("text that opens the marker but is not a complete token is left unchanged")
        void whenTextHasPartialMarkerThenPassedThrough() {
            var plain = ObjectValue.builder().put("hint", Value.of("ENC[not-closed")).build();
            assertThat(ValueSealer.unseal(recipient, plain)).isEqualTo(plain);
        }

        @Test
        @DisplayName("unsealing with the wrong key is rejected")
        void whenWrongKeyThenThrows() {
            var wrongKey = SecretSealing.generateRecipientKey();
            var sealed   = ValueSealer.seal(sealingKey(), sampleSecrets());
            assertThatThrownBy(() -> ValueSealer.unseal(wrongKey, sealed)).isInstanceOf(SecretSealingException.class);
        }
    }
}
