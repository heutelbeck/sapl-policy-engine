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
package io.sapl.pdp.configuration;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExtensionFiles")
class ExtensionFilesTests {

    @Nested
    @DisplayName("file recognition")
    class Recognition {

        @ParameterizedTest(name = "\"{0}\" -> config={1}, secrets={2}, sealedSecrets={3}")
        @CsvSource({ "ext-upstreams.json, true, false, false", "ext-upstreams-secrets.json, false, true, false",
                "ext-upstreams-secrets.sealed.json, false, false, true", "pdp.json, false, false, false",
                "policy.sapl, false, false, false", "critical-extensions.json, false, false, false",
                "secrets.json, false, false, false", "secrets.sealed.json, false, false, false",
                "upstreams.json, false, false, false", "ext-.json, true, false, false" })
        @DisplayName("classifies root-level file names")
        void whenNameGivenThenClassifiedCorrectly(String name, boolean isConfig, boolean isSecrets,
                boolean isSealedSecrets) {
            assertThat(ExtensionFiles.isExtensionFile(name)).isEqualTo(isConfig);
            assertThat(ExtensionFiles.isExtensionSecretsFile(name)).isEqualTo(isSecrets);
            assertThat(ExtensionFiles.isSealedExtensionSecretsFile(name)).isEqualTo(isSealedSecrets);
        }

        @Test
        @DisplayName("strips prefix and suffix to recover the extension name")
        void whenExtensionFileThenNameRecovered() {
            assertThat(ExtensionFiles.extensionNameOf("ext-upstreams.json")).isEqualTo("upstreams");
            assertThat(ExtensionFiles.extensionSecretsNameOf("ext-upstreams-secrets.json")).isEqualTo("upstreams");
            assertThat(ExtensionFiles.sealedExtensionSecretsNameOf("ext-upstreams-secrets.sealed.json"))
                    .isEqualTo("upstreams");
        }
    }

    @Nested
    @DisplayName("critical set parsing")
    class Parsing {

        @Test
        @DisplayName("a JSON array of names parses to a set")
        void whenJsonArrayThenParsed() {
            assertThat(ExtensionFiles.parseCriticalExtensions("""
                    ["upstreams", "branding"]""")).containsExactlyInAnyOrder("upstreams", "branding");
        }

        @NullAndEmptySource
        @ValueSource(strings = { "   " })
        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("absent or blank content parses to an empty set")
        void whenAbsentThenEmpty(String json) {
            assertThat(ExtensionFiles.parseCriticalExtensions(json)).isEmpty();
        }

        @ValueSource(strings = { "\"upstreams\"", "{ \"upstreams\": true }", "[1, 2]", "[\"ok\", 3]", "not json" })
        @ParameterizedTest(name = "{0}")
        @DisplayName("content that is not a JSON array of strings is rejected")
        void whenNotArrayOfStringsThenThrows(String json) {
            val throwable = assertThatThrownBy(() -> ExtensionFiles.parseCriticalExtensions(json));
            throwable.isInstanceOf(PDPConfigurationException.class).hasMessageContaining("JSON array");
        }

        @Test
        @DisplayName("serialization sorts names and round-trips through parsing")
        void whenSerializedThenSortedAndRoundTrips() {
            val json = ExtensionFiles.toJson(Set.of("upstreams", "branding", "audit"));
            assertThat(json.indexOf("audit")).isLessThan(json.indexOf("branding"));
            assertThat(json.indexOf("branding")).isLessThan(json.indexOf("upstreams"));
            assertThat(ExtensionFiles.parseCriticalExtensions(json)).containsExactlyInAnyOrder("upstreams", "branding",
                    "audit");
        }
    }

    @Nested
    @DisplayName("integrity validation")
    class Integrity {

        @Test
        @DisplayName("a critical extension with a configuration passes")
        void whenCriticalHasConfigThenPasses() {
            ExtensionFiles.validateIntegrity(Set.of("upstreams"), Set.of("upstreams", "branding"), Set.of());
        }

        @Test
        @DisplayName("a critical extension with only sealed secrets passes")
        void whenCriticalHasOnlySecretsThenPasses() {
            ExtensionFiles.validateIntegrity(Set.of("upstreams"), Set.of(), Set.of("upstreams"));
        }

        @Test
        @DisplayName("a critical extension with neither configuration nor secrets is rejected")
        void whenCriticalWithoutPayloadThenThrows() {
            val throwable = assertThatThrownBy(
                    () -> ExtensionFiles.validateIntegrity(Set.of("upstreams"), Set.of("branding"), Set.of("audit")));
            throwable.isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("Critical extension 'upstreams'");
        }
    }
}
