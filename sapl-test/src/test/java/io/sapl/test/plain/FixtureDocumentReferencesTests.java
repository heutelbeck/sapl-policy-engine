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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Explicit fixture document references")
class FixtureDocumentReferencesTests {

    static Stream<Arguments> namedDocuments() {
        return Stream.of("document", "documents").flatMap(directive -> Stream
                .of(arguments(directive, "shared", TestStatus.ERROR), arguments(directive, "only", TestStatus.PASSED)));
    }

    @ParameterizedTest(name = "{0} {1} yields {2}")
    @MethodSource("namedDocuments")
    @DisplayName("an explicitly selected name must identify one document")
    void whenAnExplicitDocumentNameIsAmbiguousThenBothPathsAreReported(String directive, String name,
            TestStatus expected) {
        val source = """
                requirement "lookup" {
                    given - ${directive} "${name}"
                    scenario "named file"
                        when null attempts null on null
                        expect permit;
                }
                """.replace("${directive}", directive).replace("${name}", name);
        val config = TestConfiguration.builder()
                .withSaplDocument(SaplDocument.of("shared", "policy \"east\" permit", "east/rule.sapl"))
                .withSaplDocument(SaplDocument.of("shared", "policy \"west\" permit", "west/rule.sapl"))
                .withSaplDocument(SaplDocument.of("only", "policy \"one\" permit", "one.sapl"))
                .withSaplTestDocument(SaplTestDocument.of("lookup", source)).build();

        val result = new PlainTestAdapter().execute(config);

        assertThat(result.scenarioResults()).singleElement().satisfies(scenario -> {
            assertThat(scenario.status()).isEqualTo(expected);
            if (expected == TestStatus.ERROR) {
                assertThat(scenario.failureMessage()).contains("east/rule.sapl", "west/rule.sapl");
            } else {
                assertThat(scenario.failureMessage()).isNull();
            }
        });
    }
}
