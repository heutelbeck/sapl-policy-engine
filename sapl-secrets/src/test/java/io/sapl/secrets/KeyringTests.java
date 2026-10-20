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

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;

import lombok.val;

@DisplayName("Keyring (key-id routing)")
class KeyringTests {

    private final OctetKeyPair keyA = SecretSealing.generateRecipientKey("a");

    @Test
    @DisplayName("a key without a key id is rejected, failing closed")
    void whenKeyHasNoKeyIdThenThrows() {
        val nullKidKey = new OctetKeyPair.Builder(keyA).keyID(null).build();
        assertThatThrownBy(() -> Keyring.of(nullKidKey)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("no key id");
    }

    @Test
    @DisplayName("two keys sharing a key id are rejected, failing closed")
    void whenTwoKeysShareKeyIdThenThrows() {
        val keyA1 = SecretSealing.generateRecipientKey("a");
        val keyA2 = SecretSealing.generateRecipientKey("a");
        assertThatThrownBy(() -> Keyring.of(keyA1, keyA2)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("'a'");
    }

    @Test
    @DisplayName("the same key material under different key ids is rejected")
    void whenKeyMaterialIsDuplicatedThenThrows() {
        val duplicate = new OctetKeyPair.Builder(keyA).keyID("b").build();

        assertThatThrownBy(() -> Keyring.of(keyA, duplicate)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    @DisplayName("a public-only recipient key is rejected")
    void whenKeyIsPublicThenThrows() {
        val publicKey = keyA.toPublicJWK();

        assertThatThrownBy(() -> Keyring.of(publicKey)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("private key material");
    }

    @Test
    @DisplayName("a private key on another curve is rejected")
    void whenKeyUsesAnotherCurveThenThrows() throws Exception {
        val ed25519 = new OctetKeyPairGenerator(Curve.Ed25519).keyID("signing").generate();

        assertThatThrownBy(() -> Keyring.of(ed25519)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("X25519");
    }

    @Test
    @DisplayName("a key indexed under a key id other than its own is rejected, failing closed")
    void whenKeyIndexedUnderForeignKeyIdThenThrows() {
        val inconsistent = Map.of("wrong-id", keyA);
        assertThatThrownBy(() -> keyring(inconsistent)).isInstanceOf(SecretSealingException.class)
                .hasMessageContaining("wrong-id").hasMessageContaining("'a'");
    }

    @Test
    @DisplayName("a present key id resolves and an absent one yields empty")
    void whenKeyIdResolvedThenPresentOrEmpty() {
        val ring = Keyring.of(keyA);
        assertThat(ring.privateKeyFor("a")).isPresent();
        assertThat(ring.privateKeyFor("b")).isEmpty();
        assertThat(ring.toString()).contains("a").doesNotContain(keyA.toJSONString());
    }

    private static Keyring keyring(Map<String, OctetKeyPair> keys) {
        return new Keyring(keys);
    }
}
