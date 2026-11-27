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
package io.sapl.compiler.document;

import com.github.valfirst.slf4jtest.LoggingEvent;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SyntaxAnalysis")
class SyntaxAnalysisTests {

    private static final String POLICY_WITH_CONDITION = """
            policy "p" permit %s;""";

    @Nested
    @DisplayName("the report of a document with many mistakes")
    class ReportTests {

        @Test
        @DisplayName("each statement gets its own problem, and the problems follow the order of the document")
        void whenSeveralStatementsHoldAMistakeThenEachIsReportedInTheOrderOfTheDocument() {
            val document = POLICY_WITH_CONDITION.formatted("a = 1; b == 2; c === 3; d <> 4");

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            assertThat(diagnostics).extracting(Diagnostic::problem).containsExactly(Problem.ASSIGNMENT_AS_COMPARISON,
                    Problem.UNKNOWN_OPERATOR, Problem.UNKNOWN_OPERATOR);
        }

        @Test
        @DisplayName("a statement with several mistakes gets one problem, the first")
        void whenStatementHoldsSeveralMistakesThenOnlyTheFirstIsReported() {
            val document = POLICY_WITH_CONDITION.formatted("a = 1 = 2 = 3");

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            assertThat(diagnostics).singleElement().extracting(Diagnostic::problem)
                    .isEqualTo(Problem.ASSIGNMENT_AS_COMPARISON);
        }

        @Test
        @DisplayName("after 100 problems the report ends with a notice at the first problem left out")
        void whenDocumentHasMoreProblemsThanAreReportedThenTheReportEndsWithANotice() {
            val document = POLICY_WITH_CONDITION.formatted("a = 1; ".repeat(150) + "a");

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            val firstLeftOut = "policy \"p\" permit ".length()
                    + SyntaxAnalysis.MAX_REPORTED_PROBLEMS * "a = 1; ".length() + "a ".length();
            assertThat(diagnostics).hasSize(SyntaxAnalysis.MAX_REPORTED_PROBLEMS + 1).last().satisfies(notice -> {
                assertThat(notice.problem()).isEqualTo(Problem.TOO_MANY_PROBLEMS);
                assertThat(notice.region().start()).isEqualTo(firstLeftOut);
            });
        }

        @Test
        @DisplayName("exactly 100 problems are reported without a notice")
        void whenDocumentHasAsManyProblemsAsAreReportedThenNoNoticeFollows() {
            val document = POLICY_WITH_CONDITION
                    .formatted("a = 1; ".repeat(SyntaxAnalysis.MAX_REPORTED_PROBLEMS - 1) + "a = 1");

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            assertThat(diagnostics).hasSize(SyntaxAnalysis.MAX_REPORTED_PROBLEMS).extracting(Diagnostic::problem)
                    .containsOnly(Problem.ASSIGNMENT_AS_COMPARISON);
        }
    }

    @Nested
    @DisplayName("the rest of a document after a mistake in its tokens")
    class AfterAMistakeInTokensTests {

        @Test
        @DisplayName("a string that is not closed is read as a string, so the parser checks the rest of the document")
        void whenStringIsNotClosedThenAStringStandsInForIt() {
            val analysis = SyntaxAnalysis.of(POLICY_WITH_CONDITION.formatted("action == \"read"));

            assertThat(analysis.tokenStream().getTokens()).filteredOn(token -> token.getText().startsWith("\"read"))
                    .singleElement().extracting(Token::getType).isEqualTo(SAPLLexer.STRING);
        }

        static Stream<Arguments> mistakesInTokensBeforeALaterMistake() {
            return Stream.of(arguments("a bracket that is not closed", "lib.f(1", Problem.MISSING_CLOSING_BRACKET),
                    arguments("a character that is not part of the language", "a == 1 \u20AC",
                            Problem.UNEXPECTED_CHARACTER));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("mistakesInTokensBeforeALaterMistake")
        @DisplayName("a mistake in the tokens is reported once, and a mistake in a later statement is still reported")
        void whenTokensHoldAMistakeThenALaterMistakeIsStillReported(String description, String statement,
                Problem problem) {
            val document = POLICY_WITH_CONDITION.formatted(statement + "; b = 2");

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            assertThat(diagnostics).extracting(Diagnostic::problem).containsExactly(problem,
                    Problem.ASSIGNMENT_AS_COMPARISON);
        }

        @Test
        @DisplayName("a comment that is not closed is shown as a comment up to the end of the document")
        void whenCommentIsNotClosedThenTheRestOfTheDocumentIsAComment() {
            val analysis = SyntaxAnalysis.of("policy \"p\" permit /* not closed");

            assertThat(analysis.tokenStream().getTokens()).filteredOn(token -> token.getType() == SAPLLexer.ML_COMMENT)
                    .singleElement().extracting(Token::getText).isEqualTo("/* not closed");
        }
    }

    @Nested
    @DisplayName("the validity of the syntax")
    class SyntaxValidityTests {

        @Test
        @DisplayName("a document that only breaks a rule has a valid syntax")
        void whenOnlyARuleIsBrokenThenTheSyntaxIsValid() {
            val analysis = SyntaxAnalysis.of(POLICY_WITH_CONDITION.formatted("foo()"));

            assertThat(analysis).returns(true, SyntaxAnalysis::syntaxIsValid)
                    .extracting(SyntaxAnalysis::diagnostics, list(Diagnostic.class)).extracting(Diagnostic::problem)
                    .containsExactly(Problem.NAME_NOT_IMPORTED);
        }

        static Stream<Arguments> mistakesOfTheSyntax() {
            return Stream.of(arguments("a refused token", "a = 1"), arguments("a mistake in a token", "\"\\d\""));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("mistakesOfTheSyntax")
        @DisplayName("a document with a mistake of its syntax has no valid syntax")
        void whenSyntaxHasAMistakeThenTheSyntaxIsNotValid(String description, String condition) {
            val analysis = SyntaxAnalysis.of(POLICY_WITH_CONDITION.formatted(condition));

            assertThat(analysis.syntaxIsValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("hostile documents of the size limit")
    class HostileDocumentTests {

        private static final int    LIMIT  = DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES;
        private static final String POLICY = "policy \"p\" permit ";
        private static final int    NOTICE = 1;

        private static String filled(String start, String repeated, String end) {
            val room = LIMIT - bytesOf(start) - bytesOf(end);
            return start + repeated.repeat(room / bytesOf(repeated)) + end;
        }

        private static int bytesOf(String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }

        static Stream<Arguments> hostileDocuments() {
            return Stream.of(arguments("characters that are not part of the language", filled("", "\u20AC", "")),
                    arguments("invisible characters", filled(POLICY, "\u00A0", "")),
                    arguments("one name with letters beyond ASCII", filled(POLICY, "\u00F6", ";")),
                    arguments("names with letters beyond ASCII after dots", filled(POLICY + "a", ".\u00F6", ";")),
                    arguments("strings that are not closed", filled(POLICY, "\"a\n", "")),
                    arguments("strings with escapes that do not exist", filled(POLICY, "\"\\d\" + ", "1;")),
                    arguments("texts in typographic quotation marks", filled(POLICY, "\u201Ca\u201D + ", "1;")),
                    arguments("typographic quotation marks without a closing one", filled(POLICY, "\u201C", "")),
                    arguments("comments that are not closed", filled(POLICY, "/*", "")),
                    arguments("one number of leading zeros", filled(POLICY, "0", ";")),
                    arguments("numbers with letters", filled(POLICY, "1a + ", "1;")),
                    arguments("indices after dots", filled(POLICY + "a", ".0", ";")),
                    arguments("opening brackets", filled(POLICY, "(", "")),
                    arguments("closing brackets", filled(POLICY, ")", "")),
                    arguments("brackets closed by brackets of another kind", filled(POLICY, "(]", "")),
                    arguments("brackets open at the end of each statement", filled(POLICY, "f(a;", "")),
                    arguments("statements with a mistake", filled(POLICY, "a = 1; ", "")),
                    arguments("statements without ';'", filled(POLICY, "a\n", "")),
                    arguments("statements without ';' after a long statement",
                            filled(POLICY + "x" + " + x".repeat(LIMIT / 10), "a\n", "")),
                    arguments("one long path that ends with a dot", filled(POLICY + "a", ".b", ".;")),
                    arguments("one long chain of comparisons", filled(POLICY + "a", " < b", ";")),
                    arguments("chained comparisons", filled(POLICY, "a < b < c; ", "")),
                    arguments("keywords as keys", filled(POLICY + "a", ".set", ";")),
                    arguments("subscripts without parentheses", filled(POLICY + "a", "[i + i]", ";")),
                    arguments("attribute finders that are not closed", filled(POLICY, "<a.b ", "")),
                    arguments("names that are not imported", filled(POLICY, "f() + ", "1;")),
                    arguments("indices out of range", filled(POLICY + "a", "[9999999999]", ";")),
                    arguments("variables named like the subscription", filled(POLICY, "var `subject` = 1; ", "")),
                    arguments("attribute finders in a schema", filled("subject schema ", "<a.b> + ", "1 " + POLICY)),
                    arguments("policies outside a policy set", filled("", POLICY, "")),
                    arguments("imports of the same name without a policy", filled("", "import lib.f\n", "")));
        }

        @Timeout(30)
        @ParameterizedTest(name = "{0}")
        @MethodSource("hostileDocuments")
        @DisplayName("a document of the size limit is analysed, with a report of at most 100 problems and the notice")
        void whenDocumentIsHostileThenTheAnalysisEndsAndTheReportIsBounded(String description, String document) {
            assertThat(bytesOf(document)).isLessThanOrEqualTo(LIMIT);

            val diagnostics = SyntaxAnalysis.of(document).diagnostics();

            assertThat(diagnostics).isNotEmpty()
                    .hasSizeLessThanOrEqualTo(SyntaxAnalysis.MAX_REPORTED_PROBLEMS + NOTICE);
        }
    }

    @Nested
    @DisplayName("a failure of reading that the checks did not foresee")
    class UnforeseenFailureTests {

        private static final String DOCUMENT = "policy \"p\" permit";

        private static final String LOG_UNFORESEEN_FAILURE = "Reading a SAPL document failed in a way that its checks did not foresee. The document is refused.";
        private static final String NOT_READ               = "Document not read.";
        private static final String NESTING_TOO_DEEP       = "Nesting too deep.";
        private static final String READING_FAILED         = "Reading the document failed with '%s'. The checks of SAPL did not foresee this. Report it together with the document.";
        private static final String NESTS_WITHOUT_BRACKETS = "This expression nests too deeply to be read, for example through a long chain of '!', '-', '+' or '::'.";

        @BeforeEach
        void forgetEarlierLogEvents() {
            getTestLogger(SyntaxAnalysis.class).clear();
        }

        static Stream<Arguments> unforeseenFailures() {
            val illegalState = "java.lang.IllegalStateException: A state that cannot happen.";
            return Stream.of(
                    arguments("an exception of the analysis", new IllegalStateException("A state that cannot happen."),
                            Problem.SYNTAX_ERROR, List.of(illegalState), NOT_READ,
                            READING_FAILED.formatted(illegalState)),
                    arguments("an exhausted stack", new StackOverflowError(), Problem.NESTING_TOO_DEEP_WITHOUT_BRACKETS,
                            List.of(), NESTING_TOO_DEEP, NESTS_WITHOUT_BRACKETS),
                    arguments("a refusal of the transformation that names no place",
                            new SaplCompilerException("Unknown effect."), Problem.SYNTAX_ERROR,
                            List.of("Unknown effect."), NOT_READ, READING_FAILED.formatted("Unknown effect.")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unforeseenFailures")
        @DisplayName("a step of reading that fails refuses the document with a problem at its start and logs the failure, but not the document")
        void whenStepOfReadingFailsThenDocumentIsRefusedAtItsStartAndFailureIsLogged(String description,
                Throwable failure, Problem expected, List<String> values, String shortForm, String explanation) {
            val refusal = SyntaxAnalysis.refusingUnforeseenFailures(DOCUMENT, failingWith(failure),
                    Function.<Diagnostic>identity());

            assertThat(refusal).returns(expected, Diagnostic::problem).returns(values, Diagnostic::values)
                    .returns(shortForm, Diagnostic::shortForm).returns(explanation, Diagnostic::explanation)
                    .returns(0, problem -> problem.region().start()).returns(0, problem -> problem.region().end());
            assertThat(getTestLogger(SyntaxAnalysis.class).getLoggingEvents()).singleElement()
                    .returns(Level.ERROR, LoggingEvent::getLevel)
                    .returns(Optional.of(failure), LoggingEvent::getThrowable)
                    .returns(List.of(), LoggingEvent::getArguments)
                    .returns(LOG_UNFORESEEN_FAILURE, LoggingEvent::getMessage);
        }

        @Test
        @DisplayName("a step of reading that succeeds returns its result and logs nothing")
        void whenStepOfReadingSucceedsThenItsResultIsReturnedAndNothingIsLogged() {
            val read = SyntaxAnalysis.refusingUnforeseenFailures(DOCUMENT, () -> DOCUMENT, problem -> "");

            assertThat(read).isEqualTo(DOCUMENT);
            assertThat(getTestLogger(SyntaxAnalysis.class).getLoggingEvents()).isEmpty();
        }

        private static <T> Supplier<T> failingWith(Throwable failure) {
            return () -> {
                if (failure instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) failure;
            };
        }
    }

}
