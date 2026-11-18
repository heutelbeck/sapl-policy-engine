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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

class SAPLDiagnosticsProviderTests {

    private static final String POLICY = """
            policy "p"
            permit
                subject.role == "admin";
            """;

    private final SAPLDiagnosticsProvider provider = new SAPLDiagnosticsProvider();

    @Test
    @DisplayName("when a parse error reports empty offending text then the diagnostic spans at least one character so the editor highlights it")
    void whenParseErrorHasEmptyOffendingTextThenDiagnosticIsVisible() {
        var document = new StubParsedDocument(POLICY,
                List.of(new ParsedDocument.ParseError(2, 4, "unexpected token", "")), List.of());

        var diagnostics = provider.provideDiagnostics(document);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            var start = diagnostic.getRange().getStart();
            var end   = diagnostic.getRange().getEnd();
            assertThat(start.getLine()).isEqualTo(1);
            assertThat(start.getCharacter()).isEqualTo(4);
            assertThat(end.getCharacter()).isGreaterThan(start.getCharacter());
        });
    }

    @Test
    @DisplayName("when a validation error reports empty offending text then the diagnostic spans at least one character so the editor highlights it")
    void whenValidationErrorHasEmptyOffendingTextThenDiagnosticIsVisible() {
        var document = new StubParsedDocument(POLICY, List.of(),
                List.of(new ParsedDocument.ValidationError(3, 0, "invalid reference", "")));

        var diagnostics = provider.provideDiagnostics(document);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            var start = diagnostic.getRange().getStart();
            var end   = diagnostic.getRange().getEnd();
            assertThat(start.getLine()).isEqualTo(2);
            assertThat(end.getCharacter()).isGreaterThan(start.getCharacter());
        });
    }

    @Test
    @DisplayName("when an error reports a multi-character offending symbol then the diagnostic spans exactly that symbol's width")
    void whenOffendingSymbolHasWidthThenDiagnosticSpansThatWidth() {
        var document = new StubParsedDocument("policy permitt",
                List.of(new ParsedDocument.ParseError(1, 7, "unexpected token", "permitt")), List.of());

        var diagnostics = provider.provideDiagnostics(document);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            var range = diagnostic.getRange();
            assertThat(range.getStart().getCharacter()).isEqualTo(7);
            assertThat(range.getEnd().getCharacter()).isEqualTo(14);
        });
    }

    @Nested
    @DisplayName("the range of a diagnostic")
    class RangeOfADiagnosticTests {

        static Stream<Arguments> comparisonsWrittenWithTheSignOfADefinition() {
            val plainLine    = "    action = \"read\";";
            val afterTheFace = "    \"" + ProtocolText.GRINNING_FACE + "\" = subject.mood;";
            return ProtocolText.documentsWithEveryLineEndAndFaces(plainLine, afterTheFace);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("comparisonsWrittenWithTheSignOfADefinition")
        @DisplayName("marks the text that is wrong in the editor")
        void whenAComparisonIsWrittenWithTheSignOfADefinitionThenTheEditorMarksThatSign(String description,
                String content) {
            val document = new SAPLParsedDocument("test.sapl", content);

            val diagnostics = provider.provideDiagnostics(document);

            assertThat(diagnostics).first()
                    .extracting(diagnostic -> ProtocolText.textOf(content, diagnostic.getRange())).isEqualTo("=");
        }
    }

    private record StubParsedDocument(
            String content,
            List<ParsedDocument.ParseError> parseErrors,
            List<ParsedDocument.ValidationError> validationErrors) implements ParsedDocument {

        @Override
        public String getUri() {
            return "stub.sapl";
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public ParseTree getParseTree() {
            return null;
        }

        @Override
        public List<Token> getTokens() {
            return List.of();
        }

        @Override
        public boolean hasErrors() {
            return !parseErrors.isEmpty() || !validationErrors.isEmpty();
        }

        @Override
        public List<ParsedDocument.ParseError> getParseErrors() {
            return parseErrors;
        }

        @Override
        public List<ParsedDocument.ValidationError> getValidationErrors() {
            return validationErrors;
        }
    }

}
