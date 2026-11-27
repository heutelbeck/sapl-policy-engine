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
package io.sapl.functions.geo.conformance;

import io.sapl.conformance.CaseFileWriter;
import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceLibraries;
import io.sapl.conformance.ConformanceCorpus.CaseFile;
import io.sapl.conformance.FunctionCatalogue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the outcomes of this engine into the case file of the geographic
 * library and writes the function catalogue of all libraries the corpus covers.
 * The introduction, headings and documents stay as they are written.
 * <p>
 * These tests change the corpus they read. They run in the Maven profile
 * "conformance-dump" only, and a review of the difference in the corpus
 * decides what is kept.
 */
@Tag("conformance-dump")
@DisplayName("Generation of the cases of the geographic library and of the function catalogue")
class GeographicCorpusGenerationTests {

    @Test
    @DisplayName("the case file of the geographic library is written with what this engine yields for its cases")
    void whenCasesAreGeneratedThenTheCaseFileHoldsWhatThisEngineYields() {
        ConformanceCorpus.caseFilesOf(GeographicCorpus.CORPUS).stream().filter(GeographicCorpus::isCaseFile)
                .forEach(file -> CaseFileWriter.rewrite(file, GeometryOutcomes::withOutcome));

        assertThat(ConformanceCorpus.caseFilesOf(GeographicCorpus.CORPUS)).filteredOn(GeographicCorpus::isCaseFile)
                .flatMap(CaseFile::cases)
                .allSatisfy(generatedCase -> assertThat(GeometryOutcomes.withOutcome(generatedCase))
                        .isEqualTo(generatedCase));
    }

    @Test
    @DisplayName("the geographic library matches its entry in the function catalogue")
    void whenFunctionCatalogueIsGeneratedThenItHoldsAllLibraries() {
        FunctionCatalogue.write();

        assertThat(ConformanceLibraries.withoutAbsentLibraries(FunctionCatalogue.stated()))
                .isEqualTo(FunctionCatalogue.catalogue());
    }
}
