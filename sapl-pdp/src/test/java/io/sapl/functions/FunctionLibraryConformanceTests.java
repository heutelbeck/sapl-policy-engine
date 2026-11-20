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
package io.sapl.functions;

import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.conformance.ConformanceLibraries;
import io.sapl.conformance.FunctionCatalogue;
import io.sapl.conformance.FunctionOutcomes;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every implementation of SAPL offers the same library functions, with the
 * same values and error outcomes. The function corpus states what a
 * function returns for its arguments, and the function catalogue describes the
 * libraries it covers. The libraries of extensions that depend on this module
 * are not on the class path here, and the tests of each such extension check
 * its cases.
 */
@Tag("conformance")
@DisplayName("Function libraries against the conformance corpus")
class FunctionLibraryConformanceTests {

    static Stream<Case> casesOfTheFunctionCorpus() {
        val absent = ConformanceLibraries.absentLibraries();
        return ConformanceCorpus.casesOf("functions").stream()
                .filter(functionCase -> !absent.contains(libraryOf(functionCase)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casesOfTheFunctionCorpus")
    @DisplayName("a function called through the broker returns what the corpus states")
    void whenFunctionIsCalledThenItReturnsWhatTheCorpusStates(Case functionCase) {
        assertThat(FunctionOutcomes.agrees(functionCase)).as("%s: stated %s, returned %s", functionCase,
                functionCase.expectation(), FunctionOutcomes.outcomeOf(functionCase)).isTrue();
    }

    @Test
    @DisplayName("the libraries the corpus covers are described as the function catalogue states")
    void whenLibrariesAreDescribedThenTheyMatchTheFunctionCatalogue() {
        assertThat(FunctionCatalogue.catalogue())
                .isEqualTo(ConformanceLibraries.withoutAbsentLibraries(FunctionCatalogue.stated()));
    }

    private static String libraryOf(Case functionCase) {
        val function = functionCase.documentAsWritten().lines().findFirst().orElse("");
        return function.substring(0, Math.max(function.indexOf('.'), 0));
    }
}
