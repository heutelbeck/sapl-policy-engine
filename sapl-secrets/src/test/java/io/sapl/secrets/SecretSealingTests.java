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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.text.ParseException;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.nimbusds.jose.CompressionAlgorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.X25519Encrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;

import lombok.val;

@DisplayName("Secret sealing (JWE primitive)")
class SecretSealingTests {

    private static final String ERROR_KEY_DOES_NOT_FIT = "Cannot unseal a secret: the key does not fit the token, or the token was altered.";
    private static final String ERROR_MALFORMED_TOKEN  = "Cannot unseal a secret: the token is not a compact JWE of a sealed text.";

    private static final OctetKeyPair RECIPIENT = SecretSealing.generateRecipientKey();
    private static final OctetKeyPair OTHER_KEY = SecretSealing.generateRecipientKey();

    @Test
    @DisplayName("a sealed secret unseals to the original plaintext")
    void whenSealedThenUnsealRestoresPlaintext() {
        val sealed = SecretSealing.seal(RECIPIENT.toPublicJWK(), "s3cr3t-token");
        assertThat(SecretSealing.unseal(RECIPIENT, sealed)).isEqualTo("s3cr3t-token");
    }

    @Test
    @DisplayName("empty plaintext round-trips")
    void whenEmptyPlaintextThenRoundTrips() {
        val sealed = SecretSealing.seal(RECIPIENT.toPublicJWK(), "");
        assertThat(SecretSealing.unseal(RECIPIENT, sealed)).isEmpty();
    }

    @Test
    @DisplayName("sealing the same secret twice yields different tokens (fresh ephemeral key)")
    void whenSealedTwiceThenTokensDiffer() {
        val publicKey = RECIPIENT.toPublicJWK();
        assertThat(SecretSealing.seal(publicKey, "same")).isNotEqualTo(SecretSealing.seal(publicKey, "same"));
    }

    @Test
    @DisplayName("generated recipient keys are distinct")
    void whenKeysGeneratedThenTheyDiffer() {
        assertThat(SecretSealing.generateRecipientKey().toJSONString())
                .isNotEqualTo(SecretSealing.generateRecipientKey().toJSONString());
    }

    static Stream<Arguments> tokensTheKeyDoesNotFit() {
        val sealed = SecretSealing.seal(RECIPIENT.toPublicJWK(), "secret");
        return Stream.of(arguments("sealed to another key", SecretSealing.seal(OTHER_KEY.toPublicJWK(), "secret")),
                arguments("ciphertext altered", withAlteredCiphertext(sealed)));
    }

    @MethodSource("tokensTheKeyDoesNotFit")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a token the key does not fit, or an altered token, is refused naming the key or the alteration")
    void whenKeyDoesNotFitTokenThenRefusedNamingTheKey(String description, String token) {
        assertThatThrownBy(() -> SecretSealing.unseal(RECIPIENT, token)).isInstanceOf(SecretSealingException.class)
                .hasMessage(ERROR_KEY_DOES_NOT_FIT);
    }

    static Stream<Arguments> malformedTokens() throws JOSEException, ParseException {
        val sealed    = SecretSealing.seal(RECIPIENT.toPublicJWK(), "secret");
        val ephemeral = JWEObject.parse(sealed).getHeader().getEphemeralPublicKey();
        return Stream.of(arguments("not a token", "not-a-token"),
                arguments("three parts", "eyJhbGciOiJFUzI1NiJ9.e30.c2ln"),
                arguments("no ephemeral key", withHeader(sealed, agreementHeader().build())),
                arguments("ephemeral key of type EC",
                        withHeader(sealed, agreementHeader()
                                .ephemeralPublicKey(new ECKeyGenerator(Curve.P_256).generate().toPublicJWK()).build())),
                arguments("ephemeral key on the Ed25519 curve", withHeader(sealed,
                        agreementHeader()
                                .ephemeralPublicKey(new OctetKeyPairGenerator(Curve.Ed25519).generate().toPublicJWK())
                                .build())),
                arguments("critical header extension",
                        withHeader(sealed, agreementHeader().ephemeralPublicKey(ephemeral).customParam("x-sapl", 1)
                                .criticalParams(Set.of("x-sapl")).build())));
    }

    @MethodSource("malformedTokens")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a token that is not a compact JWE of a sealed text is refused as malformed")
    void whenTokenMalformedThenRefusedNamingTheToken(String description, String token) {
        assertThatThrownBy(() -> SecretSealing.unseal(RECIPIENT, token)).isInstanceOf(SecretSealingException.class)
                .hasMessage(ERROR_MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("sealing with a key whose curve is not X25519 is rejected")
    void whenSealingWithIncompatibleKeyThenThrows() throws JOSEException {
        val ed25519 = new OctetKeyPairGenerator(Curve.Ed25519).generate().toPublicJWK();
        assertThatThrownBy(() -> SecretSealing.seal(ed25519, "secret")).isInstanceOf(SecretSealingException.class);
    }

    static Stream<JWEHeader> rejectedHeaders() {
        return Stream.of(new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM).build(),
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A128KW, EncryptionMethod.A256GCM).build(),
                agreementHeader().compressionAlgorithm(CompressionAlgorithm.DEF).build());
    }

    @MethodSource("rejectedHeaders")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a token whose header is not the pinned uncompressed ECDH-ES/A256GCM is refused")
    void whenTokenHeaderNotAcceptedThenRefused(JWEHeader header) throws JOSEException {
        val jwe = new JWEObject(header, new Payload("secret"));
        jwe.encrypt(new X25519Encrypter(RECIPIENT.toPublicJWK()));
        val token = jwe.serialize();
        assertThatThrownBy(() -> SecretSealing.unseal(RECIPIENT, token)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("Refusing to unseal");
    }

    private static JWEHeader.Builder agreementHeader() {
        return new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM);
    }

    private static String withHeader(String token, JWEHeader header) {
        return header.toBase64URL() + token.substring(token.indexOf('.'));
    }

    private static String withAlteredCiphertext(String token) {
        val parts       = token.split("\\.", -1);
        val ciphertext  = parts[3];
        val replacement = ciphertext.charAt(0) == 'A' ? 'B' : 'A';
        parts[3] = replacement + ciphertext.substring(1);
        return String.join(".", parts);
    }

    @Nested
    @DisplayName("key id assignment")
    class KeyIdAssignment {

        @Test
        @DisplayName("a key generated with a chosen key id carries that id")
        void whenGeneratedWithKeyIdThenKidIsThatId() {
            assertThat(SecretSealing.generateRecipientKey("alpha").getKeyID()).isEqualTo("alpha");
        }

        @Test
        @DisplayName("two keys generated without a chosen id get distinct key ids")
        void whenTwoKeysGeneratedThenKeyIdsDiffer() {
            assertThat(SecretSealing.generateRecipientKey().getKeyID())
                    .isNotEqualTo(SecretSealing.generateRecipientKey().getKeyID());
        }
    }

    @Nested
    @DisplayName("keyring routing")
    class KeyringRouting {

        static final OctetKeyPair keyA = SecretSealing.generateRecipientKey("a");
        static final OctetKeyPair keyB = SecretSealing.generateRecipientKey("b");

        static Stream<Arguments> plaintextsAndKeys() {
            return Stream.of(arguments("secret-for-a", keyA), arguments("secret-for-b", keyB));
        }

        @MethodSource("plaintextsAndKeys")
        @ParameterizedTest(name = "{0}")
        @DisplayName("a keyring routes each token to the key its own key id names")
        void whenKeyringHoldsManyKeysThenEachTokenRoutesToItsOwnKey(String plaintext, OctetKeyPair key) {
            val ring   = Keyring.of(keyA, keyB);
            val sealed = SecretSealing.seal(key.toPublicJWK(), plaintext);
            assertThat(SecretSealing.unseal(ring, sealed)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("a token whose key id is not in the keyring is rejected")
        void whenKeyIdNotInKeyringThenThrows() {
            val ring   = Keyring.of(keyB);
            val sealed = SecretSealing.seal(keyA.toPublicJWK(), "secret");
            assertThatThrownBy(() -> SecretSealing.unseal(ring, sealed)).isInstanceOf(SecretSealingException.class)
                    .hasMessageContaining("'a'");
        }

        @Test
        @DisplayName("a token that names no key id is rejected")
        void whenTokenNamesNoKeyIdThenThrows() {
            val noKid  = new OctetKeyPair.Builder(keyA.toPublicJWK()).keyID(null).build();
            val ring   = Keyring.of(keyA);
            val sealed = SecretSealing.seal(noKid, "secret");
            assertThatThrownBy(() -> SecretSealing.unseal(ring, sealed)).isInstanceOf(SecretSealingException.class)
                    .hasMessageContaining("no key id");
        }

        @Test
        @DisplayName("an empty keyring rejects every token, failing closed")
        void whenKeyringEmptyThenThrows() {
            val ring   = Keyring.of();
            val sealed = SecretSealing.seal(keyA.toPublicJWK(), "secret");
            assertThatThrownBy(() -> SecretSealing.unseal(ring, sealed)).isInstanceOf(SecretSealingException.class);
        }
    }

    @Nested
    @DisplayName("reseal")
    class Reseal {

        private final OctetKeyPair keyA = SecretSealing.generateRecipientKey("a");
        private final OctetKeyPair keyB = SecretSealing.generateRecipientKey("b");

        @Test
        @DisplayName("a resealed token unseals under the target")
        void whenResealedThenUnsealsUnderTarget() {
            val sealed   = SecretSealing.seal(keyA.toPublicJWK(), "s3cr3t");
            val resealed = SecretSealing.reseal(Keyring.of(keyA), keyB.toPublicJWK(), sealed);
            assertThat(SecretSealing.unseal(keyB, resealed)).isEqualTo("s3cr3t");
        }

        @Test
        @DisplayName("a resealed token no longer unseals under the source")
        void whenResealedThenNoLongerUnsealsUnderSource() {
            val sealed   = SecretSealing.seal(keyA.toPublicJWK(), "s3cr3t");
            val resealed = SecretSealing.reseal(Keyring.of(keyA), keyB.toPublicJWK(), sealed);
            assertThatThrownBy(() -> SecretSealing.unseal(keyA, resealed)).isInstanceOf(SecretSealingException.class);
        }
    }
}
