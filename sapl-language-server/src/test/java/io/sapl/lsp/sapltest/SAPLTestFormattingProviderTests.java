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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.test.grammar.antlr.SAPLTestLexer;

@DisplayName("SAPLTest formatting provider")
class SAPLTestFormattingProviderTests {

    private final SAPLTestFormattingProvider provider = new SAPLTestFormattingProvider();

    @Nested
    @DisplayName("parse error gating")
    class ParseErrorGating {

        @Test
        @DisplayName("returns empty list when document has parse errors")
        void whenDocumentHasParseErrors_thenReturnsEmptyList() {
            var document = new SAPLTestParsedDocument("test.sapltest", "requirement {");

            var edits = provider.provideFormatting(document);

            assertThat(edits).isEmpty();
        }

        @Test
        @DisplayName("returns edits when document is valid")
        void whenDocumentIsValid_thenReturnsEdits() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "test" {
                        scenario "s1"
                            when "user" attempts "read" on "doc"
                            expect permit;
                    }
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1);
        }

    }

    @Nested
    @DisplayName("requirement formatting")
    class RequirementFormatting {

        @Test
        @DisplayName("formats requirement with proper structure")
        void whenSimpleRequirement_thenFormatsCorrectly() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "test" {
                      scenario "s1"
                        when "user" attempts "read" on "doc"
                        expect permit;
                    }
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo("""
                    requirement "test" {

                        scenario "s1"
                            when "user" attempts "read" on "doc"
                            expect permit;

                    }
                    """));
        }

    }

    @Nested
    @DisplayName("streaming scenario formatting")
    class StreamingScenarioFormatting {

        @Test
        @DisplayName("formats then/expect pairs correctly")
        void whenStreamingScenario_thenFormatsThenExpectPairs() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "streaming" {
                        scenario "alternating"
                            given
                                - document "policy1"
                                - attribute "nowMock" <time.now> emits "t1"
                            when "user" attempts "read" on "data"
                            expect permit
                            then
                                - attribute "nowMock" emits "t2"
                            expect deny;
                    }
                    """);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> {
                var text = edit.getNewText();
                assertThat(text).contains("        expect permit\n");
                assertThat(text).contains("        then\n");
                assertThat(text).contains("            - attribute \"nowMock\" emits \"t2\"\n");
                assertThat(text).contains("        expect deny;\n");
            });
        }

    }

    @Nested
    @DisplayName("script preservation")
    class ScriptPreservation {

        @Test
        @DisplayName("matcher keywords remain separate and values retain their spelling")
        void whenFormattingMatchersThenScriptRemainsValid() {
            var source = """
                    requirement "matchers" {
                        given
                            - function demo.echo(matching text containing "when - { value } // text") maps to "ok"
                        scenario "values"
                            when "when - { value } // text" attempts "read" on "report"
                            expect decision is permit, with resource equals {"a":"x", "b":[1,-2,3.50]};
                    }
                    """;

            assertThat(formatStable(source)).contains("matching text containing", "is permit", "with resource equals",
                    "\"when - { value } // text\"", "3.50");
        }

        @ParameterizedTest(name = "verification: {0}")
        @ValueSource(strings = { "called once", "called 2 times", "never called" })
        @DisplayName("function and attribute call counts retain their grammar")
        void whenFormattingVerificationThenScriptRemainsValid(String amount) {
            for (var reference : List.of("function demo.echo(any)", "attribute <demo.counter>")) {
                var source = """
                        requirement "verification" {
                            scenario "count"
                                when "Alice" attempts "read" on "report"
                                expect permit
                                verify
                                    - %s is %s;
                        }
                        """.formatted(reference, amount);

                assertThat(formatStable(source)).contains(reference + " is " + amount);
            }
        }

        @ParameterizedTest(name = "low-latency-mode {0}")
        @ValueSource(booleans = { true, false })
        @DisplayName("the scheduling option remains a single given item")
        void whenFormattingSchedulingOptionThenScriptRemainsValid(boolean enabled) {
            var source = """
                    requirement "scheduling" {
                        given
                            - low-latency-mode %s
                        scenario "normal"
                            when "Alice" attempts "read" on "report"
                            expect permit;
                    }
                    """.formatted(enabled);

            assertThat(formatStable(source)).contains("- low-latency-mode " + enabled);
        }

        @Test
        @DisplayName("comments inside values and after the last requirement are retained")
        void whenFormattingCommentsThenAuthorTextIsRetained() {
            var source    = """
                    /* heading */
                    requirement "comments" {
                        given
                            - documents "first", /* selection */ "second"
                        scenario "values"
                            when {"name": /* field */ "Alice", "tags": ["north" /* last item */]}
                                attempts "read" on "report"
                            expect permit; // scenario note
                    } // final note
                    """;
            var before    = new SAPLTestParsedDocument("test.sapltest", source);
            var formatted = formatStable(source);
            var after     = new SAPLTestParsedDocument("test.sapltest", formatted);

            assertThat(comments(after)).containsExactlyElementsOf(comments(before));
        }
    }

    private String formatStable(String source) {
        var original = new SAPLTestParsedDocument("test.sapltest", source);
        assertThat(original.getParseErrors()).isEmpty();
        var first = provider.provideFormatting(original);
        assertThat(first).hasSize(1);
        var formatted = first.getFirst().getNewText();
        var reparsed  = new SAPLTestParsedDocument("test.sapltest", formatted);
        assertThat(reparsed.getParseErrors()).isEmpty();
        assertThat(provider.provideFormatting(reparsed)).hasSize(1).first()
                .satisfies(edit -> assertThat(edit.getNewText()).isEqualTo(formatted));
        return formatted;
    }

    private List<String> comments(SAPLTestParsedDocument document) {
        return document.getTokens().stream().filter(
                token -> token.getType() == SAPLTestLexer.ML_COMMENT || token.getType() == SAPLTestLexer.SL_COMMENT)
                .map(token -> token.getText().strip()).toList();
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("formatting formatted sapltest is idempotent")
        void whenFormattingAlreadyFormatted_thenUnchanged() throws IOException {
            var formatted = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/formatted-requirement.sapltest"))
                    .replace("\r\n", "\n");
            var document  = new SAPLTestParsedDocument("test.sapltest", formatted);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo(formatted));
        }

        @Test
        @DisplayName("formatting unformatted produces expected output")
        void whenFormattingUnformatted_thenMatchesExpected() throws IOException {
            var unformatted = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/unformatted-requirement.sapltest"))
                    .replace("\r\n", "\n");
            var expected    = Files
                    .readString(Path.of("src/test/resources/testfiles/formatting/formatted-requirement.sapltest"))
                    .replace("\r\n", "\n");
            var document    = new SAPLTestParsedDocument("test.sapltest", unformatted);

            var edits = provider.provideFormatting(document);

            assertThat(edits).hasSize(1).first().satisfies(edit -> assertThat(edit.getNewText()).isEqualTo(expected));
        }

    }

}
