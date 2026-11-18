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

import io.sapl.compiler.diagnostics.FurtherRegion.Label;
import io.sapl.compiler.document.ConformanceCorpus;
import io.sapl.compiler.document.ConformanceCorpus.Case;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.sapl.compiler.document.ConformanceCorpus.ACCEPTED;
import static io.sapl.compiler.document.ConformanceCorpus.BETWEEN_PROBLEMS;
import static io.sapl.compiler.document.ConformanceCorpus.RUN_OF_PROBLEMS;
import static io.sapl.util.PlacedDocuments.SOMEWHERE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The texts of the catalogue are those that all implementations of SAPL share,
 * word for word. The corpora "diagnostics" and "several-mistakes" state for
 * each of their documents what is reported, and every problem they state must
 * be one that the catalogue renders.
 */
@Tag("conformance")
@DisplayName("Catalogue of problems against the conformance corpus")
class CatalogueConformanceTests {

    private static final List<String> CORPORA = List.of("diagnostics", "several-mistakes");

    /**
     * The problems of the catalogue that no case of the corpus reports, and the
     * stand-in for the problems that the catalogue does not classify, which is
     * not part of the shared catalogue. Their texts are not proven by this test.
     * A problem that is listed here and is reported by a case fails the test, so
     * the list cannot go stale.
     */
    private static final Set<Problem> PROBLEMS_WITHOUT_A_CASE = Set.of(Problem.SYNTAX_ERROR,
            Problem.NESTING_TOO_DEEP_WITHOUT_BRACKETS, Problem.DOCUMENT_TOO_LARGE,
            Problem.INVISIBLE_PARAGRAPH_SEPARATOR, Problem.IMPORT_CONFLICT_WITHOUT_ORIGIN,
            Problem.UNQUOTED_POLICY_SET_NAME, Problem.MISPLACED_PART_AFTER_TRANSFORMATION,
            Problem.UNEXPECTED_END_AFTER_VALUE, Problem.UNEXPECTED_END_AFTER_VALUE_WITH_DELIMITERS,
            Problem.UNEXPECTED_END_WHERE_VALUE_EXPECTED, Problem.UNEXPECTED_END_WHERE_END_EXPECTED,
            Problem.UNEXPECTED_END_WHERE_ONE_OF_EXPECTED);

    private static final String WITHOUT_A_CASE = "The problems of the catalogue that render no problem of the corpus. One that is not expected here has a text that differs from the corpus, or the corpus has no case for it. One that is not found here is reported by a case and leaves the list of problems without a case.";

    private static final String OR = " or ";

    private static final Pattern FURTHER_REGION     = Pattern.compile("also \\d+:\\d+-\\d+:\\d+ (?<label>.+)");
    private static final Pattern MARKED_VALUE       = Pattern.compile("\0(?<number>\\d)\0");
    private static final Pattern PROBLEM_AND_REGION = Pattern.compile("(?<identifier>[a-z0-9-]+) \\d+:\\d+-\\d+:\\d+");
    private static final Pattern QUICK_FIX          = Pattern.compile("fix \\d+:\\d+-\\d+:\\d+ \".*\"");

    private static final String ERROR_NO_PROBLEM       = "The statement of %s does not state a problem. A problem is stated by its identifier and region, its short form and its explanation.";
    private static final String ERROR_UNEXPECTED_LINE  = "The statement of %s holds a line that is neither a further region nor a quick fix: '%s'.";
    private static final String ERROR_UNKNOWN_PROBLEM  = "The catalogue has no problem with the identifier '%2$s' that %1$s reports.";
    private static final String ERROR_WORDED_OTHERWISE = "No problem of the catalogue renders the two lines of %s:%n  short form:  %s%n  explanation: %s%nThe problems with the identifier '%s':%n%s";

    /**
     * One problem of a report of the corpus, or a run of problems of one class.
     *
     * @param name the case and the number of the problem in its report
     * @param lines the lines that state the problem
     */
    private record Stated(String name, List<String> lines) {

        /**
         * @return the identifier of the class, if this is a run of problems of
         * one class
         */
        Optional<String> classOfRun() {
            val run = RUN_OF_PROBLEMS.matcher(lines.size() == 1 ? lines.getFirst() : "");
            return run.matches() ? Optional.of(run.group("identifier")) : Optional.empty();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A problem as a report of the corpus states it.
     *
     * @param name the case and the number of the problem in its report
     * @param identifier the identifier of the class of the problem
     * @param shortForm the line that says what is wrong
     * @param explanation the line that says what to do
     * @param labels the labels of the further regions
     */
    private record ReportedProblem(
            String name,
            String identifier,
            String shortForm,
            String explanation,
            List<String> labels) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<ReportedProblem> reportedProblems() {
        return stated().stream().filter(stated -> stated.classOfRun().isEmpty())
                .map(CatalogueConformanceTests::reportedProblemOf);
    }

    static Stream<ReportedProblem> reportedProblemsWithFurtherRegions() {
        return reportedProblems().filter(reported -> !reported.labels().isEmpty());
    }

    static Stream<Arguments> runsOfProblems() {
        return stated().stream().flatMap(
                stated -> stated.classOfRun().map(identifier -> arguments(stated.name(), identifier)).stream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportedProblems")
    @DisplayName("a problem that the corpus reports is rendered by the catalogue, word for word")
    void whenProblemIsReportedThenTheCatalogueRendersItWordForWord(ReportedProblem reported) {
        val rendering = problemsThatRender(reported);

        assertThat(rendering).as(() -> whyNoneRenders(reported)).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reportedProblemsWithFurtherRegions")
    @DisplayName("a label of a further region that the corpus reports is a label of the catalogue")
    void whenFurtherRegionIsReportedThenItsLabelIsOneOfTheCatalogue(ReportedProblem reported) {
        val labelsOfTheCatalogue = Arrays.stream(Label.values()).map(Label::text).toList();

        assertThat(reported.labels()).as("%s: the labels of the further regions", reported)
                .isSubsetOf(labelsOfTheCatalogue);
    }

    @MethodSource("runsOfProblems")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a run of problems that the corpus reports names a class of the catalogue")
    void whenRunOfProblemsIsReportedThenItsIdentifierIsOneOfTheCatalogue(String name, String identifier) {
        assertThat(Arrays.stream(Problem.values()).map(Problem::identifier)).as(name).contains(identifier);
    }

    @Test
    @DisplayName("every problem of the catalogue is reported by a case, except those known to have none")
    void whenCorpusIsReadThenEveryProblemRendersACaseExceptThoseKnownToHaveNone() {
        val reported = reportedProblems().flatMap(problem -> problemsThatRender(problem).stream())
                .collect(Collectors.toSet());

        val withoutCase = Arrays.stream(Problem.values()).filter(problem -> !reported.contains(problem)).toList();

        assertThat(withoutCase).as(WITHOUT_A_CASE).containsExactlyInAnyOrderElementsOf(PROBLEMS_WITHOUT_A_CASE);
    }

    /**
     * What the reports of the corpus state, problem by problem.
     */
    private static List<Stated> stated() {
        val stated = new ArrayList<Stated>();
        for (val corpus : CORPORA) {
            for (val conformanceCase : ConformanceCorpus.casesOf(corpus)) {
                stated.addAll(statedIn(conformanceCase));
            }
        }
        return stated;
    }

    private static List<Stated> statedIn(Case conformanceCase) {
        if (ACCEPTED.equals(conformanceCase.expectation())) {
            return List.of();
        }
        val problems = conformanceCase.expectation().split(BETWEEN_PROBLEMS);
        return IntStream.range(0, problems.length).mapToObj(
                index -> new Stated(conformanceCase + ", problem " + (index + 1), problems[index].lines().toList()))
                .toList();
    }

    /**
     * A report that only resembles the format is refused. If a line were
     * skipped, what it states would not be checked.
     */
    private static ReportedProblem reportedProblemOf(Stated stated) {
        val lines   = stated.lines();
        val heading = PROBLEM_AND_REGION.matcher(lines.isEmpty() ? "" : lines.getFirst());
        if (lines.size() < 3 || !heading.matches()) {
            throw new IllegalStateException(ERROR_NO_PROBLEM.formatted(stated));
        }
        val labels = new ArrayList<String>();
        for (val line : lines.subList(3, lines.size())) {
            val furtherRegion = FURTHER_REGION.matcher(line);
            if (furtherRegion.matches()) {
                labels.add(furtherRegion.group("label"));
            } else if (!QUICK_FIX.matcher(line).matches()) {
                throw new IllegalStateException(ERROR_UNEXPECTED_LINE.formatted(stated, line));
            }
        }
        return new ReportedProblem(stated.name(), heading.group("identifier"), lines.get(1), lines.get(2), labels);
    }

    private static List<Problem> problemsThatRender(ReportedProblem reported) {
        return problemsOfClass(reported.identifier()).filter(problem -> renders(problem, reported)).toList();
    }

    private static Stream<Problem> problemsOfClass(String identifier) {
        return Arrays.stream(Problem.values()).filter(problem -> problem.identifier().equals(identifier));
    }

    private static boolean renders(Problem problem, ReportedProblem reported) {
        return valuesThatRender(problem, reported).filter(values -> areWordedByTheCatalogue(problem, values))
                .map(values -> new Diagnostic(problem, values, SOMEWHERE, List.of(), null))
                .filter(diagnostic -> diagnostic.shortForm().equals(reported.shortForm())
                        && diagnostic.explanation().equals(reported.explanation()))
                .isPresent();
    }

    /**
     * The values with which the texts of the problem read as the two lines of the
     * reported problem. Empty if no values make them read so.
     */
    private static Optional<List<String>> valuesThatRender(Problem problem, ReportedProblem reported) {
        val marks   = marksFor(problem);
        val texts   = patternOf(problem.shortForm(marks) + "\n" + problem.explanation(marks));
        val matcher = texts.matcher(reported.shortForm() + "\n" + reported.explanation());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(IntStream.rangeClosed(1, problem.numberOfValues())
                .mapToObj(number -> matcher.group(groupOf(String.valueOf(number)))).toList());
    }

    /**
     * Values that stand out in a rendered text, so that the text shows where
     * which value goes.
     */
    private static List<String> marksFor(Problem problem) {
        return IntStream.rangeClosed(1, problem.numberOfValues()).mapToObj(number -> "\0" + number + "\0").toList();
    }

    /**
     * The pattern of the texts that a rendering with marked values stands for:
     * the same words, and any text in place of a value. A value that the texts
     * name more than once is the same text each time. A value does not reach
     * from the short form into the explanation.
     */
    private static Pattern patternOf(String renderedWithMarks) {
        val pattern = new StringBuilder();
        val seen    = new HashSet<String>();
        val marked  = MARKED_VALUE.matcher(renderedWithMarks);
        var words   = 0;
        while (marked.find()) {
            pattern.append(Pattern.quote(renderedWithMarks.substring(words, marked.start())));
            val group = groupOf(marked.group("number"));
            pattern.append(seen.add(group) ? "(?<" + group + ">.*)" : "\\k<" + group + ">");
            words = marked.end();
        }
        pattern.append(Pattern.quote(renderedWithMarks.substring(words)));
        return Pattern.compile(pattern.toString(), Pattern.UNIX_LINES);
    }

    private static String groupOf(String numberOfValue) {
        return "value" + numberOfValue;
    }

    /**
     * Some values are worded by the catalogue: a count as a word, a list of what
     * can stand at a place. Such a value proves the wording only if the catalogue
     * words it the same way.
     */
    private static boolean areWordedByTheCatalogue(Problem problem, List<String> values) {
        return switch (problem) {
        case NAME_TOO_LONG                              ->
            values.get(1).equals(Problem.countInWords(values.get(0).split("\\.").length));
        case UNEXPECTED_AFTER_VALUE_WITH_DELIMITERS     -> isListOfDelimiters(values.get(1));
        case UNEXPECTED_END_AFTER_VALUE_WITH_DELIMITERS -> isListOfDelimiters(values.get(0));
        case UNEXPECTED_WHERE_ONE_OF_EXPECTED           -> isListOfAlternatives(values.get(1));
        case UNEXPECTED_END_WHERE_ONE_OF_EXPECTED       -> isListOfAlternatives(values.get(0));
        default                                         -> true;
        };
    }

    private static boolean isListOfAlternatives(String listed) {
        val lastOr       = listed.lastIndexOf(OR);
        val alternatives = new ArrayList<String>();
        if (lastOr < 0) {
            alternatives.add(listed);
        } else {
            alternatives.addAll(List.of(listed.substring(0, lastOr).split(", ")));
            alternatives.add(listed.substring(lastOr + OR.length()));
        }
        val words = Set.of(Problem.A_NAME, Problem.A_NUMBER, Problem.A_TEXT);
        return alternatives.stream().allMatch(alternative -> words.contains(alternative) || isToken(alternative))
                && Problem.oneOf(alternatives).equals(listed);
    }

    private static boolean isListOfDelimiters(String listed) {
        val quoted = List.of(listed.split(OR));
        return quoted.stream().allMatch(CatalogueConformanceTests::isToken) && Problem
                .delimiters(quoted.stream().map(CatalogueConformanceTests::withoutQuotes).toList()).equals(listed);
    }

    private static boolean isToken(String worded) {
        return worded.length() > 2 && Problem.token(withoutQuotes(worded)).equals(worded);
    }

    private static String withoutQuotes(String token) {
        return token.substring(1, token.length() - 1);
    }

    private static String whyNoneRenders(ReportedProblem reported) {
        val ofClass = problemsOfClass(reported.identifier()).toList();
        if (ofClass.isEmpty()) {
            return ERROR_UNKNOWN_PROBLEM.formatted(reported, reported.identifier());
        }
        val differences = ofClass.stream().map(problem -> "  %s: %s".formatted(problem, whatDiffers(problem, reported)))
                .collect(Collectors.joining(System.lineSeparator()));
        return ERROR_WORDED_OTHERWISE.formatted(reported, reported.shortForm(), reported.explanation(),
                reported.identifier(), differences);
    }

    private static String whatDiffers(Problem problem, ReportedProblem reported) {
        val marks            = marksFor(problem);
        val shortFormReads   = patternOf(problem.shortForm(marks)).matcher(reported.shortForm()).matches();
        val explanationReads = patternOf(problem.explanation(marks)).matcher(reported.explanation()).matches();
        val names            = IntStream.rangeClosed(1, problem.numberOfValues())
                .mapToObj(number -> "<value " + number + ">").toList();
        val shortFormOfIt    = problem.shortForm(names);
        val explanationOfIt  = problem.explanation(names);
        if (!shortFormReads && !explanationReads) {
            return "the short form and the explanation differ: %s %s".formatted(shortFormOfIt, explanationOfIt);
        }
        if (!shortFormReads) {
            return "the short form differs: %s".formatted(shortFormOfIt);
        }
        if (!explanationReads) {
            return "the explanation differs: %s".formatted(explanationOfIt);
        }
        return "a value differs between the two lines, or is not worded as the catalogue words it";
    }

}
