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

import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.sapl.conformance.DiagnosticReports.ACCEPTED;
import static io.sapl.conformance.DiagnosticReports.BETWEEN_PROBLEMS;
import static io.sapl.conformance.DiagnosticReports.RUN_OF_PROBLEMS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every problem class of the corpus belongs to the catalogue. The texts and
 * labels of the catalogue are this engine's own.
 */
@Tag("conformance")
@DisplayName("Catalogue of problems against the conformance corpus")
class CatalogueConformanceTests {

    private static final List<String> CORPORA                = List.of("diagnostics", "several-mistakes");
    private static final Set<String>  CLASSES_WITHOUT_A_CASE = Set.of("syntax-error", "document-too-large");

    private static final Pattern PROBLEM_AND_REGION = Pattern.compile("(?<identifier>[a-z0-9-]+) \\d+:\\d+-\\d+:\\d+");
    private static final Pattern FURTHER_REGION     = Pattern.compile("also \\d+:\\d+-\\d+:\\d+ .+");
    private static final Pattern QUICK_FIX          = Pattern.compile("fix \\d+:\\d+-\\d+:\\d+ \".*\"");

    private static final String ERROR_NO_PROBLEM      = "The statement of %s does not state a problem with its class and region, short form and explanation.";
    private static final String ERROR_UNEXPECTED_LINE = "The statement of %s holds a line that is neither a further region nor a quick fix: '%s'.";

    private record Stated(String name, String identifier) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Stated> statedProblems() {
        return CORPORA.stream().flatMap(corpus -> ConformanceCorpus.casesOf(corpus).stream())
                .filter(conformanceCase -> !ACCEPTED.equals(conformanceCase.expectation()))
                .flatMap(conformanceCase -> List.of(conformanceCase.expectation().split(BETWEEN_PROBLEMS)).stream()
                        .map(block -> statedProblem(conformanceCase, block)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statedProblems")
    @DisplayName("every problem and run of problems names a class of the catalogue")
    void whenProblemIsReportedThenItsClassBelongsToTheCatalogue(Stated stated) {
        assertThat(Arrays.stream(Problem.values()).map(Problem::identifier)).as(stated.name())
                .contains(stated.identifier());
    }

    @Test
    @DisplayName("every class of the catalogue has a case, except those known to have none")
    void whenCorpusIsReadThenEveryClassHasACaseExceptThoseKnownToHaveNone() {
        val reported    = statedProblems().map(Stated::identifier).collect(Collectors.toSet());
        val withoutCase = Arrays.stream(Problem.values()).map(Problem::identifier).distinct()
                .filter(identifier -> !reported.contains(identifier)).toList();

        assertThat(withoutCase).containsExactlyInAnyOrderElementsOf(CLASSES_WITHOUT_A_CASE);
    }

    private static Stated statedProblem(Case conformanceCase, String block) {
        val run = RUN_OF_PROBLEMS.matcher(block);
        if (run.matches()) {
            return new Stated(conformanceCase.toString(), run.group("identifier"));
        }
        val lines   = block.lines().toList();
        val heading = PROBLEM_AND_REGION.matcher(lines.isEmpty() ? "" : lines.getFirst());
        if (lines.size() < 3 || !heading.matches()) {
            throw new IllegalStateException(ERROR_NO_PROBLEM.formatted(conformanceCase));
        }
        for (val line : lines.subList(3, lines.size())) {
            if (!FURTHER_REGION.matcher(line).matches() && !QUICK_FIX.matcher(line).matches()) {
                throw new IllegalStateException(ERROR_UNEXPECTED_LINE.formatted(conformanceCase, line));
            }
        }
        return new Stated(conformanceCase.toString(), heading.group("identifier"));
    }
}
