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

import com.google.common.collect.ImmutableList;
import io.sapl.grammar.sapl.SAPL;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@AllArgsConstructor
@Getter(AccessLevel.NONE)
public class CanonicalIndexDataContainer {

    Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas;

    @Getter
    ImmutableList<Predicate> predicateOrder;

    List<Set<DisjunctiveFormula>> relatedFormulas;

    Map<DisjunctiveFormula, Bitmask> relatedCandidates;

    Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;

    int[] numberOfLiteralsInConjunction;

    int[] numberOfFormulasWithConjunction;

    @Getter
    int numberOfConjunctions;

    //TODO exposed internal representation (int arrays)
    public CanonicalIndexDataContainer(Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
                                       Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas,
                                       Collection<Predicate> predicateOrder, List<Set<DisjunctiveFormula>> relatedFormulas,
                                       Map<DisjunctiveFormula, Bitmask> relatedCandidates,
                                       Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction,
                                       int[] numberOfLiteralsInConjunction, int[] numberOfFormulasWithConjunction) {

        this(formulaToDocuments, clauseToFormulas, ImmutableList.copyOf(predicateOrder),
                relatedFormulas, relatedCandidates, conjunctionsInFormulasReferencingConjunction,
                numberOfLiteralsInConjunction, numberOfFormulasWithConjunction, numberOfLiteralsInConjunction.length);
    }


    public int getNumberOfLiteralsInConjunction(int conjunctionIndex) {
        return numberOfLiteralsInConjunction[conjunctionIndex];
    }


    public int getNumberOfFormulasWithConjunction(int conjunctionIndex) {
        return numberOfLiteralsInConjunction[conjunctionIndex];
    }


    public Set<CTuple> getConjunctionsInFormulasReferencingConjunction(int conjunctionIndex) {
        return conjunctionsInFormulasReferencingConjunction.get(conjunctionIndex);
    }

    Set<DisjunctiveFormula> getRelatedFormulas(int conjunctionIndex) {
        return relatedFormulas.get(conjunctionIndex);
    }

    Set<SAPL> getPoliciesIncludingFormula(DisjunctiveFormula formula) {
        return formulaToDocuments.get(formula);
    }


}
