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
package io.sapl.functions.libraries.crypto;

import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P256_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P384_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P521_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP256R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP384R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP521R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("KeyUtils key size and EC curve identification")
class KeyUtilsTests {

    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static ECPublicKey generateKey(String standardName, String provider) throws Exception {
        val generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(new ECGenParameterSpec(standardName));
        return (ECPublicKey) generator.generateKeyPair().getPublic();
    }

    @Test
    @DisplayName("an Ed25519 key reports its true 256-bit strength")
    void whenEd25519KeyThenKeySizeIs256() throws Exception {
        val publicKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();

        assertThat(KeyUtils.getKeySize(publicKey)).isEqualTo(256);
    }

    @Test
    @DisplayName("an Ed448 key reports its true 448-bit strength, not the Ed25519 size")
    void whenEd448KeyThenKeySizeIs448() throws Exception {
        val publicKey = KeyPairGenerator.getInstance("Ed448").generateKeyPair().getPublic();

        assertThat(KeyUtils.getKeySize(publicKey)).isEqualTo(448);
    }

    @Test
    @DisplayName("a secp256k1 key is not mistaken for the NIST P-256 curve")
    void whenKeyIsSecp256k1ThenJwkCurveIsUnknown() throws Exception {
        val key = generateKey("secp256k1", BouncyCastleProvider.PROVIDER_NAME);

        assertThat(KeyUtils.getJwkCurveName(key)).isEqualTo(CURVE_UNKNOWN);
    }

    @Test
    @DisplayName("a secp256k1 key is not labelled with a NIST curve name")
    void whenKeyIsSecp256k1ThenEcCurveNameIsUnknown() throws Exception {
        val key = generateKey("secp256k1", BouncyCastleProvider.PROVIDER_NAME);

        assertThat(KeyUtils.extractEcCurveName(key)).isEqualTo(CURVE_UNKNOWN);
    }

    @Test
    @DisplayName("the NIST P-256 curve is identified by JWK name and standard name")
    void whenKeyIsP256ThenIdentifiedAsP256() throws Exception {
        val key = generateKey("secp256r1", "SunEC");

        assertThat(KeyUtils.getJwkCurveName(key)).isEqualTo(CURVE_P256_JWK);
        assertThat(KeyUtils.extractEcCurveName(key)).isEqualTo(CURVE_SECP256R1);
    }

    @Test
    @DisplayName("the NIST P-384 curve is identified by JWK name and standard name")
    void whenKeyIsP384ThenIdentifiedAsP384() throws Exception {
        val key = generateKey("secp384r1", "SunEC");

        assertThat(KeyUtils.getJwkCurveName(key)).isEqualTo(CURVE_P384_JWK);
        assertThat(KeyUtils.extractEcCurveName(key)).isEqualTo(CURVE_SECP384R1);
    }

    @Test
    @DisplayName("the NIST P-521 curve is identified by JWK name and standard name")
    void whenKeyIsP521ThenIdentifiedAsP521() throws Exception {
        val key = generateKey("secp521r1", "SunEC");

        assertThat(KeyUtils.getJwkCurveName(key)).isEqualTo(CURVE_P521_JWK);
        assertThat(KeyUtils.extractEcCurveName(key)).isEqualTo(CURVE_SECP521R1);
    }
}
