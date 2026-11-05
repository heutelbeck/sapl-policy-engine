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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;

import lombok.val;

@DisplayName("pdp.json declares required extensions for every configuration source")
class PDPConfigurationRequiredExtensionsTests {

    @TempDir
    Path directory;

    @Test
    @DisplayName("A directory reads required extension names from pdp.json")
    void whenDirectoryDeclaresRequiredExtensionsThenTheirNamesAreRetained() throws IOException {
        Files.writeString(directory.resolve("pdp.json"), """
                {"requiredExtensions":["upstreams"]}
                """);
        Files.writeString(directory.resolve("ext-upstreams.json"), "{}");

        assertThat(PDPConfigurationLoader.loadFromDirectory(directory, "arkham").requiredExtensions())
                .containsExactly("upstreams");
    }

    @Test
    @DisplayName("Bundle loading preserves required extension names from pdp.json")
    void whenBundleDeclaresRequiredExtensionsThenTheirNamesAreRetained() {
        val bundle = BundleBuilder.create().withPdpJson("""
                {"requiredExtensions":["upstreams"]}
                """).withExtension("upstreams", "{}").build();
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThat(BundleParser.parse(bundle, "arkham", policy).requiredExtensions()).containsExactly("upstreams");
    }

    @Test
    @DisplayName("Required extension declarations without payload are rejected before packaging")
    void whenBundleHasRequiredExtensionWithoutPayloadThenBuildingFails() {
        val builder = BundleBuilder.create().withPdpJson("""
                {"requiredExtensions":["upstreams"]}
                """);
        assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("upstreams");
    }

    @Test
    @DisplayName("A resource source cannot accept a required extension without its payload")
    void whenResourceRequiresExtensionWithoutPayloadThenLoadingFails() {
        val json = """
                {"requiredExtensions":["upstreams"]}
                """;
        val load = (ThrowingCallable) () -> PDPConfigurationLoader.loadFromContent(json, Map.of(), "arkham",
                "policies");
        assertThatThrownBy(load).isInstanceOf(PDPConfigurationException.class).hasMessageContaining("upstreams");
    }

    @ParameterizedTest(name = "Invalid requiredExtensions value {0}")
    @DisplayName("Malformed required extension declarations are rejected")
    @ValueSource(strings = { "null", "true", "\"upstreams\"", "{}", "[1]", "[\"upstreams\", null]" })
    void whenRequiredExtensionsHaveInvalidShapeThenLoadingFails(String declaration) {
        val json = "{\"requiredExtensions\": " + declaration + "}";
        val load = (ThrowingCallable) () -> PDPConfigurationLoader.loadFromContent(json, Map.of(), "arkham",
                "policies");
        assertThatThrownBy(load).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("requiredExtensions");
    }

    @Test
    @DisplayName("Unknown pdp.json fields are rejected so misspelled requirements cannot disappear")
    void whenPdpJsonContainsUnknownFieldThenLoadingFails() {
        val json = """
                {"unknownRequirement": []}
                """;
        val load = (ThrowingCallable) () -> PDPConfigurationLoader.loadFromContent(json, Map.of(), "arkham",
                "policies");
        assertThatThrownBy(load).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("unknownRequirement");
    }
}
