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
package io.sapl.compiler.diagnostics;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.sapl.util.PlacedDocuments.SOMEWHERE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Catalogue of problems")
class ProblemTests {

    private static final String PRINTABLE_ASCII = "[ -~]+";

    /**
     * The identifiers of the catalogue that all implementations of SAPL share.
     * Tests, agents and editors filter by them, so none may change by accident.
     */
    private static final String SHARED_IDENTIFIERS = """
            assignment-instead-of-comparison
            attribute-in-filter-target
            attribute-in-schema
            attribute-name-missing
            bidirectional-control-character
            chained-comparison
            combining-algorithm-malformed
            document-start
            document-too-large
            effect-missing
            empty-document
            empty-statement
            escaped-bidirectional-control
            filter-function-missing
            filter-statement-malformed
            import-conflict
            import-malformed
            incomplete-unicode-escape
            index-not-an-integer
            invalid-escape
            invalid-name-character
            invisible-character
            key-missing
            keyword-as-name
            leading-zero
            lone-surrogate-escape
            malformed-number
            mismatched-bracket
            misplaced-import-or-schema
            name-incomplete
            name-missing
            name-not-imported
            name-too-long
            nested-policy-set
            nesting-too-deep
            number-out-of-range
            number-too-long
            object-entry-malformed
            operator-incomplete
            operator-missing
            policy-keyword-missing
            policy-part-order
            policy-set-without-policy
            reserved-variable-name
            schema-statement-malformed
            second-policy
            semicolon-after-obligation
            separator-missing
            statement-after-obligation
            statement-not-terminated
            subscript-malformed
            too-many-problems
            trailing-comma
            typographic-quotation-mark
            unclosed-bracket
            unexpected-character
            unexpected-token
            unknown-operator
            unmatched-closing-bracket
            unterminated-comment
            unterminated-string
            value-missing
            variable-definition-malformed
            where-keyword
            """;

    /**
     * A diagnostic of the problem whose values are told apart by their names:
     * "value1", "value2" and so on.
     */
    private static Diagnostic withNamedValues(Problem problem) {
        return diagnosticOf(problem, namedValues(problem.numberOfValues()));
    }

    private static Diagnostic diagnosticOf(Problem problem, List<String> values) {
        return new Diagnostic(problem, values, SOMEWHERE, List.of(), null);
    }

    private static List<String> namedValues(int number) {
        return IntStream.rangeClosed(1, number).mapToObj(index -> "value" + index).toList();
    }

    @Nested
    @DisplayName("the catalogue")
    class CatalogueTests {

        @Test
        @DisplayName("holds exactly the identifiers that all implementations share")
        void whenIdentifiersAreCollectedThenTheyAreTheSharedOnes() {
            val identifiers = Arrays.stream(Problem.values()).filter(problem -> problem != Problem.SYNTAX_ERROR)
                    .map(Problem::identifier).distinct();

            assertThat(identifiers).containsExactlyInAnyOrderElementsOf(SHARED_IDENTIFIERS.lines().toList());
        }

        @Test
        @DisplayName("holds each message once")
        void whenMessagesAreRenderedThenNoTwoProblemsReadTheSame() {
            val messages = Arrays.stream(Problem.values()).map(ProblemTests::withNamedValues)
                    .map(diagnostic -> diagnostic.identifier() + " " + diagnostic.message());

            assertThat(messages).doesNotHaveDuplicates();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Problem.class)
        @DisplayName("says what is wrong in a short form and what to do in an explanation")
        void whenProblemIsRenderedThenItHasAnIdentifierAShortFormAndAnExplanation(Problem problem) {
            val diagnostic = withNamedValues(problem);

            assertThat(diagnostic).satisfies(rendered -> {
                assertThat(rendered.identifier()).matches("[a-z0-9]+(-[a-z0-9]+)*");
                assertThat(rendered.shortForm()).matches(PRINTABLE_ASCII).matches("[A-Z].*\\.").doesNotContain("%");
                assertThat(rendered.explanation()).matches(PRINTABLE_ASCII).isEqualTo(rendered.explanation().strip())
                        .doesNotContain("%");
                assertThat(rendered.message()).isEqualTo(rendered.shortForm() + " " + rendered.explanation());
            });
        }

        @EnumSource(Problem.class)
        @ParameterizedTest(name = "{0}")
        @DisplayName("names each of its values in its texts")
        void whenProblemIsRenderedThenItsTextsNameEachValue(Problem problem) {
            val diagnostic = withNamedValues(problem);

            assertThat(diagnostic.values()).allSatisfy(value -> assertThat(diagnostic.message()).contains(value));
        }

        @EnumSource(Problem.class)
        @ParameterizedTest(name = "{0}")
        @DisplayName("is refused with a value more than its texts name")
        void whenOneValueTooManyIsGivenThenDiagnosticIsRefused(Problem problem) {
            val oneTooMany = namedValues(problem.numberOfValues() + 1);

            assertThatThrownBy(() -> diagnosticOf(problem, oneTooMany)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(problem.name());
        }
    }

    @Nested
    @DisplayName("the stand-in for a problem that the catalogue does not classify")
    class StandInTests {

        @Test
        @DisplayName("passes on the message of the failure, and its identifier is none that the implementations share")
        void whenProblemIsNotClassifiedThenItPassesOnTheMessageOfTheFailure() {
            val message = "Unresolved reference 'foo': not imported and not fully qualified.";

            val diagnostic = diagnosticOf(Problem.SYNTAX_ERROR, List.of(message));

            assertThat(diagnostic).satisfies(standIn -> {
                assertThat(standIn.message()).contains(message);
                assertThat(SHARED_IDENTIFIERS.lines()).doesNotContain(standIn.identifier());
            });
        }
    }

    @Nested
    @DisplayName("the parts of a message that the catalogue words")
    class WordedPartsTests {

        static Stream<Arguments> countsAndTheirWords() {
            return Stream.of(arguments(2, "2"), arguments(3, "three"), arguments(4, "four"), arguments(5, "five"),
                    arguments(6, "six"), arguments(7, "seven"), arguments(8, "eight"), arguments(9, "nine"),
                    arguments(10, "ten"), arguments(11, "11"));
        }

        @MethodSource("countsAndTheirWords")
        @ParameterizedTest(name = "{0} is written as {1}")
        @DisplayName("a count from three to ten is a word, any other is written in digits")
        void whenCountIsWrittenThenSmallCountsAreWords(int count, String expected) {
            assertThat(Problem.countInWords(count)).isEqualTo(expected);
        }

        static Stream<Arguments> alternativesAndTheirList() {
            return Stream.of(arguments(List.of("'policy'"), "'policy'"),
                    arguments(List.of("'policy'", Problem.A_NAME), "'policy' or a name"),
                    arguments(List.of(Problem.A_TEXT, Problem.A_NUMBER, "'*'"),
                            "a text in quotation marks, a number or '*'"),
                    arguments(List.of("'import'", "'set'", "'policy'", Problem.A_NAME),
                            "'import', 'set', 'policy' or a name"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("alternativesAndTheirList")
        @DisplayName("alternatives are separated by commas, the last one by 'or'")
        void whenAlternativesAreListedThenTheLastIsJoinedByOr(List<String> alternatives, String expected) {
            assertThat(Problem.oneOf(alternatives)).isEqualTo(expected);
        }

        static Stream<Arguments> delimitersAndTheirList() {
            return Stream.of(arguments(List.of(";"), "';'"), arguments(List.of(",", "]"), "',' or ']'"),
                    arguments(List.of(";", ",", ")"), "';' or ',' or ')'"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("delimitersAndTheirList")
        @DisplayName("delimiters stand in single quotation marks and are joined by 'or'")
        void whenDelimitersAreListedThenEachIsQuotedAndJoinedByOr(List<String> delimiters, String expected) {
            assertThat(Problem.delimiters(delimiters)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a keyword or symbol stands in single quotation marks")
        void whenTokenIsWordedThenItStandsInSingleQuotationMarks() {
            assertThat(Problem.token("policy")).isEqualTo("'policy'");
        }

        static Stream<Arguments> waysToList() {
            return Stream.of(arguments("alternatives", (Function<List<String>, String>) Problem::oneOf),
                    arguments("delimiters", (Function<List<String>, String>) Problem::delimiters));
        }

        @MethodSource("waysToList")
        @ParameterizedTest(name = "{0}")
        @DisplayName("a list of nothing is refused")
        void whenNothingIsListedThenItIsRefused(String listed, Function<List<String>, String> list) {
            val nothing = List.<String>of();

            assertThatThrownBy(() -> list.apply(nothing)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("a diagnostic")
    class DiagnosticTests {

        @Test
        @DisplayName("fills the values of its occurrence into the texts of its problem")
        void whenDiagnosticIsRenderedThenValuesAreFilledIntoTheTexts() {
            val keywordAsKey = diagnosticOf(Problem.KEYWORD_AS_KEY, List.of("set", "resource"));

            assertThat(keywordAsKey).satisfies(diagnostic -> {
                assertThat(diagnostic.identifier()).isEqualTo("keyword-as-name");
                assertThat(diagnostic.shortForm()).isEqualTo("Keyword 'set' as key.");
                assertThat(diagnostic.explanation())
                        .isEqualTo("Write it in backticks or as a string: resource.`set` or resource[\"set\"]");
                assertThat(diagnostic.message()).isEqualTo(
                        "Keyword 'set' as key. Write it in backticks or as a string: resource.`set` or resource[\"set\"]");
            });
        }

        @Test
        @DisplayName("is refused with fewer values than the texts of its problem name")
        void whenAValueIsMissingThenDiagnosticIsRefused() {
            val oneTooFew = List.of("set");

            assertThatThrownBy(() -> diagnosticOf(Problem.KEYWORD_AS_KEY, oneTooFew))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("KEYWORD_AS_KEY");
        }

        @Test
        @DisplayName("a reported diagnostic does not change when the caller changes its lists afterwards")
        void whenCallerChangesItsListsAfterReportingThenTheDiagnosticDoesNotChange() {
            val values         = new ArrayList<>(List.of("===", "=="));
            val furtherRegions = new ArrayList<>(
                    List.of(new FurtherRegion(SOMEWHERE, FurtherRegion.Label.OPENED_HERE)));
            val diagnostic     = new Diagnostic(Problem.UNKNOWN_OPERATOR, values, SOMEWHERE, furtherRegions,
                    new QuickFix(SOMEWHERE, "=="));

            values.set(0, "<>");
            furtherRegions.clear();

            assertThat(diagnostic).satisfies(kept -> {
                assertThat(kept.shortForm()).isEqualTo("Unknown operator '==='.");
                assertThat(kept.furtherRegions()).hasSize(1);
            });
        }
    }

}
