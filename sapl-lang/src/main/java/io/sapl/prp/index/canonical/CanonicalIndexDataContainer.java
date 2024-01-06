/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import io.sapl.grammar.sapl.SAPL;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CanonicalIndexDataContainer {

    private final Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    private final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas;

    @Getter
    private final ImmutableList<Predicate> predicateOrder;

    private final List<Set<DisjunctiveFormula>> relatedFormulas;

    private final Map<DisjunctiveFormula, Bitmask> relatedCandidates;

    private final Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;

    private final int[] numberOfLiteralsInConjunction;

    private final int[] numberOfFormulasWithConjunction;

    @Getter
    private final int numberOfConjunctions;

    public CanonicalIndexDataContainer(Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
            Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas, Collection<Predicate> predicateOrder,
            List<Set<DisjunctiveFormula>> relatedFormulas, Map<DisjunctiveFormula, Bitmask> relatedCandidates,
            Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction, int[] numberOfLiteralsInConjunction,
            int[] numberOfFormulasWithConjunction) {

        this(formulaToDocuments, clauseToFormulas, ImmutableList.copyOf(predicateOrder), relatedFormulas,
                relatedCandidates, conjunctionsInFormulasReferencingConjunction, numberOfLiteralsInConjunction,
                numberOfFormulasWithConjunction, numberOfLiteralsInConjunction.length);
    }

    public CanonicalIndexDataContainer(Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
            Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas, ImmutableList<Predicate> predicateOrder,
            List<Set<DisjunctiveFormula>> relatedFormulas, Map<DisjunctiveFormula, Bitmask> relatedCandidates,
            Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction, int[] numberOfLiteralsInConjunction,
            int[] numberOfFormulasWithConjunction, int numberOfConjunctions) {
        this.formulaToDocuments                           = formulaToDocuments;
        this.clauseToFormulas                             = clauseToFormulas;
        this.predicateOrder                               = predicateOrder;
        this.relatedFormulas                              = relatedFormulas;
        this.relatedCandidates                            = relatedCandidates;
        this.conjunctionsInFormulasReferencingConjunction = conjunctionsInFormulasReferencingConjunction;
        this.numberOfLiteralsInConjunction                = numberOfLiteralsInConjunction.clone();
        this.numberOfFormulasWithConjunction              = numberOfFormulasWithConjunction.clone();
        this.numberOfConjunctions                         = numberOfConjunctions;
    }

    public int getNumberOfLiteralsInConjunction(int conjunctionIndex) {
        return numberOfLiteralsInConjunction[conjunctionIndex];
    }

    public int getNumberOfFormulasWithConjunction(int conjunctionIndex) {
        return numberOfFormulasWithConjunction[conjunctionIndex];
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
