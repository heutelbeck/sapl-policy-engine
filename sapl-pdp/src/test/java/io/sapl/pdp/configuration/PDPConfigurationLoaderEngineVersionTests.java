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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;

import lombok.val;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("PDP configuration sources preserve and validate optional engine requirements")
class PDPConfigurationLoaderEngineVersionTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path directory;

    private enum LoadingPath {
        DIRECTORY,
        RESOURCE_CONTENT,
        BUNDLE
    }

    @MethodSource("acceptedRequirements")
    @DisplayName("Every loading path preserves a declared requirement or its absence")
    @ParameterizedTest(name = "{0} preserves requirement {1}, with null representing a missing field")
    void whenConfigurationIsLoadedThenEngineRequirementIsPreserved(LoadingPath path, String requirement)
            throws IOException {
        val configuration = load(path, jsonWithRequirement(requirement));

        assertThat(configuration.requiredEngineVersion()).isEqualTo(requirement);
    }

    @EnumSource(LoadingPath.class)
    @ParameterizedTest(name = "{0} accepts an explicit null requirement")
    @DisplayName("An explicit null requirement is undeclared in every loading path")
    void whenRequirementIsExplicitlyNullThenLoadingLeavesItUndeclared(LoadingPath path) throws IOException {
        val configuration = load(path, jsonWithLiteral("null"));

        assertThat(configuration.requiredEngineVersion()).isNull();
    }

    @MethodSource("invalidRanges")
    @ParameterizedTest(name = "{0} rejects malformed requirement {1}")
    @DisplayName("Every loading path reports a configuration error for a malformed version range")
    void whenRequirementIsMalformedThenLoadingRejectsConfiguration(LoadingPath path, String requirement) {
        val json = jsonWithRequirement(requirement);

        assertThatThrownBy(() -> load(path, json)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("requiredEngineVersion");
    }

    @MethodSource("invalidJsonTypes")
    @ParameterizedTest(name = "{0} rejects requirement JSON value {1}")
    @DisplayName("Every loading path requires an engine requirement to be a string or null")
    void whenRequirementHasWrongJsonTypeThenLoadingRejectsConfiguration(LoadingPath path, String literal) {
        val json = jsonWithLiteral(literal);

        assertThatThrownBy(() -> load(path, json)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("requiredEngineVersion");
    }

    @Test
    @DisplayName("A directory without pdp.json has no engine version requirement")
    void whenPdpJsonIsMissingThenDirectoryLoadingLeavesRequirementUndeclared() {
        val configuration = PDPConfigurationLoader.loadFromDirectory(directory, "pdp");

        assertThat(configuration.requiredEngineVersion()).isNull();
    }

    @NullSource
    @ValueSource(strings = { ">=4.2.0", "^4.2.0", ">=4.2.0-0" })
    @ParameterizedTest(name = "Bundle parsing preserves requirement {0}")
    @DisplayName("A real bundle preserves the requirement through loading and provenance attachment")
    void whenBundleIsParsedThenEngineRequirementIsPreserved(String requirement) {
        val bundle = BundleBuilder.create().withPdpJson(jsonWithRequirement(requirement))
                .withConfigurationId("manifest-config").build();
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        val configuration = BundleParser.parse(bundle, "pdp", policy);

        assertThat(configuration).returns(requirement, PDPConfiguration::requiredEngineVersion)
                .returns("manifest-config", PDPConfiguration::configurationId);
    }

    static Stream<Arguments> acceptedRequirements() {
        return cases(null, ">=4.2.0", "^4.2.0", ">=4.2.0-0");
    }

    static Stream<Arguments> invalidRanges() {
        return cases("", "5.0.01", ">=4.2.0 garbage", ">=4.2.0 ||");
    }

    static Stream<Arguments> invalidJsonTypes() {
        return cases("42", "true", "[]", "{}");
    }

    private static Stream<Arguments> cases(String... values) {
        return Arrays.stream(LoadingPath.values())
                .flatMap(path -> Arrays.stream(values).map(value -> arguments(path, value)));
    }

    private static String jsonWithRequirement(String requirement) {
        val document = MAPPER.createObjectNode();
        if (requirement != null) {
            document.put("requiredEngineVersion", requirement);
        }
        return document.toString();
    }

    private static String jsonWithLiteral(String literal) {
        return """
                { "requiredEngineVersion": %s }""".formatted(literal);
    }

    private PDPConfiguration load(LoadingPath path, String json) throws IOException {
        return switch (path) {
        case DIRECTORY        -> {
            Files.writeString(directory.resolve("pdp.json"), json);
            yield PDPConfigurationLoader.loadFromDirectory(directory, "pdp");
        }
        case RESOURCE_CONTENT -> PDPConfigurationLoader.loadFromContent(json, Map.of(), "pdp", "resources");
        case BUNDLE           -> PDPConfigurationLoader.loadFromBundle(json, null, Map.of(), "pdp", "manifest-config");
        };
    }
}
