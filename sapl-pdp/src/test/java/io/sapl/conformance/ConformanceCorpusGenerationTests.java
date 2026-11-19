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
package io.sapl.conformance;

import io.sapl.conformance.ConformanceCorpus.Case;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Writes what this engine yields into the case files of the conformance
 * corpus: the verdicts of the parser corpus, the reports of the corpora
 * "diagnostics" and "several-mistakes", and the outcomes of the evaluation
 * corpus. The introductions, headings and documents stay as they are written.
 * <p>
 * These tests change the corpus they read. They run in the Maven profile
 * "conformance-dump" only, and a review of the difference in the corpus
 * decides what is kept.
 */
@Tag("conformance-dump")
@DisplayName("Generation of the conformance corpus")
class ConformanceCorpusGenerationTests {

    static Stream<Arguments> generatedCorpora() {
        return Stream.of(arguments("parser", (UnaryOperator<Case>) ParserVerdicts::withVerdict),
                arguments("diagnostics", (UnaryOperator<Case>) DiagnosticReports::withReport),
                arguments("several-mistakes", (UnaryOperator<Case>) DiagnosticReports::withReport),
                arguments("evaluation", (UnaryOperator<Case>) EvaluationOutcomes::withOutcome));
    }

    @MethodSource("generatedCorpora")
    @ParameterizedTest(name = "{0}")
    @DisplayName("every case file of a corpus is written with what this engine yields for its cases")
    void whenCorpusIsGeneratedThenEveryCaseFileHoldsWhatThisEngineYields(String corpus, UnaryOperator<Case> generator) {
        ConformanceCorpus.caseFilesOf(corpus).forEach(file -> CaseFileWriter.rewrite(file, generator));

        assertThat(ConformanceCorpus.casesOf(corpus))
                .allSatisfy(generatedCase -> assertThat(generator.apply(generatedCase)).isEqualTo(generatedCase));
    }
}
