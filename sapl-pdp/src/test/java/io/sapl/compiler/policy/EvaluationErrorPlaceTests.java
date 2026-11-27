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
package io.sapl.compiler.policy;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.document.Vote;
import io.sapl.util.PlacedDocuments.Placed;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.PlacedDocuments.GRINNING_FACE;
import static io.sapl.util.PlacedDocuments.documentOf;
import static io.sapl.util.PlacedDocuments.placedWithEveryLineEnd;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

/**
 * A condition that fails while a policy is evaluated makes the decision
 * indeterminate. Its error names the place of the failing expression, and
 * traces, reports and logs show that place to the author of the policy.
 */
@DisplayName("An error of the evaluation names the place of the failing expression")
class EvaluationErrorPlaceTests {

    private static final String FAILING_COMPARISON = "subject.age < 18";

    /**
     * Policies whose third line holds a comparison that fails for a subject
     * without an age, and where that comparison stands.
     */
    static Stream<Placed> failingComparisons() {
        val plainLine    = "    " + FAILING_COMPARISON + ";";
        val afterTheFace = "    \"" + GRINNING_FACE + "\" == subject.mood || " + FAILING_COMPARISON + ";";
        return Stream.concat(placedWithEveryLineEnd(plainLine, FAILING_COMPARISON, 3, 5, 3, 21),
                Stream.of(new Placed("a character outside the basic plane before the comparison, on its line",
                        documentOf("\n", afterTheFace), FAILING_COMPARISON, 3, 28, 3, 44)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingComparisons")
    @DisplayName("the error in a trace names its lines and columns, counted from 1 in whole characters")
    void whenComparisonFailsThenItsErrorInATraceNamesLinesAndColumnsInWholeCharacters(Placed placed) {
        val vote = voteOfEvaluating(placed.document());

        assertThat(vote).isNotNull().extracting(Vote::errors, list(ErrorValue.class)).singleElement()
                .extracting(ValueJsonMarshaller::toJsonNodeLenient)
                .extracting(error -> error.get("line").asInt(), error -> error.get("column").asInt(),
                        error -> error.get("endLine").asInt(), error -> error.get("endColumn").asInt())
                .containsExactly(placed.line(), placed.column(), placed.endLine(), placed.endColumn());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingComparisons")
    @DisplayName("the offsets of the error address the failing comparison in the document")
    void whenComparisonFailsThenTheOffsetsOfItsErrorAddressTheComparisonInTheDocument(Placed placed) {
        val vote = voteOfEvaluating(placed.document());

        assertThat(vote).isNotNull().extracting(Vote::errors, list(ErrorValue.class)).singleElement()
                .extracting(ErrorValue::location)
                .extracting(location -> placed.document().substring(location.start(), location.end()))
                .isEqualTo(placed.text());
    }

    private static Vote voteOfEvaluating(String document) {
        val compiled = DocumentCompiler.compileDocument(document, compilationContext());
        return compiled.applicabilityAndVote().evaluate(evaluationContext()).vote();
    }

}
