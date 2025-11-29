/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleManifestTests {

    @Test
    void whenBuildingUnsignedManifest_thenContainsFileHashes() {
        val manifest = BundleManifest.builder()
                .addFile("necronomicon.sapl", "policy \"forbidden\" deny subject.sanity < 10")
                .addFile("pdp.json", "{ \"algorithm\": \"DENY_OVERRIDES\" }").buildUnsigned();

        assertThat(manifest.version()).isEqualTo(BundleManifest.MANIFEST_VERSION);
        assertThat(manifest.hashAlgorithm()).isEqualTo(BundleManifest.HASH_ALGORITHM);
        assertThat(manifest.files()).hasSize(2).containsKey("necronomicon.sapl").containsKey("pdp.json");
        assertThat(manifest.signature()).isNull();
    }

    @Test
    void whenBuildingSignedManifest_thenContainsSignature() {
        val manifest = BundleManifest.builder()
                .addFile("ritual.sapl", "policy \"ritual\" permit action.type == \"summon\"")
                .signature("elder-key", "base64signature==").build();

        assertThat(manifest.signature()).isNotNull();
        assertThat(manifest.signature().algorithm()).isEqualTo("Ed25519");
        assertThat(manifest.signature().keyId()).isEqualTo("elder-key");
        assertThat(manifest.signature().value()).isEqualTo("base64signature==");
    }

    @Test
    void whenSettingExpiration_thenManifestContainsExpiration() {
        val expirationTime = Instant.now().plus(7, ChronoUnit.DAYS);
        val manifest       = BundleManifest.builder().addFile("prophecy.sapl", "policy \"stars\" permit true")
                .expires(expirationTime).buildUnsigned();

        assertThat(manifest.expires()).isEqualTo(expirationTime);
    }

    @Test
    void whenSettingCreationTime_thenManifestContainsCreatedTime() {
        val createdTime = Instant.parse("2024-01-15T10:30:00Z");
        val manifest    = BundleManifest.builder().created(createdTime)
                .addFile("tome.sapl", "policy \"tome\" permit true").buildUnsigned();

        assertThat(manifest.created()).isEqualTo(createdTime);
    }

    @Test
    void whenAddingFileBytes_thenHashIsComputed() {
        val content  = "policy \"byte-test\" permit true".getBytes();
        val manifest = BundleManifest.builder().addFile("byte-policy.sapl", content).buildUnsigned();

        assertThat(manifest.files()).containsKey("byte-policy.sapl");
        assertThat(manifest.files().get("byte-policy.sapl")).startsWith("sha256:");
    }

    @Test
    void whenAddingPrecomputedHash_thenHashIsStored() {
        val precomputedHash = "sha256:abc123def456";
        val manifest        = BundleManifest.builder().addFileHash("precomputed.sapl", precomputedHash).buildUnsigned();

        assertThat(manifest.files().get("precomputed.sapl")).isEqualTo(precomputedHash);
    }

    @Test
    void whenSettingFilesMap_thenAllFilesAreStored() {
        val files    = Map.of("policy1.sapl", "sha256:hash1", "policy2.sapl", "sha256:hash2", "pdp.json",
                "sha256:hash3");
        val manifest = BundleManifest.builder().files(files).buildUnsigned();

        assertThat(manifest.files()).hasSize(3).containsAllEntriesOf(files);
    }

    @Test
    void whenRemovingSignature_thenSignatureIsNull() {
        val manifest = BundleManifest.builder().addFile("test.sapl", "policy \"test\" permit true")
                .signature("key-id", "signature-value").build();

        val unsigned = manifest.withoutSignature();

        assertThat(unsigned.signature()).isNull();
        assertThat(unsigned.files()).isEqualTo(manifest.files());
        assertThat(unsigned.version()).isEqualTo(manifest.version());
    }

    @Test
    void whenSerializingToJson_thenValidJsonIsProduced() {
        val manifest = BundleManifest.builder().addFile("cult.sapl", "policy \"cult\" permit true")
                .signature("dagon-key", "ZWxkZXJfc2lnbg==").build();

        val json = manifest.toJson();

        assertThat(json).contains("\"version\"").contains("\"hashAlgorithm\"").contains("\"files\"")
                .contains("\"signature\"").contains("cult.sapl");
    }

    @Test
    void whenParsingFromJson_thenManifestIsRestored() {
        val json = """
                {
                  "version": "1.0",
                  "hashAlgorithm": "SHA-256",
                  "created": "2024-01-15T10:30:00Z",
                  "files": {
                    "access.sapl": "sha256:dGVzdGhhc2g="
                  },
                  "signature": {
                    "algorithm": "Ed25519",
                    "keyId": "miskatonic-key",
                    "value": "c2lnbmF0dXJl"
                  }
                }
                """;

        val manifest = BundleManifest.fromJson(json);

        assertThat(manifest.version()).isEqualTo("1.0");
        assertThat(manifest.hashAlgorithm()).isEqualTo("SHA-256");
        assertThat(manifest.files()).containsKey("access.sapl");
        assertThat(manifest.signature().keyId()).isEqualTo("miskatonic-key");
    }

    @Test
    void whenParsingInvalidJson_thenThrowsException() {
        val invalidJson = "{ invalid json }";

        assertThatThrownBy(() -> BundleManifest.fromJson(invalidJson)).isInstanceOf(BundleSignatureException.class)
                .hasMessageContaining("Failed to parse manifest");
    }

    @Test
    void whenSerializingAndParsing_thenRoundtripPreservesContent() {
        val original = BundleManifest.builder().created(Instant.parse("2024-06-15T12:00:00Z"))
                .expires(Instant.parse("2025-06-15T12:00:00Z"))
                .addFile("arkham.sapl", "policy \"asylum\" permit subject.role == \"doctor\"")
                .addFile("pdp.json", "{ \"algorithm\": \"DENY_UNLESS_PERMIT\" }")
                .signature("arkham-key", "YXJraGFtX3NpZw==").build();

        val json     = original.toJson();
        val restored = BundleManifest.fromJson(json);

        assertThat(restored.version()).isEqualTo(original.version());
        assertThat(restored.hashAlgorithm()).isEqualTo(original.hashAlgorithm());
        assertThat(restored.created()).isEqualTo(original.created());
        assertThat(restored.expires()).isEqualTo(original.expires());
        assertThat(restored.files()).isEqualTo(original.files());
        assertThat(restored.signature().keyId()).isEqualTo(original.signature().keyId());
        assertThat(restored.signature().value()).isEqualTo(original.signature().value());
    }

    @Test
    void whenComputingHash_thenDeterministicResultIsProduced() {
        val content = "policy \"deterministic\" permit true";

        val hash1 = BundleManifest.computeHash(content);
        val hash2 = BundleManifest.computeHash(content);

        assertThat(hash1).isEqualTo(hash2).startsWith("sha256:");
    }

    @Test
    void whenComputingHashFromBytes_thenSameAsString() {
        val content    = "policy \"byte-equality\" permit true";
        val stringHash = BundleManifest.computeHash(content);
        val bytesHash  = BundleManifest.computeHash(content.getBytes());

        assertThat(stringHash).isEqualTo(bytesHash);
    }

    @ParameterizedTest
    @ValueSource(strings = { "policy \"elder\" permit true", "policy \"different\" deny false",
            "{ \"algorithm\": \"PERMIT_OVERRIDES\" }" })
    void whenComputingHashForDifferentContent_thenDifferentHashesAreProduced(String content) {
        val baseHash    = BundleManifest.computeHash("policy \"base\" permit true");
        val contentHash = BundleManifest.computeHash(content);

        assertThat(contentHash).isNotEqualTo(baseHash);
    }

    @Test
    void whenConvertingToCanonicalBytes_thenDeterministicOutputIsProduced() {
        val manifest = BundleManifest.builder().addFile("z-file.sapl", "policy \"z\" permit true")
                .addFile("a-file.sapl", "policy \"a\" permit true").buildUnsigned();

        val bytes1 = manifest.toCanonicalBytes();
        val bytes2 = manifest.toCanonicalBytes();

        assertThat(bytes1).isEqualTo(bytes2);
    }

    @Test
    void whenConvertingToCanonicalBytes_thenFilesAreSorted() {
        val manifest = BundleManifest.builder().addFile("zeta.sapl", "z").addFile("alpha.sapl", "a")
                .addFile("beta.sapl", "b").buildUnsigned();

        val json = new String(manifest.toCanonicalBytes());

        val alphaIndex = json.indexOf("alpha.sapl");
        val betaIndex  = json.indexOf("beta.sapl");
        val zetaIndex  = json.indexOf("zeta.sapl");

        assertThat(alphaIndex).isLessThan(betaIndex);
        assertThat(betaIndex).isLessThan(zetaIndex);
    }

    @Test
    void whenManifestFilenameConstant_thenIsCorrect() {
        assertThat(BundleManifest.MANIFEST_FILENAME).isEqualTo(".sapl-manifest.json");
    }

    @Test
    void whenAlgorithmConstants_thenAreCorrect() {
        assertThat(BundleManifest.HASH_ALGORITHM).isEqualTo("SHA-256");
        assertThat(BundleManifest.SIGNATURE_ALGORITHM).isEqualTo("Ed25519");
        assertThat(BundleManifest.MANIFEST_VERSION).isEqualTo("1.0");
    }

}
