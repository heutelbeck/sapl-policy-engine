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
package io.sapl.pdp.configuration.bundle;

import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BundleSignerTests {

    private static PrivateKey cultPrivate;
    private static PublicKey  cultPublic;

    private static PrivateKey rsaPrivate;
    private static PublicKey  rsaPublic;

    @BeforeAll
    static void setupKeys() throws NoSuchAlgorithmException {
        val ed25519Generator = KeyPairGenerator.getInstance("Ed25519");
        val cultKeyPair      = ed25519Generator.generateKeyPair();
        cultPrivate = cultKeyPair.getPrivate();
        cultPublic  = cultKeyPair.getPublic();

        val rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        val rsaKeyPair = rsaGenerator.generateKeyPair();
        rsaPrivate = rsaKeyPair.getPrivate();
        rsaPublic  = rsaKeyPair.getPublic();
    }

    @Test
    void whenSigningWithValidKey_thenManifestContainsSignature() {
        val files = createTestFiles();

        val manifest = BundleSigner.sign(files, cultPrivate, "necronomicon-key");

        assertThat(manifest.signature()).isNotNull().satisfies(sig -> {
            assertThat(sig.algorithm()).isEqualTo("Ed25519");
            assertThat(sig.keyId()).isEqualTo("necronomicon-key");
            assertThat(sig.value()).isNotBlank();
        });
    }

    @Test
    void whenSigningWithExpiration_thenManifestContainsExpiration() {
        val files   = createTestFiles();
        val expires = Instant.now().plus(7, ChronoUnit.DAYS);

        val manifest = BundleSigner.sign(files, cultPrivate, "elder-sign", expires);

        assertThat(manifest.expires()).isEqualTo(expires);
    }

    @Test
    void whenSigningWithNullPrivateKey_thenThrowsException() {
        val files = createTestFiles();

        assertThatThrownBy(() -> BundleSigner.sign(files, null, "forbidden-key"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Private key must not be null");
    }

    @Test
    void whenSigningWithNonEd25519Key_thenThrowsException() {
        val files = createTestFiles();

        assertThatThrownBy(() -> BundleSigner.sign(files, rsaPrivate, "wrong-algorithm-key"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Ed25519");
    }

    @Test
    void whenVerifyingValidSignature_thenNoExceptionThrown() {
        val files    = createTestFiles();
        val manifest = BundleSigner.sign(files, cultPrivate, "miskatonic-key");

        BundleSigner.verify(manifest, files, cultPublic);
    }

    @Test
    void whenVerifyingWithWrongPublicKey_thenThrowsException() {
        val files          = createTestFiles();
        val manifest       = BundleSigner.sign(files, cultPrivate, "arkham-key");
        val wrongKeyPair   = generateEd25519KeyPair();
        val wrongPublicKey = wrongKeyPair.getPublic();
        assertThatThrownBy(() -> BundleSigner.verify(manifest, files, wrongPublicKey))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Signature verification failed");
    }

    @Test
    void whenVerifyingWithNullPublicKey_thenThrowsException() {
        val files    = createTestFiles();
        val manifest = BundleSigner.sign(files, cultPrivate, "innsmouth-key");

        assertThatThrownBy(() -> BundleSigner.verify(manifest, files, null))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Public key must not be null");
    }

    @Test
    void whenVerifyingWithRsaPublicKey_thenThrowsException() {
        val files    = createTestFiles();
        val manifest = BundleSigner.sign(files, cultPrivate, "dagon-key");

        assertThatThrownBy(() -> BundleSigner.verify(manifest, files, rsaPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Ed25519");
    }

    @Test
    void whenVerifyingWithTamperedFile_thenThrowsException() {
        val originalFiles = createTestFiles();
        val manifest      = BundleSigner.sign(originalFiles, cultPrivate, "cthulhu-key");

        val tamperedFiles = new TreeMap<>(originalFiles);
        tamperedFiles.put("ritual.sapl", "policy \"corrupted\" deny true");

        assertThatThrownBy(() -> BundleSigner.verify(manifest, tamperedFiles, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("integrity check failed");
    }

    @Test
    void whenVerifyingWithMissingFile_thenThrowsException() {
        val originalFiles = createTestFiles();
        val manifest      = BundleSigner.sign(originalFiles, cultPrivate, "yog-sothoth-key");

        val incompleteFiles = new TreeMap<>(originalFiles);
        incompleteFiles.remove("ritual.sapl");

        assertThatThrownBy(() -> BundleSigner.verify(manifest, incompleteFiles, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Missing file");
    }

    @Test
    void whenVerifyingWithExtraFile_thenThrowsException() {
        val originalFiles = createTestFiles();
        val manifest      = BundleSigner.sign(originalFiles, cultPrivate, "shub-niggurath-key");

        val expandedFiles = new TreeMap<>(originalFiles);
        expandedFiles.put("forbidden-rite.sapl", "policy \"forbidden\" deny true");

        assertThatThrownBy(() -> BundleSigner.verify(manifest, expandedFiles, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Unexpected file");
    }

    @Test
    void whenVerifyingExpiredSignature_thenThrowsException() {
        val files       = createTestFiles();
        val expiredTime = Instant.now().minus(1, ChronoUnit.DAYS);
        val manifest    = BundleSigner.sign(files, cultPrivate, "expired-tome-key", expiredTime);
        val now         = Instant.now();
        assertThatThrownBy(() -> BundleSigner.verify(manifest, files, cultPublic, now))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("expired");
    }

    @Test
    void whenVerifyingExpiredSignatureWithoutTimeCheck_thenNoExceptionThrown() {
        val files       = createTestFiles();
        val expiredTime = Instant.now().minus(1, ChronoUnit.DAYS);
        val manifest    = BundleSigner.sign(files, cultPrivate, "old-ritual-key", expiredTime);

        BundleSigner.verify(manifest, files, cultPublic, null);
    }

    @Test
    void whenVerifyingValidFutureExpiration_thenNoExceptionThrown() {
        val files      = createTestFiles();
        val futureTime = Instant.now().plus(30, ChronoUnit.DAYS);
        val manifest   = BundleSigner.sign(files, cultPrivate, "future-prophecy-key", futureTime);

        BundleSigner.verify(manifest, files, cultPublic, Instant.now());
    }

    @Test
    void whenVerifyingSignatureOnly_thenDoesNotCheckFileIntegrity() {
        val files    = createTestFiles();
        val manifest = BundleSigner.sign(files, cultPrivate, "quick-check-key");

        BundleSigner.verifySignatureOnly(manifest, cultPublic);
    }

    @Test
    void whenCheckingIfManifestIsSigned_thenReturnsCorrectly() {
        val files          = createTestFiles();
        val signedManifest = BundleSigner.sign(files, cultPrivate, "signed-key");

        assertThat(BundleSigner.isSigned(signedManifest)).isTrue();
        assertThat(BundleSigner.isSigned(null)).isFalse();
        assertThat(BundleSigner.isSigned(signedManifest.withoutSignature())).isFalse();
    }

    @Test
    void whenVerifyingNullManifest_thenThrowsException() {
        val variables = Map.<String, String>of();
        assertThatThrownBy(() -> BundleSigner.verify(null, variables, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("null");
    }

    @Test
    void whenVerifyingUnsignedManifest_thenThrowsException() {
        val files            = createTestFiles();
        val signedManifest   = BundleSigner.sign(files, cultPrivate, "test-key");
        val unsignedManifest = signedManifest.withoutSignature();
        assertThatThrownBy(() -> BundleSigner.verify(unsignedManifest, files, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
    }

    @Test
    void whenVerifyingWithEmptyFileList_thenThrowsException() {
        val signedManifest = BundleSigner.sign(createTestFiles(), cultPrivate, "test-key");
        val emptyManifest  = new BundleManifest(BundleManifest.MANIFEST_VERSION, BundleManifest.HASH_ALGORITHM,
                Instant.now(), null, Map.of(), signedManifest.signature());
        val variables      = Map.<String, String>of();
        assertThatThrownBy(() -> BundleSigner.verify(emptyManifest, variables, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("no file entries");
    }

    @Test
    void whenVerifyingWithInvalidBase64Signature_thenThrowsException() {
        val invalidSig  = new BundleManifest.Signature("Ed25519", "bad-key", "not-valid-base64!!!");
        val badManifest = new BundleManifest(BundleManifest.MANIFEST_VERSION, BundleManifest.HASH_ALGORITHM,
                Instant.now(), null, Map.of("test.sapl", BundleManifest.computeHash("test")), invalidSig);
        val variables   = Map.of("test.sapl", "test");
        assertThatThrownBy(() -> BundleSigner.verify(badManifest, variables, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Invalid signature encoding");
    }

    @Test
    void whenVerifyingWithWrongAlgorithm_thenThrowsException() {
        val wrongAlgSig = new BundleManifest.Signature("RSA", "wrong-alg-key", "abc123");
        val badManifest = new BundleManifest(BundleManifest.MANIFEST_VERSION, BundleManifest.HASH_ALGORITHM,
                Instant.now(), null, Map.of("test.sapl", BundleManifest.computeHash("test")), wrongAlgSig);
        val variables   = Map.of("test.sapl", "test");
        assertThatThrownBy(() -> BundleSigner.verify(badManifest, variables, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("Unsupported signature algorithm");
    }

    @ParameterizedTest
    @MethodSource("emptySignatureValueCases")
    void whenVerifyingWithEmptySignatureValue_thenThrowsException(String signatureValue) {
        val emptySig    = new BundleManifest.Signature("Ed25519", "empty-sig-key", signatureValue);
        val badManifest = new BundleManifest(BundleManifest.MANIFEST_VERSION, BundleManifest.HASH_ALGORITHM,
                Instant.now(), null, Map.of("test.sapl", BundleManifest.computeHash("test")), emptySig);
        val variables   = Map.of("test.sapl", "test");
        assertThatThrownBy(() -> BundleSigner.verify(badManifest, variables, cultPublic))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("signature value is empty");
    }

    static Stream<Arguments> emptySignatureValueCases() {
        return Stream.of(arguments((String) null), arguments(""), arguments("   "));
    }

    private Map<String, String> createTestFiles() {
        val files = new TreeMap<String, String>();
        files.put("pdp.json",
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                        """);
        files.put("ritual.sapl", """
                policy "elder-ritual"
                permit subject.cultRank == "elder"
                action.type == "summon"
                """);
        files.put("access-control.sapl", """
                policy "arkham-library"
                deny subject.sanity < 30
                """);
        return files;
    }

    private KeyPair generateEd25519KeyPair() {
        try {
            val generator = KeyPairGenerator.getInstance("Ed25519");
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available.", e);
        }
    }

}
