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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("KeyUtils key size reporting")
class KeyUtilsTests {

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
}
