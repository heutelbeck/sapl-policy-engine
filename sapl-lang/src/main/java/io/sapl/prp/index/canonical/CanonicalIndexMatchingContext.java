/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import io.sapl.interpreter.EvaluationContext;
import lombok.Getter;
import lombok.Setter;

@Getter
public class CanonicalIndexMatchingContext {
    private final Bitmask candidatesMask;
    private final Bitmask matchingCandidatesMask;

    private final int[] trueLiteralsOfConjunction;
    private final int[] eliminatedFormulasWithConjunction;

    private final EvaluationContext subscriptionScopedEvaluationContext;

    @Setter
    private boolean errorsInTargets = false;

    public CanonicalIndexMatchingContext(CanonicalIndexDataContainer dataContainer,
                                         EvaluationContext subscriptionScopedEvaluationContext) {
        int arrayLength = dataContainer.getNumberOfLiteralsInConjunction().length;

        candidatesMask = new Bitmask();
        candidatesMask.set(0, arrayLength);

        matchingCandidatesMask = new Bitmask();

        trueLiteralsOfConjunction = new int[arrayLength];
        eliminatedFormulasWithConjunction = new int[arrayLength];

        this.subscriptionScopedEvaluationContext = subscriptionScopedEvaluationContext;
    }
}
