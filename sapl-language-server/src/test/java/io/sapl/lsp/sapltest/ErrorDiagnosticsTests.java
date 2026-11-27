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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.lsp.sapl.ProtocolText;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument.ParseError;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument.ValidationError;
import lombok.val;

@DisplayName("Diagnostics of the errors of ANTLR")
class ErrorDiagnosticsTests {

    private static final String DOCUMENT = """
            requirement "r" {
                scenario "s"
                    then expect permit;
            }
            """;

    static Stream<Arguments> errorsAndTheTextTheyMark() {
        return Stream.of(
                arguments("a parse error without offending text marks one character",
                        List.of(new ParseError(2, 4, "unexpected token", "")), List.of(), "s"),
                arguments("a validation error without offending text marks one character", List.of(),
                        List.of(new ValidationError(3, 8, "invalid reference", "")), "t"),
                arguments("an error with offending text marks exactly that text",
                        List.of(new ParseError(1, 12, "unexpected token", "\"r\"")), List.of(), "\"r\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorsAndTheTextTheyMark")
    @DisplayName("an error marks its offending text, and at least one character, so that the editor shows it")
    void whenErrorIsPublishedThenItMarksItsOffendingTextAndAtLeastOneCharacter(String description,
            List<ParseError> parseErrors, List<ValidationError> validationErrors, String marked) {
        val diagnostics = ErrorDiagnostics.diagnosticsOf(DOCUMENT, parseErrors, validationErrors, "sapltest");

        assertThat(diagnostics).singleElement()
                .extracting(diagnostic -> ProtocolText.textOf(DOCUMENT, diagnostic.getRange())).isEqualTo(marked);
    }

}
