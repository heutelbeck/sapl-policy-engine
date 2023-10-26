/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import lombok.Getter;
import lombok.Setter;

public class CanonicalIndexMatchingContext {

    private final Bitmask candidatesMask;

    @Getter
    private final Bitmask matchingCandidatesMask;

    private final int[] trueLiteralsOfConjunction;

    private final int[] eliminatedFormulasWithConjunction;

    @Getter
    @Setter
    private boolean errorsInTargets = false;

    public CanonicalIndexMatchingContext(int numberOfConjunctions) {
        candidatesMask = new Bitmask();
        candidatesMask.set(0, numberOfConjunctions);

        matchingCandidatesMask = new Bitmask();

        trueLiteralsOfConjunction         = new int[numberOfConjunctions];
        eliminatedFormulasWithConjunction = new int[numberOfConjunctions];
    }

    Bitmask getCopyOfCandidates() {
        return new Bitmask(candidatesMask);
    }

    boolean isRemainingCandidate(int candidateIndex) {
        return candidatesMask.isSet(candidateIndex);
    }

    boolean isPredicateReferencedInCandidates(final Predicate predicate) {
        return predicate.getConjunctions().intersects(candidatesMask);
    }

    void incrementTrueLiteralsForConjunction(int conjunctionIndex) {
        trueLiteralsOfConjunction[conjunctionIndex] += 1;
    }

    void increaseNumberOfEliminatedFormulasForConjunction(int conjunctionIndex, long numberOfEliminatedFormulas) {
        eliminatedFormulasWithConjunction[conjunctionIndex] += numberOfEliminatedFormulas;
    }

    boolean isConjunctionSatisfied(int conjunctionIndex, int numberOfLiteralsInConjunction) {
        return trueLiteralsOfConjunction[conjunctionIndex] == numberOfLiteralsInConjunction;
    }

    boolean areAllFunctionsEliminated(int conjunctionIndex, int numberOfFormulasWithConjunction) {
        return eliminatedFormulasWithConjunction[conjunctionIndex] == numberOfFormulasWithConjunction;
    }

    void addSatisfiedCandidates(Bitmask satisfiedCandidates) {
        matchingCandidatesMask.or(satisfiedCandidates);
    }

    void addCandidates(Bitmask candidatesToAdd) {
        candidatesMask.or(candidatesToAdd);
    }

    void removeCandidates(Bitmask candidatesToRemove) {
        candidatesMask.andNot(candidatesToRemove);
    }

}
