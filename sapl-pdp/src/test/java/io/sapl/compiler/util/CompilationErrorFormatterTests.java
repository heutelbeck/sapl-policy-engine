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
package io.sapl.compiler.util;

import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.PlacedDocuments.GRINNING_FACE;
import static io.sapl.util.PlacedDocuments.documentOf;
import static io.sapl.util.SaplTesting.compilationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("CompilationErrorFormatter")
class CompilationErrorFormatterTests {

    @Nested
    @DisplayName("Plain text formatting")
    class PlainTextFormatting {

        @Test
        @DisplayName("when null exception then returns default message")
        void whenNullExceptionThenReturnsDefaultMessage() {
            assertThat(CompilationErrorFormatter.format(null)).isEqualTo("Unknown compilation error (null exception)");
        }

        @Test
        @DisplayName("when exception without location then shows message only")
        void whenExceptionWithoutLocationThenShowsMessageOnly() {
            var exception = new SaplCompilerException("Something went wrong");

            var expected = """
                    SAPL Compilation Error
                    Error: Something went wrong
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

        @Test
        @DisplayName("when exception with location then shows code snippet")
        void whenExceptionWithLocationThenShowsCodeSnippet() {
            var source = """
                    policy "test"
                    permit
                        subject == "admin"
                    """;

            var location  = new SourceLocation("test.sapl", source, 20, 30, 3, 5, 3, 15);
            var exception = new SaplCompilerException("Invalid expression", location);

            var expected = """
                    SAPL Compilation Error
                    Document: test.sapl
                    Error: Invalid expression
                    Location: line 3, column 5

                      1 | policy "test"
                      2 | permit
                    > 3 |     subject == "admin"
                              ^
                      4 |\s
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

        @Test
        @DisplayName("when error at first line then shows limited context")
        void whenErrorAtFirstLineThenShowsLimitedContext() {
            var source = """
                    polcy "typo in keyword"
                    permit
                        true
                    """;

            var location  = new SourceLocation("typo.sapl", source, 0, 5, 1, 1, 1, 5);
            var exception = new SaplCompilerException("Unexpected token 'polcy'", location);

            var expected = """
                    SAPL Compilation Error
                    Document: typo.sapl
                    Error: Unexpected token 'polcy'
                    Location: line 1, column 1

                    > 1 | polcy "typo in keyword"
                          ^
                      2 | permit
                      3 |     true
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

        @Test
        @DisplayName("when error at last line then shows limited context")
        void whenErrorAtLastLineThenShowsLimitedContext() {
            var source = """
                    policy "test"
                    permit
                        true
                    oblgation
                    """;

            var location  = new SourceLocation("last-line.sapl", source, 40, 49, 4, 1, 4, 9);
            var exception = new SaplCompilerException("Unexpected token 'oblgation'", location);

            var expected = """
                    SAPL Compilation Error
                    Document: last-line.sapl
                    Error: Unexpected token 'oblgation'
                    Location: line 4, column 1

                      2 | permit
                      3 |     true
                    > 4 | oblgation
                          ^
                      5 |\s
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

        @Test
        @DisplayName("when multiline policy error then shows context around error line")
        void whenMultilinePolicyErrorThenShowsContextAroundErrorLine() {
            var source = """
                    /*
                     * A complex policy with multiple conditions
                     */
                    policy "admin access"
                    permit
                        resource == "confidential";
                        subject.role == "admin";
                        action in ["read", "write"];
                        var x = undefined_function();
                    obligation
                        { "type": "log", "message": "Access granted" }
                    """;

            var location  = new SourceLocation("admin-policy.sapl", source, 150, 170, 9, 13, 9, 31);
            var exception = new SaplCompilerException("Unknown function: undefined_function", location);

            var expected = """
                    SAPL Compilation Error
                    Document: admin-policy.sapl
                    Error: Unknown function: undefined_function
                    Location: line 9, column 13

                       7 |     subject.role == "admin";
                       8 |     action in ["read", "write"];
                    >  9 |     var x = undefined_function();
                                       ^
                      10 | obligation
                      11 |     { "type": "log", "message": "Access granted" }
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

        @Test
        @DisplayName("when no document source then shows message without snippet")
        void whenNoDocumentSourceThenShowsMessageWithoutSnippet() {
            var location  = new SourceLocation("unknown.sapl", null, 0, 0, 5, 10, 5, 15);
            var exception = new SaplCompilerException("Some error", location);

            var expected = """
                    SAPL Compilation Error
                    Document: unknown.sapl
                    Error: Some error
                    Location: line 5, column 10
                    """;

            assertThat(CompilationErrorFormatter.format(exception)).isEqualTo(expected);
        }

    }

    @Nested
    @DisplayName("HTML formatting")
    class HtmlFormatting {

        @Test
        @DisplayName("when null exception then returns default HTML message")
        void whenNullExceptionThenReturnsDefaultHtmlMessage() {
            assertThat(CompilationErrorFormatter.formatHtml(null))
                    .isEqualTo("<div class=\"error\">Unknown compilation error (null exception)</div>");
        }

        @Test
        @DisplayName("when exception with location then produces valid HTML")
        void whenExceptionWithLocationThenProducesValidHtml() {
            var source = """
                    policy "test"
                    permit
                        invalid syntax here
                    """;

            var location  = new SourceLocation("test.sapl", source, 25, 35, 3, 5, 3, 20);
            var exception = new SaplCompilerException("Parse error", location);
            var result    = CompilationErrorFormatter.formatHtml(exception);

            assertThat(result).startsWith("<div class=\"sapl-compilation-error\">").endsWith("</div>")
                    .contains("<code>test.sapl</code>", "Parse error", "<span class=\"line-number\">");
        }

        @Test
        @DisplayName("when message contains special characters then escapes them")
        void whenMessageContainsSpecialCharactersThenEscapesThem() {
            var source = """
                    policy "test"
                    permit
                        subject < "admin" & resource > "file"
                    """;

            var location  = new SourceLocation("escape-test.sapl", source, 25, 50, 3, 5, 3, 40);
            var exception = new SaplCompilerException("Error with <special> & \"chars\"", location);
            var result    = CompilationErrorFormatter.formatHtml(exception);

            assertThat(result).contains("&lt;special&gt;", "&amp;", "&quot;chars&quot;").doesNotContain("<special>");
        }

    }

    @Nested
    @DisplayName("A report for an error of the compiler with a place in the document")
    class ReportsOfPlacedErrorsTests {

        private static final String PLAIN_TEXT_REPORT = """
                SAPL Compilation Error
                Document: p
                Error: Policy attempted to redefine variable 'mood'.
                Location: line 3, column 19

                  1 | policy "p"
                  2 | permit
                > 3 |     var mood = 1; var mood = 2;
                                        ^
                """;

        private static final String PLAIN_TEXT_REPORT_AFTER_A_FACE = """
                SAPL Compilation Error
                Document: p
                Error: Policy attempted to redefine variable 'mood'.
                Location: line 3, column 21

                  1 | policy "p"
                  2 | permit
                > 3 |     var mood = "FACE"; var mood = "sad";
                                          ^
                """.replace("FACE", GRINNING_FACE);

        private static final String HTML_REPORT = """
                <div class="sapl-compilation-error">
                  <div class="error-header">SAPL Compilation Error</div>
                  <div class="error-document">Document: <code>p</code></div>
                  <div class="error-message">Policy attempted to redefine variable 'mood'.</div>
                  <div class="error-location">Line 3, column 19</div>
                  <pre class="error-snippet"><span class="line-number">1</span> policy &quot;p&quot;
                <span class="line-number">2</span> permit
                <span class="error-line"><span class="line-number">3</span>     var mood = 1; <span class="error-char">v</span>ar mood = 2;</span>
                </pre>
                </div>""";

        private static final String TWO_DEFINITIONS              = "    var mood = 1; var mood = 2;";
        private static final String TWO_DEFINITIONS_AFTER_A_FACE = "    var mood = \"" + GRINNING_FACE
                + "\"; var mood = \"sad\";";

        @ParameterizedTest(name = "lines that end with {0}")
        @MethodSource("io.sapl.util.PlacedDocuments#lineEnds")
        @DisplayName("the plain text report shows the lines of the document and marks the place, whatever ends the lines")
        void whenLinesEndWithAnyLineBreakThenPlainTextReportShowsTheLinesAndMarksThePlace(String lineBreak,
                String lineEnd) {
            val redefinition = errorOfCompiling(documentOf(lineEnd, TWO_DEFINITIONS));

            assertThat(CompilationErrorFormatter.format(redefinition)).isEqualTo(PLAIN_TEXT_REPORT);
        }

        @ParameterizedTest(name = "lines that end with {0}")
        @MethodSource("io.sapl.util.PlacedDocuments#lineEnds")
        @DisplayName("the HTML report shows the lines of the document and marks the place, whatever ends the lines")
        void whenLinesEndWithAnyLineBreakThenHtmlReportShowsTheLinesAndMarksThePlace(String lineBreak, String lineEnd) {
            val redefinition = errorOfCompiling(documentOf(lineEnd, TWO_DEFINITIONS));

            assertThat(CompilationErrorFormatter.formatHtml(redefinition)).isEqualTo(HTML_REPORT);
        }

        @Test
        @DisplayName("the plain text report points at the place after a character outside the basic plane")
        void whenACharacterOutsideTheBasicPlaneStandsBeforeThePlaceThenPlainTextReportPointsAtThePlace() {
            val redefinition = errorOfCompiling(documentOf("\n", TWO_DEFINITIONS_AFTER_A_FACE));

            assertThat(CompilationErrorFormatter.format(redefinition)).isEqualTo(PLAIN_TEXT_REPORT_AFTER_A_FACE);
        }

        @Test
        @DisplayName("the HTML report marks the first character of the place after a character outside the basic plane")
        void whenACharacterOutsideTheBasicPlaneStandsBeforeThePlaceThenHtmlReportMarksTheFirstCharacterOfThePlace() {
            val redefinition = errorOfCompiling(documentOf("\n", TWO_DEFINITIONS_AFTER_A_FACE));

            assertThat(CompilationErrorFormatter.formatHtml(redefinition)).contains("var mood = &quot;" + GRINNING_FACE
                    + "&quot;; <span class=\"error-char\">v</span>ar mood = &quot;sad&quot;;");
        }

        /**
         * The second definition of a variable is refused by the compiler, and
         * the error names the place of that definition.
         */
        private static SaplCompilerException errorOfCompiling(String document) {
            return catchThrowableOfType(SaplCompilerException.class,
                    () -> DocumentCompiler.compileDocument(document, compilationContext()));
        }
    }

}
