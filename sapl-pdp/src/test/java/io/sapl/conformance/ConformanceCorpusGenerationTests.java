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
import io.sapl.conformance.ConformanceCorpus.CaseFile;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * corpus, and the outcomes of the function corpus with the catalogue of the
 * function libraries it covers. The introductions, headings and documents stay
 * as they are written. The case files of libraries of extensions that depend
 * on this module stay as they are, and the catalogue, which describes them
 * too, is written where all libraries are on the class path: by the tests of
 * such an extension.
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
                arguments("evaluation", (UnaryOperator<Case>) EvaluationOutcomes::withOutcome),
                arguments("functions", (UnaryOperator<Case>) FunctionOutcomes::withOutcome));
    }

    @MethodSource("generatedCorpora")
    @ParameterizedTest(name = "{0}")
    @DisplayName("every case file of a corpus is written with what this engine yields for its cases")
    void whenCorpusIsGeneratedThenEveryCaseFileHoldsWhatThisEngineYields(String corpus, UnaryOperator<Case> generator) {
        val absent = ConformanceLibraries.absentLibraries();
        ConformanceCorpus.caseFilesOf(corpus).stream().filter(file -> !absent.contains(libraryOf(file)))
                .forEach(file -> CaseFileWriter.rewrite(file, generator));

        assertThat(ConformanceCorpus.caseFilesOf(corpus)).filteredOn(file -> !absent.contains(libraryOf(file)))
                .flatMap(CaseFile::cases)
                .allSatisfy(generatedCase -> assertThat(generator.apply(generatedCase)).isEqualTo(generatedCase));
    }

    /**
     * The catalogue describes the libraries of the extensions that depend on
     * this module too. Where they are not on the class path, the tests of those
     * extensions write it, and here the catalogue is only compared for the
     * libraries of this engine.
     */
    @Test
    @DisplayName("the function catalogue is written with the libraries of this engine that the function corpus covers")
    void whenFunctionCatalogueIsGeneratedThenItHoldsTheLibrariesOfThisEngine() {
        if (ConformanceLibraries.absentLibraries().isEmpty()) {
            FunctionCatalogue.write();
        }

        assertThat(ConformanceLibraries.withoutAbsentLibraries(FunctionCatalogue.stated()))
                .isEqualTo(FunctionCatalogue.catalogue());
    }

    /**
     * The name of a case file without its extension, which in the function
     * corpus is the name of the library it covers.
     */
    private static String libraryOf(CaseFile file) {
        val name = file.path().toFile().getName();
        return name.substring(0, name.lastIndexOf('.'));
    }
}
