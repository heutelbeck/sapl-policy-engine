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

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.FurtherRegion;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.document.ConformanceCorpus.Case;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.sapl.compiler.document.ConformanceCorpus.ACCEPTED;
import static io.sapl.compiler.document.ConformanceCorpus.BETWEEN_PROBLEMS;
import static io.sapl.compiler.document.ConformanceCorpus.RUN_OF_PROBLEMS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every implementation of SAPL reports the same problem for the same mistake.
 * The corpora "diagnostics" and "several-mistakes" state the report for each of
 * their documents in the format of the corpus, and the report of the engine is
 * written in that format and compared as text. In "diagnostics" the quick fixes
 * of a document must also leave a document without problems.
 */
@Tag("conformance")
@DisplayName("Diagnostics against the conformance corpus")
class DiagnosticsConformanceTests {

    private static final String ONE_MISTAKE      = "diagnostics";
    private static final String SEVERAL_MISTAKES = "several-mistakes";

    private static final String INDENTATION = "    ";
    private static final char   LINE_FEED   = '\n';

    private static final String DEVIATION           = "  expected:%n%s  reported:%n%s";
    private static final String LEFT_BY_QUICK_FIXES = "  the quick fixes leave problems:%n%s";
    private static final String RUN                 = "<%d times %s>";
    private static final String WRITTEN_FURTHER     = "also %s %s";
    private static final String WRITTEN_PROBLEM     = "%s %s";
    private static final String WRITTEN_QUICK_FIX   = "fix %s \"%s\"";
    private static final String WRITTEN_REGION      = "%d:%d-%d:%d";

    private static final String ERROR_NOT_AS_STATED = "The engine does not report %s as the corpus states.";

    static Stream<Case> casesWithOneMistake() {
        return ConformanceCorpus.casesOf(ONE_MISTAKE).stream();
    }

    static Stream<Case> casesWithSeveralMistakes() {
        return ConformanceCorpus.casesOf(SEVERAL_MISTAKES).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casesWithOneMistake")
    @DisplayName("a document with one mistake is reported as the catalogue states, and its quick fixes leave no problem")
    void whenDocumentHoldsOneMistakeThenItIsReportedAsTheCatalogueStates(Case conformanceCase) {
        val deviation = deviationFromStatedReport(conformanceCase)
                .or(() -> problemsLeftByQuickFixes(conformanceCase.document()));

        assertThat(deviation).as(ERROR_NOT_AS_STATED, conformanceCase).isEmpty();
    }

    /**
     * Repairing one of several mistakes can uncover another that was not
     * reported before. So the quick fixes of these documents are not expected to
     * leave a document without problems.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("casesWithSeveralMistakes")
    @DisplayName("a document with several mistakes is reported as the corpus states")
    void whenDocumentHoldsSeveralMistakesThenEachIsReportedAsTheCorpusStates(Case conformanceCase) {
        assertThat(deviationFromStatedReport(conformanceCase)).as(ERROR_NOT_AS_STATED, conformanceCase).isEmpty();
    }

    private static Optional<String> deviationFromStatedReport(Case conformanceCase) {
        val stated   = conformanceCase.expectation();
        val reported = withRunsAsStated(reportOf(conformanceCase.document()), stated);
        if (reported.equals(stated)) {
            return Optional.empty();
        }
        return Optional.of(DEVIATION.formatted(indented(stated), indented(reported)));
    }

    private static Optional<String> problemsLeftByQuickFixes(String document) {
        val quickFixes = DocumentCompiler.parseDocument(document).diagnostics().stream().map(Diagnostic::quickFix)
                .filter(Objects::nonNull).toList();
        if (quickFixes.isEmpty()) {
            return Optional.empty();
        }
        val remaining = DocumentCompiler.parseDocument(QuickFixes.applied(document, quickFixes));
        if (!remaining.isInvalid()) {
            return Optional.empty();
        }
        return Optional.of(LEFT_BY_QUICK_FIXES.formatted(indented(writtenReport(remaining.diagnostics()))));
    }

    /**
     * What the engine reports for a document, in the format of the corpus.
     */
    private static String reportOf(String document) {
        val parsed = DocumentCompiler.parseDocument(document);
        return parsed.isInvalid() ? writtenReport(parsed.diagnostics()) : ACCEPTED;
    }

    private static String writtenReport(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(DiagnosticsConformanceTests::written)
                .collect(Collectors.joining(BETWEEN_PROBLEMS)).stripTrailing();
    }

    private static String written(Diagnostic diagnostic) {
        val lines = new ArrayList<String>();
        lines.add(WRITTEN_PROBLEM.formatted(diagnostic.identifier(), written(diagnostic.region())));
        lines.add(diagnostic.shortForm());
        lines.add(diagnostic.explanation());
        for (val furtherRegion : diagnostic.furtherRegions()) {
            lines.add(written(furtherRegion));
        }
        val quickFix = diagnostic.quickFix();
        if (quickFix != null) {
            lines.add(WRITTEN_QUICK_FIX.formatted(written(quickFix.region()), quickFix.replacement()));
        }
        return String.join(String.valueOf(LINE_FEED), lines);
    }

    private static String written(FurtherRegion furtherRegion) {
        return WRITTEN_FURTHER.formatted(written(furtherRegion.region()), furtherRegion.label().text());
    }

    private static String written(Region region) {
        return WRITTEN_REGION.formatted(region.line(), region.column(), region.endLine(), region.endColumn());
    }

    /**
     * The report with its problems written as runs wherever the stated report
     * does so. A run of the stated report takes the problems of its class that
     * follow each other, up to its number, so that a report with fewer of them
     * differs in the number.
     */
    private static String withRunsAsStated(String reported, String stated) {
        val reportedBlocks = new ArrayDeque<>(List.of(reported.split(BETWEEN_PROBLEMS)));
        val blocks         = new ArrayList<String>();
        for (val statedBlock : stated.split(BETWEEN_PROBLEMS)) {
            val run = RUN_OF_PROBLEMS.matcher(statedBlock);
            if (run.matches()) {
                val count      = Integer.parseInt(run.group("count"));
                val identifier = run.group("identifier");
                var found      = 0;
                while (found < count && !reportedBlocks.isEmpty() && isOfClass(reportedBlocks.getFirst(), identifier)) {
                    reportedBlocks.removeFirst();
                    found++;
                }
                blocks.add(RUN.formatted(found, identifier));
            } else if (!reportedBlocks.isEmpty()) {
                blocks.add(reportedBlocks.removeFirst());
            }
        }
        blocks.addAll(reportedBlocks);
        return String.join(BETWEEN_PROBLEMS, blocks);
    }

    private static boolean isOfClass(String block, String identifier) {
        return block.startsWith(identifier + " ");
    }

    private static String indented(String text) {
        return text.lines().map(line -> INDENTATION + line + LINE_FEED).collect(Collectors.joining());
    }

}
