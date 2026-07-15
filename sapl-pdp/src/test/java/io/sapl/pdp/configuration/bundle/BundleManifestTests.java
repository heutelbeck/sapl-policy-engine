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

import io.sapl.api.SaplVersion;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BundleManifest")
class BundleManifestTests {

    private static final Instant CREATED_TIME = Instant.parse("2024-01-15T10:30:00Z");

    private static BundleManifest.Builder validBuilder() {
        return BundleManifest.builder().created(CREATED_TIME).configurationId("test-config")
                .attribution(BundleManifest.attributionOfText("test-suite"))
                .addFile("pdp.json", "{ \"algorithm\": null }")
                .addFile("necronomicon.sapl", "policy \"forbidden\" deny subject.sanity < 10");
    }

    private static String validManifestJson() {
        return validBuilder().buildUnsigned().toJson();
    }

    @Nested
    @DisplayName("minting")
    class Minting {

        @Test
        @DisplayName("mints the engine library version and the creation time at build")
        void whenBuildingThenVersionIsMintedFromSaplVersionAndCreatedIsSet() {
            val manifest = validBuilder().buildUnsigned();

            assertThat(manifest.version()).isEqualTo(SaplVersion.VERSION);
            assertThat(manifest.created()).isEqualTo(CREATED_TIME);
            assertThat(manifest.signature()).isNull();
        }

        @Test
        @DisplayName("round-trips configurationId, attribution, and audience through JSON")
        void whenBuildingWithAllMetadataThenRoundTripPreservesIt() {
            val original = validBuilder().configurationId("release-77")
                    .attribution(BundleManifest.parseAttributionJson("{\"publisher\":\"arkham\",\"build\":42}"))
                    .audience("recipient-key-2024").buildUnsigned();

            val restored = BundleManifest.fromJson(original.toJson());

            assertThat(restored.configurationId()).isEqualTo("release-77");
            assertThat(restored.attribution().get("publisher").asString()).isEqualTo("arkham");
            assertThat(restored.audience().sealingRecipient()).isEqualTo("recipient-key-2024");
            assertThat(restored.version()).isEqualTo(SaplVersion.VERSION);
        }

        @Test
        @DisplayName("builds a signed manifest carrying the signature block")
        void whenBuildingSignedManifestThenContainsSignature() {
            val manifest = validBuilder().signature("elder-key", "base64signature==").build();

            assertThat(manifest.signature()).isNotNull();
            assertThat(manifest.signature().algorithm()).isEqualTo("Ed25519");
            assertThat(manifest.signature().keyId()).isEqualTo("elder-key");
            assertThat(manifest.signature().value()).isEqualTo("base64signature==");
        }

        @Test
        @DisplayName("withoutSignature keeps all metadata fields")
        void whenRemovingSignatureThenAllOtherFieldsAreKept() {
            val manifest = validBuilder().audience("recipient-key").signature("key-id", "signature-value").build();

            val unsigned = manifest.withoutSignature();

            assertThat(unsigned.signature()).isNull();
            assertThat(unsigned.files()).isEqualTo(manifest.files());
            assertThat(unsigned.version()).isEqualTo(manifest.version());
            assertThat(unsigned.configurationId()).isEqualTo(manifest.configurationId());
            assertThat(unsigned.attribution()).isEqualTo(manifest.attribution());
            assertThat(unsigned.audience()).isEqualTo(manifest.audience());
        }
    }

    @Nested
    @DisplayName("strict validation")
    class StrictValidation {

        @Test
        @DisplayName("rejects unknown top-level fields fail-closed")
        void whenParsingManifestWithUnknownFieldThenRejected() {
            val json = validManifestJson().replaceFirst("\\{", "{ \"maxAge\": 3600,");

            assertThatThrownBy(() -> BundleManifest.fromJson(json)).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("Failed to parse manifest");
        }

        @ParameterizedTest(name = "missing {0} is rejected with the migration message")
        @ValueSource(strings = { "created", "configurationId", "attribution" })
        void whenParsingManifestMissingRequiredFieldThenRejectedWithMigrationMessage(String field) {
            val json = validManifestJson().replaceFirst("\"" + field + "\"", "\"" + field + "Removed\"")
                    .replaceFirst("\"" + field + "Removed\"[^,}]*(,)?", "");

            assertThatThrownBy(() -> BundleManifest.fromJson(json)).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("rebuild the bundle with SAPL 4.2.0 or later");
        }

        @ParameterizedTest(name = "invalid configurationId \"{0}\" is rejected at build")
        @ValueSource(strings = { " ", "has space", "has/slash" })
        void whenBuildingWithInvalidConfigurationIdThenRejected(String configurationId) {
            val builder = validBuilder().configurationId(configurationId);

            assertThatThrownBy(builder::buildUnsigned).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("configurationId");
        }

        @Test
        @DisplayName("accepts string and object attribution")
        void whenAttributionIsStringOrObjectThenAccepted() {
            val withString = validBuilder().attribution(BundleManifest.attributionOfText("tag")).buildUnsigned();
            val withObject = validBuilder().attribution(BundleManifest.parseAttributionJson("{\"a\":1}"))
                    .buildUnsigned();

            assertThat(withString.attribution().isString()).isTrue();
            assertThat(withObject.attribution().isObject()).isTrue();
        }

        @ParameterizedTest(name = "attribution {0} is rejected")
        @ValueSource(strings = { "[1,2]", "42", "true" })
        void whenAttributionIsNotStringOrObjectThenRejected(String attributionJson) {
            val builder = validBuilder().attribution(BundleManifest.parseAttributionJson(attributionJson));

            assertThatThrownBy(builder::buildUnsigned).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("string or object");
        }

        @Test
        @DisplayName("rejects oversized attribution at build and at parse")
        void whenAttributionExceedsSizeCapThenRejectedAtBuildAndParse() {
            val hugeText = "x".repeat(BundleManifest.MAX_ATTRIBUTION_BYTES + 1);
            val builder  = validBuilder().attribution(BundleManifest.attributionOfText(hugeText));

            assertThatThrownBy(builder::buildUnsigned).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("maximum serialized size");

            val json = validManifestJson().replace("\"test-suite\"", "\"" + hugeText + "\"");
            assertThatThrownBy(() -> BundleManifest.fromJson(json)).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("maximum serialized size");
        }

        @Test
        @DisplayName("rejects a blank audience.sealingRecipient")
        void whenAudienceRecipientIsBlankThenRejected() {
            val builder = validBuilder().audience(" ");

            assertThatThrownBy(builder::buildUnsigned).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("sealingRecipient");
        }

        @ParameterizedTest(name = "version \"{0}\" parses when required fields are present")
        @ValueSource(strings = { "1.0", "utterly-garbage-version" })
        void whenParsingManifestWithForeignVersionThenAcceptedBecauseVersionIsProvenanceOnly(String version) {
            val json = validManifestJson().replace("\"" + SaplVersion.VERSION + "\"", "\"" + version + "\"");

            val manifest = BundleManifest.fromJson(json);

            assertThat(manifest.version()).isEqualTo(version);
        }

        @Test
        @DisplayName("rejects invalid JSON")
        void whenParsingInvalidJsonThenThrowsException() {
            val invalidJson = "{ invalid json }";

            assertThatThrownBy(() -> BundleManifest.fromJson(invalidJson)).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("Failed to parse manifest");
        }
    }

    @Nested
    @DisplayName("hashing and canonical form")
    class HashingAndCanonicalForm {

        @Test
        void whenComputingHashThenDeterministicResultIsProduced() {
            val content = "policy \"deterministic\" permit true";

            val hash1 = BundleManifest.computeHash(content);
            val hash2 = BundleManifest.computeHash(content);

            assertThat(hash1).isEqualTo(hash2).startsWith("sha256:")
                    .isEqualTo(BundleManifest.computeHash(content.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        void whenSettingFilesMapThenAllFilesAreStored() {
            val files    = Map.of("policy1.sapl", "sha256:hash1", "policy2.sapl", "sha256:hash2", "pdp.json",
                    "sha256:hash3");
            val manifest = validBuilder().files(files).buildUnsigned();

            assertThat(manifest.files()).hasSize(3).containsAllEntriesOf(files);
        }

        @Test
        @DisplayName("canonical bytes are stable across toJson, fromJson, and toCanonicalBytes")
        void whenRoundTrippingThroughJsonThenCanonicalBytesAreStable() {
            val original = validBuilder().audience("recipient-key").signature("arkham-key", "YXJraGFtX3NpZw==").build();

            val restored = BundleManifest.fromJson(original.toJson());

            assertThat(restored.toCanonicalBytes()).isEqualTo(original.toCanonicalBytes());
            assertThat(restored.withoutSignature().toCanonicalBytes())
                    .isEqualTo(original.withoutSignature().toCanonicalBytes());
        }

        @Test
        void whenConvertingToCanonicalBytesThenFilesAreSorted() {
            val manifest = validBuilder().addFile("zeta.sapl", "z").addFile("alpha.sapl", "a").addFile("beta.sapl", "b")
                    .buildUnsigned();

            val json = new String(manifest.toCanonicalBytes(), StandardCharsets.UTF_8);

            val alphaIndex = json.indexOf("alpha.sapl");
            val betaIndex  = json.indexOf("beta.sapl");
            val zetaIndex  = json.indexOf("zeta.sapl");

            assertThat(alphaIndex).isLessThan(betaIndex);
            assertThat(betaIndex).isLessThan(zetaIndex);
        }
    }
}
