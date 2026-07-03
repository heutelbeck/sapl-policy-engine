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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.X25519Encrypter;
import com.nimbusds.jose.jwk.OctetKeyPair;

@DisplayName("Secret sealing (JWE primitive)")
class SecretSealingTests {

    private final OctetKeyPair recipient = SecretSealing.generateRecipientKey();

    @Test
    @DisplayName("a sealed secret unseals to the original plaintext")
    void whenSealedThenUnsealRestoresPlaintext() {
        var sealed = SecretSealing.seal(recipient.toPublicJWK(), "s3cr3t-token");
        assertThat(SecretSealing.unseal(recipient, sealed)).isEqualTo("s3cr3t-token");
    }

    @Test
    @DisplayName("empty plaintext round-trips")
    void whenEmptyPlaintextThenRoundTrips() {
        var sealed = SecretSealing.seal(recipient.toPublicJWK(), "");
        assertThat(SecretSealing.unseal(recipient, sealed)).isEmpty();
    }

    @Test
    @DisplayName("sealing the same secret twice yields different tokens (fresh ephemeral key)")
    void whenSealedTwiceThenTokensDiffer() {
        var publicKey = recipient.toPublicJWK();
        assertThat(SecretSealing.seal(publicKey, "same")).isNotEqualTo(SecretSealing.seal(publicKey, "same"));
    }

    @Test
    @DisplayName("generated recipient keys are distinct")
    void whenKeysGeneratedThenTheyDiffer() {
        assertThat(SecretSealing.generateRecipientKey().toJSONString())
                .isNotEqualTo(SecretSealing.generateRecipientKey().toJSONString());
    }

    @Test
    @DisplayName("unsealing with the wrong recipient key is rejected")
    void whenUnsealedWithWrongKeyThenThrows() {
        var wrongKey = SecretSealing.generateRecipientKey();
        var sealed   = SecretSealing.seal(recipient.toPublicJWK(), "secret");
        assertThatThrownBy(() -> SecretSealing.unseal(wrongKey, sealed)).isInstanceOf(SecretSealingException.class);
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void whenTokenTamperedThenThrows() {
        var sealed   = SecretSealing.seal(recipient.toPublicJWK(), "secret");
        var tampered = flipMiddleCharacter(sealed);
        assertThatThrownBy(() -> SecretSealing.unseal(recipient, tampered)).isInstanceOf(SecretSealingException.class);
    }

    @Test
    @DisplayName("a string that is not a JWE token is rejected")
    void whenNotAJweTokenThenThrows() {
        assertThatThrownBy(() -> SecretSealing.unseal(recipient, "not-a-token"))
                .isInstanceOf(SecretSealingException.class);
    }

    @Test
    @DisplayName("a token using a different encryption method is refused (algorithm pinning)")
    void whenTokenUsesWeakerEncryptionThenRefused() throws JOSEException {
        var header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM).build();
        var jwe    = new JWEObject(header, new Payload("secret"));
        jwe.encrypt(new X25519Encrypter(recipient.toPublicJWK()));
        var token = jwe.serialize();
        assertThatThrownBy(() -> SecretSealing.unseal(recipient, token)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("Refusing to unseal");
    }

    private static String flipMiddleCharacter(String token) {
        var index       = token.length() / 2;
        var replacement = token.charAt(index) == 'A' ? 'B' : 'A';
        return token.substring(0, index) + replacement + token.substring(index + 1);
    }
}
