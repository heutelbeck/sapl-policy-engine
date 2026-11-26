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
package io.sapl.test.plain;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Loaded fixture compiler options")
class FixtureCompilerOptionsTests {

    private static final String POLICY = """
            policy "reader" permit subject == "visitor";
            """;

    @TempDir
    Path base;

    static Stream<Arguments> loadedOptions() {
        return Stream.of(arguments("configuration", 1, TestStatus.ERROR),
                arguments("configuration", 8, TestStatus.PASSED), arguments("pdp-configuration", 1, TestStatus.ERROR),
                arguments("pdp-configuration", 8, TestStatus.PASSED));
    }

    @ParameterizedTest(name = "{0}, maxExpressionDepth={1} yields {2}")
    @MethodSource("loadedOptions")
    @DisplayName("loaded compiler options govern the ordinary policy compilation")
    void whenConfigurationLoadsACompilerOptionThenTheFixtureUsesIt(String directive, int depth, TestStatus expected)
            throws IOException {
        val directory = Files.createDirectory(base.resolve("config"));
        Files.writeString(directory.resolve("reader.sapl"), POLICY);
        Files.writeString(directory.resolve("pdp.json"), """
                {"compilerOptions":{"maxExpressionDepth":${depth}}}
                """.replace("${depth}", Integer.toString(depth)));
        val resource = "configuration".equals(directive) ? "config" : "config/pdp.json";
        val test     = """
                requirement "configured compiler" {
                    given - ${directive} "${resource}"
                    scenario "ordinary expression"
                        when "visitor" attempts "read" on "document"
                        expect permit;
                }
                """.replace("${directive}", directive).replace("${resource}", resource);
        val config   = TestConfiguration.builder().withBasePath(base)
                .withSaplDocument(SaplDocument.of("reader", POLICY))
                .withSaplTestDocument(SaplTestDocument.of("configured", test)).build();

        val result = new PlainTestAdapter().execute(config);

        assertThat(result.scenarioResults()).singleElement()
                .satisfies(scenario -> assertThat(scenario.status()).isEqualTo(expected));
    }
}
