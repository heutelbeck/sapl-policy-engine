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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import io.sapl.grammar.antlr.SAPLLexer;

import lombok.val;

@DisplayName("SAPL formatting provider")
class SAPLFormattingProviderTests {

    private final SAPLFormattingProvider provider = new SAPLFormattingProvider();

    @Nested
    @DisplayName("parse error gating")
    class ParseErrorGating {

        @Test
        @DisplayName("returns empty list when document has parse errors")
        void whenDocumentHasParseErrors_thenReturnsEmptyList() {
            var document = new SAPLParsedDocument("test.sapl", "policy permit");

            var edits = provider.provideFormatting(document);

            assertThat(edits).isEmpty();
        }

        @Test
        @DisplayName("returns edits when document is valid")
        void whenDocumentIsValid_thenReturnsEdits() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1);
        }

    }

    @Nested
    @DisplayName("simple policy formatting")
    class SimplePolicyFormatting {

        @Test
        @DisplayName("formats simple policy with permit")
        void whenSimplePermitPolicy_thenFormatsCorrectly() {
            var document = new SAPLParsedDocument("test.sapl", """
                      policy   "test"
                      permit
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo("""
                    policy "test"
                    permit
                    """));
        }

        @Test
        @DisplayName("formats policy with body statements")
        void whenPolicyWithBody_thenFormatsCorrectly() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                      action == "read" ;
                      subject.role == "admin" ;
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo("""
                    policy "test"
                    permit
                        action == "read";
                        subject.role == "admin";
                    """));
        }

    }

    @Nested
    @DisplayName("policy set formatting")
    class PolicySetFormatting {

        @Test
        @DisplayName("formats policy set with combining algorithm")
        void whenPolicySet_thenFormatsCorrectly() {
            var document = new SAPLParsedDocument("test.sapl", """
                    set "test" first or abstain

                    policy "p1" permit

                    policy "p2" deny
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo("""
                    set "test"
                    first or abstain

                    policy "p1"
                    permit

                    policy "p2"
                    deny
                    """));
        }

    }

    @Nested
    @DisplayName("import sorting")
    class ImportSorting {

        @Test
        @DisplayName("sorts imports alphabetically")
        void whenImportsOutOfOrder_thenSortsAlphabetically() {
            var document = new SAPLParsedDocument("test.sapl", """
                    import filter.replace
                    import filter.blacken

                    policy "test" permit
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).startsWith("""
                    import filter.blacken
                    import filter.replace

                    """));
        }

    }

    @Nested
    @DisplayName("expression formatting")
    class ExpressionFormatting {

        @Test
        @DisplayName("formats binary operators with spaces")
        void whenBinaryOperators_thenFormatsWithSpaces() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        action=="read";
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first()
                    .satisfies(edit -> assertThat(edit.getNewText()).contains("action == \"read\""));
        }

        @Test
        @DisplayName("formats function calls with spaces after commas")
        void whenFunctionCallWithArgs_thenFormatsWithCommaSpaces() {
            var document = new SAPLParsedDocument("test.sapl", """
                    import filter.blacken

                    policy "test"
                    permit
                    transform
                        resource |- { @.field : blacken(2,0,"\\u2588") }
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first()
                    .satisfies(edit -> assertThat(edit.getNewText()).contains("blacken(2, 0, \"\\u2588\")"));
        }

    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("formatting formatted policy is idempotent")
        void whenFormattingAlreadyFormatted_thenUnchanged() throws IOException {
            var formatted = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/formatted-policy-set.sapl"))
                    .replace("\r\n", "\n");
            var document  = new SAPLParsedDocument("test.sapl", formatted);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo(formatted));
        }

        @Test
        @DisplayName("formatting unformatted produces expected output")
        void whenFormattingUnformatted_thenMatchesExpected() throws IOException {
            var unformatted = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/unformatted-policy-set.sapl"))
                    .replace("\r\n", "\n");
            var expected    = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/formatted-policy-set.sapl"))
                    .replace("\r\n", "\n");
            var document    = new SAPLParsedDocument("test.sapl", unformatted);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo(expected));
        }

    }

    @Nested
    @DisplayName("the range of the edit")
    class RangeOfTheEditTests {

        @ParameterizedTest(name = "lines that end with {0}")
        @MethodSource("io.sapl.lsp.sapl.ProtocolText#lineEnds")
        @DisplayName("replaces the whole document in the editor, whatever ends its lines")
        void whenDocumentIsFormattedThenTheEditReplacesTheWholeDocument(String lineBreak, String lineEnd) {
            val content  = ProtocolText.documentOf(lineEnd, "    action == \"read\";");
            val document = new SAPLParsedDocument("test.sapl", content);

            val edits = provider.provideFormatting(document);

            assertThat(edits).singleElement().extracting(edit -> ProtocolText.textOf(content, edit.getRange()))
                    .isEqualTo(content);
        }
    }

    @Nested
    @DisplayName("comment preservation")
    class CommentPreservation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.sapl.lsp.sapl.SAPLFormattingProviderTests#commentedPolicies")
        @DisplayName("keeps every comment through repeated formatting")
        void whenCommentsAreFormattedThenTheirContentsArePreserved(String name, String source) {
            val document = new SAPLParsedDocument("comments.sapl", source);
            val expected = commentTexts(document);

            assertThat(document.isSyntaxValid()).isTrue();
            assertThat(provider.provideFormatting(document)).singleElement().satisfies(edit -> {
                val formatted = new SAPLParsedDocument("comments.sapl", edit.getNewText());
                assertThat(formatted.isSyntaxValid()).isTrue();
                assertThat(commentTexts(formatted)).containsExactlyElementsOf(expected);
                assertThat(provider.provideFormatting(formatted)).singleElement().extracting(next -> next.getNewText())
                        .isEqualTo(edit.getNewText());
            });
        }
    }

    static Stream<Arguments> commentedPolicies() {
        return Stream.of(arguments("inline block", "policy \"inside\" permit subject[0] /* approved catalog */ == 0;"),
                arguments("trailing line", "policy \"read\" permit true;\n// Retain legacy requests\n"),
                arguments("inline line", "policy \"line\" permit subject == // requested action\n \"read\";"),
                arguments("interleaved imports", """
                        // Document header
                        import filter.replace
                        // Mask data
                        import filter.blacken
                        policy "imports" permit true;
                        """), arguments("member policies", """
                        set "members" first or abstain
                        policy "first" permit true;
                        // Other effect
                        policy "second" deny false;
                        /* Final note */
                        """),
                arguments("policy header", "policy /* named rule */ \"header\" /* effect */ permit true;"),
                arguments("set header",
                        "set /* named set */ \"header\" first /* order */ or abstain policy \"p\" permit"),
                arguments("adjacent blocks", "policy \"notes\" permit true /* first *//* second */;"),
                arguments("comment-like string", "policy \"text\" permit resource == \"/* data */ // text\";"));
    }

    private static List<String> commentTexts(SAPLParsedDocument document) {
        return document.getTokens().stream()
                .filter(token -> token.getType() == SAPLLexer.ML_COMMENT || token.getType() == SAPLLexer.SL_COMMENT)
                .map(token -> token.getText()).sorted().toList();
    }

}
