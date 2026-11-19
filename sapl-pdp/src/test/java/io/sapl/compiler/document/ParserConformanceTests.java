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
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.conformance.ParserVerdicts.isStatedValid;
import static io.sapl.conformance.ParserVerdicts.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every implementation of SAPL accepts the same documents. The corpus "parser"
 * states for each of its documents whether it is valid SAPL.
 */
@Tag("conformance")
@DisplayName("Parser against the conformance corpus")
class ParserConformanceTests {

    private static final String CORPUS = "parser";

    static Stream<Case> casesOfTheParserCorpus() {
        return ConformanceCorpus.casesOf(CORPUS).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casesOfTheParserCorpus")
    @DisplayName("a document is accepted exactly if the corpus states that it is valid")
    void whenDocumentIsParsedThenItIsAcceptedExactlyIfTheCorpusStatesThatItIsValid(Case conformanceCase) {
        val statedValid = isStatedValid(conformanceCase);

        val refusal = refusalOf(conformanceCase.document());

        assertThat(refusal.isEmpty()).as("%s: the corpus states that the document is %s, and the engine %s",
                conformanceCase, statedValid ? "valid" : "invalid",
                refusal.map(errors -> "refuses it: " + errors).orElse("accepts it")).isEqualTo(statedValid);
    }

    /**
     * A refused document is refused for a problem that the checks name. A
     * failure of the transformation that they did not foresee would make a
     * document invalid as well, and pass as a refusal the corpus states.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("casesOfTheParserCorpus")
    @DisplayName("no document is refused for a failure that the checks did not foresee")
    void whenDocumentIsParsedThenNoProblemIsAnUnforeseenFailure(Case conformanceCase) {
        val document = DocumentCompiler.parseDocument(conformanceCase.document());

        assertThat(document.diagnostics()).as(document.errors()).extracting(Diagnostic::problem)
                .doesNotContain(Problem.SYNTAX_ERROR);
    }

}
