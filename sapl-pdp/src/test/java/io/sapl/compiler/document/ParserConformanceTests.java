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

import io.sapl.compiler.document.ConformanceCorpus.Case;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every implementation of SAPL accepts the same documents. The corpus "parser"
 * states for each of its documents whether it is valid SAPL.
 */
@Tag("conformance")
@DisplayName("Parser against the conformance corpus")
class ParserConformanceTests {

    private static final String CORPUS = "parser";

    private static final String VALID   = "valid: ";
    private static final String INVALID = "invalid: ";

    private static final String ERROR_HOLDS_A_REPORT = "The case at %s holds a report. A case of the parser corpus holds a verdict and a document, and no '---'.";
    private static final String ERROR_NO_VERDICT     = "The heading of the case at %s does not start with 'valid: ' or 'invalid: '.";

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

    private static boolean isStatedValid(Case conformanceCase) {
        if (!conformanceCase.expectation().isEmpty()) {
            throw new IllegalStateException(ERROR_HOLDS_A_REPORT.formatted(conformanceCase.origin()));
        }
        if (conformanceCase.heading().startsWith(VALID)) {
            return true;
        }
        if (conformanceCase.heading().startsWith(INVALID)) {
            return false;
        }
        throw new IllegalStateException(ERROR_NO_VERDICT.formatted(conformanceCase.origin()));
    }

    /**
     * @return what the engine reports if it refuses the document, empty if it
     * accepts the document
     */
    private static Optional<String> refusalOf(String document) {
        try {
            val parsed = DocumentCompiler.parseDocument(document);
            return parsed.isInvalid() ? Optional.of(parsed.errors()) : Optional.empty();
        } catch (SaplCompilerException refusal) {
            // The engine refuses a document that holds a bidirectional control character
            // by throwing, where it returns an invalid document for every other mistake.
            return Optional.of(refusal.getMessage());
        }
    }

}
