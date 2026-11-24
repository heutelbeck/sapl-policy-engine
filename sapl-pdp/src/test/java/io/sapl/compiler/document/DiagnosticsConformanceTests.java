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
import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.sapl.conformance.DiagnosticReports.reportOf;
import static io.sapl.conformance.DiagnosticReports.agrees;
import static io.sapl.conformance.DiagnosticReports.writtenReport;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine reports for each mistake the problem the conformance corpus
 * states. The corpora "diagnostics" and "several-mistakes" state the report for each of
 * their documents. Classes and regions are compared. In "diagnostics" further
 * regions and quick fixes are compared too, without messages or labels. The quick fixes
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
        val deviation = deviationFromStatedReport(conformanceCase, false)
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
        assertThat(deviationFromStatedReport(conformanceCase, true)).as(ERROR_NOT_AS_STATED, conformanceCase).isEmpty();
    }

    private static Optional<String> deviationFromStatedReport(Case conformanceCase, boolean severalMistakes) {
        val stated   = conformanceCase.expectation();
        val reported = reportOf(conformanceCase.document());
        if (agrees(reported, stated, severalMistakes)) {
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

    private static String indented(String text) {
        return text.lines().map(line -> INDENTATION + line + LINE_FEED).collect(Collectors.joining());
    }

}
