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
package io.sapl.lsp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

class ParsedDocumentGuardsTests {

    @Nested
    @DisplayName("Trojan-source bidirectional control character detection")
    class TrojanSourceDetection {

        static Stream<Arguments> bidiControlCharacters() {
            return Stream.of(arguments("LRE", '\u202A'), arguments("RLE", '\u202B'), arguments("PDF", '\u202C'),
                    arguments("LRO", '\u202D'), arguments("RLO", '\u202E'), arguments("LRI", '\u2066'),
                    arguments("RLI", '\u2067'), arguments("FSI", '\u2068'), arguments("PDI", '\u2069'));
        }

        @ParameterizedTest(name = "{0} is flagged as a trojan-source diagnostic")
        @MethodSource("bidiControlCharacters")
        @DisplayName("every Trojan-Source bidi control character produces a diagnostic")
        void whenBidiControlCharacterPresentThenDiagnosticEmitted(String name, char control) {
            val content     = "permit" + control + "where true";
            val diagnostics = ParsedDocumentGuards.preParseDiagnostics(content);
            assertThat(diagnostics).singleElement()
                    .satisfies(error -> assertThat(error.message()).containsIgnoringCase("trojan"));
        }

        @Test
        @DisplayName("clean policy text produces no diagnostics")
        void whenContentIsCleanThenNoDiagnostics() {
            assertThat(ParsedDocumentGuards.preParseDiagnostics("permit where true;")).isEmpty();
        }
    }
}
