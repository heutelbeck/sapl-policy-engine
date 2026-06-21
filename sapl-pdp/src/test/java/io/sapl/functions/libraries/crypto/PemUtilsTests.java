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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;

@DisplayName("PemUtils private key file permissions")
@DisabledOnOs(OS.WINDOWS)
class PemUtilsTests {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    @DisplayName("a private key is written owner read/write only, never world-readable")
    void whenWritingPrivateKeyThenOwnerOnly(@TempDir Path dir) throws Exception {
        val file = dir.resolve("private.pem");

        PemUtils.writeKeyToFile(file, keyPair.getPrivate());

        assertThat(Files.getPosixFilePermissions(file)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
    }

    @Test
    @DisplayName("overwriting an existing world-readable file restricts it to the owner")
    void whenOverwritingWorldReadableFileThenOwnerOnly(@TempDir Path dir) throws Exception {
        val file = dir.resolve("private.pem");
        Files.writeString(file, "stale");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        PemUtils.writeKeyToFile(file, keyPair.getPrivate());

        assertThat(Files.getPosixFilePermissions(file)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
    }

    @Test
    @DisplayName("oversized PEM input is rejected before decoding")
    void whenPemInputExceedsMaxLengthThenThrows() {
        val oversized = "x".repeat(256 * 1024 + 1);

        assertThatThrownBy(() -> PemUtils.decodePem(oversized)).isInstanceOf(CryptoException.class)
                .hasMessageContaining("exceeds the maximum length");
    }
}
