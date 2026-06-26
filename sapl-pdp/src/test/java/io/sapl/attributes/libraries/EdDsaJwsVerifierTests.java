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
package io.sapl.attributes.libraries;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("EdDsaJwsVerifier")
class EdDsaJwsVerifierTests {

    private static final byte[] SIGNING_INPUT = "eyJhbGciOiJFZERTQSJ9.cGF5bG9hZA".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "Ed25519", "Ed448" })
    @DisplayName("a valid EdDSA signature verifies, for both Ed25519 and Ed448")
    void whenValidSignatureThenVerifies(String curve) throws Exception {
        val keyPair   = KeyPairGenerator.getInstance(curve).generateKeyPair();
        val header    = new JWSHeader(JWSAlgorithm.EdDSA);
        val signature = sign(curve, keyPair.getPrivate(), SIGNING_INPUT);

        val verifier = new EdDsaJwsVerifier(keyPair.getPublic());

        assertThat(verifier.verify(header, SIGNING_INPUT, signature)).isTrue();
    }

    @Test
    @DisplayName("a signature over different input is rejected")
    void whenInputTamperedThenRejected() throws Exception {
        val keyPair   = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        val header    = new JWSHeader(JWSAlgorithm.EdDSA);
        val signature = sign("Ed25519", keyPair.getPrivate(), SIGNING_INPUT);
        val tampered  = "eyJhbGciOiJFZERTQSJ9.dGFtcGVyZWQ".getBytes(StandardCharsets.US_ASCII);

        val verifier = new EdDsaJwsVerifier(keyPair.getPublic());

        assertThat(verifier.verify(header, tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("a non-EdDSA header is rejected as unsupported")
    void whenHeaderIsNotEdDsaThenThrows() throws Exception {
        val keyPair   = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        val header    = new JWSHeader(JWSAlgorithm.RS256);
        val signature = Base64URL.encode(new byte[] { 1, 2, 3 });
        val verifier  = new EdDsaJwsVerifier(keyPair.getPublic());

        assertThatExceptionOfType(JOSEException.class)
                .isThrownBy(() -> verifier.verify(header, SIGNING_INPUT, signature)).withMessageContaining("EdDSA");
    }

    private static Base64URL sign(String curve, PrivateKey privateKey, byte[] input) throws Exception {
        val signer = Signature.getInstance(curve);
        signer.initSign(privateKey);
        signer.update(input);
        return Base64URL.encode(signer.sign());
    }
}
