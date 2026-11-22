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

import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceLibraries;
import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.conformance.FunctionCatalogue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The geographic library against its cases in the function corpus, and the
 * catalogue of all libraries the corpus covers, which this module can load.
 */
@Tag("conformance")
@DisplayName("Geographic function library against the conformance corpus")
class GeographicFunctionLibraryConformanceTests {

    static Stream<Case> casesOfTheGeographicLibrary() {
        return ConformanceCorpus.caseFilesOf(GeographicCorpus.CORPUS).stream().filter(GeographicCorpus::isCaseFile)
                .flatMap(file -> file.cases().stream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casesOfTheGeographicLibrary")
    @DisplayName("a geographic function called through the broker returns what the corpus states")
    void whenFunctionIsCalledThenItReturnsWhatTheCorpusStates(Case functionCase) {
        assertThat(GeometryOutcomes.agrees(functionCase)).as("%s: stated %s, returned %s", functionCase,
                functionCase.expectation(), GeometryOutcomes.outcomeOf(functionCase)).isTrue();
    }

    @Test
    @DisplayName("the available libraries are described as the function catalogue states")
    void whenLibrariesAreDescribedThenTheyMatchTheFunctionCatalogue() {
        assertThat(FunctionCatalogue.catalogue())
                .isEqualTo(ConformanceLibraries.withoutAbsentLibraries(FunctionCatalogue.stated()));
    }
}
