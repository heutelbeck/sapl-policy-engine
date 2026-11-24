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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.PdpRuntime;
import io.sapl.api.pdp.configuration.UnknownProvenance;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;

@DisplayName("A PDP configuration names the runtimes it expects, and an engine of another runtime rejects it")
class ExpectedPdpRuntimeTests {

    private static final String ERROR_TYPE = "pdp.json field 'expectedPdpRuntime' must be an array of the runtime names 'java' and 'rust'.";

    @TempDir
    Path directory;

    private enum LoadingPath {
        DIRECTORY,
        RESOURCE_CONTENT,
        BUNDLE
    }

    @MethodSource("acceptedFields")
    @ParameterizedTest(name = "{0} reads {1}")
    @DisplayName("Every loading path reads the expected runtimes, none if the field is absent, null or empty")
    void whenConfigurationIsLoadedThenExpectedRuntimesArePreserved(LoadingPath path, String json,
            Set<PdpRuntime> expected) throws IOException {
        assertThat(load(path, json).expectedPdpRuntime()).isEqualTo(expected);
    }

    @MethodSource("rejectedFields")
    @ParameterizedTest(name = "{0} rejects {1}")
    @DisplayName("Every loading path rejects anything but an array of the names of runtimes")
    void whenExpectedRuntimesAreMalformedThenLoadingRejectsConfiguration(LoadingPath path, String json) {
        assertThatThrownBy(() -> load(path, json)).isInstanceOf(PDPConfigurationException.class).hasMessage(ERROR_TYPE);
    }

    @Test
    @DisplayName("A real bundle preserves the expected runtimes through loading and provenance attachment")
    void whenBundleIsParsedThenExpectedRuntimesArePreserved() {
        val bundle = BundleBuilder.create().withPdpJson("""
                {"expectedPdpRuntime": ["rust", "java"]}""").withConfigurationId("manifest-config").build();
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        val configuration = BundleParser.parse(bundle, "pdp", policy);

        assertThat(configuration.expectedPdpRuntime()).containsExactlyInAnyOrder(PdpRuntime.JAVA, PdpRuntime.RUST);
    }

    @Test
    @DisplayName("The configuration id depends on the expected runtimes, but not on their order, and is unchanged without them")
    void whenExpectedRuntimesDifferThenConfigurationIdsDiffer() {
        val none      = ConfigurationIds.entriesOf(configuration(Set.of()));
        val java      = ConfigurationIds.entriesOf(configuration(Set.of(PdpRuntime.JAVA)));
        val both      = ConfigurationIds.entriesOf(configuration(Set.of(PdpRuntime.JAVA, PdpRuntime.RUST)));
        val bothAgain = ConfigurationIds.entriesOf(configuration(Set.of(PdpRuntime.RUST, PdpRuntime.JAVA)));

        assertThat(none).doesNotContainKey("expectedPdpRuntime");
        assertThat(List.of(ConfigurationIds.derive("pdp", none), ConfigurationIds.derive("pdp", java), ConfigurationIds.derive("pdp", both)))
                .doesNotHaveDuplicates();
        assertThat(ConfigurationIds.derive("pdp", bothAgain)).isEqualTo(ConfigurationIds.derive("pdp", both));
    }

    @MethodSource
    @ParameterizedTest(name = "an engine of the runtime {0} and a configuration that expects {1}")
    @DisplayName("An engine accepts a configuration that expects its runtime or none, and rejects any other with the reason")
    void whenRuntimeIsCheckedThenOnlyAnUnexpectedRuntimeIsAnError(PdpRuntime running, Set<PdpRuntime> expected,
            String error) {
        assertThat(PdpVoterSource.runtimeError(running, expected)).isEqualTo(error);
    }

    static Stream<Arguments> whenRuntimeIsCheckedThenOnlyAnUnexpectedRuntimeIsAnError() {
        return Stream.of(arguments(PdpRuntime.JAVA, Set.of(), null),
                arguments(PdpRuntime.JAVA, Set.of(PdpRuntime.JAVA), null),
                arguments(PdpRuntime.JAVA, Set.of(PdpRuntime.JAVA, PdpRuntime.RUST), null),
                arguments(PdpRuntime.JAVA, Set.of(PdpRuntime.RUST),
                        "The running sapl-pdp runtime 'java' is not among expectedPdpRuntime [rust]. Deploy this configuration on one of the expected runtimes."),
                arguments(PdpRuntime.RUST, Set.of(PdpRuntime.JAVA),
                        "The running sapl-pdp runtime 'rust' is not among expectedPdpRuntime [java]. Deploy this configuration on one of the expected runtimes."));
    }

    static Stream<Arguments> acceptedFields() {
        return Arrays.stream(LoadingPath.values())
                .flatMap(path -> Stream.of(arguments(path, "{}", Set.of()), arguments(path, """
                        {"expectedPdpRuntime": null}""", Set.of()), arguments(path, """
                        {"expectedPdpRuntime": []}""", Set.of()), arguments(path, """
                        {"expectedPdpRuntime": ["java"]}""", Set.of(PdpRuntime.JAVA)), arguments(path, """
                        {"expectedPdpRuntime": ["rust", "java", "rust"]}""",
                        Set.of(PdpRuntime.JAVA, PdpRuntime.RUST))));
    }

    static Stream<Arguments> rejectedFields() {
        return Arrays.stream(LoadingPath.values())
                .flatMap(path -> Stream.of("\"java\"", "42", "{}", "[1]", "[\"java\", null]", "[\"Java\"]", "[\"go\"]")
                        .map(literal -> arguments(path, "{\"expectedPdpRuntime\": " + literal + "}")));
    }

    private static PDPConfiguration configuration(Set<PdpRuntime> expected) {
        return new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT), Map.of(), Map.of(), Set.of(),
                UnknownProvenance.INSTANCE, null, expected);
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
