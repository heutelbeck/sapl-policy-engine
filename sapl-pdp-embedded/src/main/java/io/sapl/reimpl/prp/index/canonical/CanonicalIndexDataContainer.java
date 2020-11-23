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
package io.sapl.reimpl.prp.index.canonical;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.improved.CTuple;
import io.sapl.prp.inmemory.indexed.improved.Predicate;
import lombok.Value;

@Value
public class CanonicalIndexDataContainer {

    Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas;

    List<Predicate> predicateOrder;

    List<Set<DisjunctiveFormula>> relatedFormulas;

    Map<DisjunctiveFormula, Bitmask> relatedCandidates;

    Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;

    int[] numberOfLiteralsInConjunction;

    int[] numberOfFormulasWithConjunction;


    public int[] getNumberOfLiteralsInConjunction() {
        return numberOfLiteralsInConjunction.clone();
    }

    public int[] getNumberOfFormulasWithConjunction() {
        return numberOfFormulasWithConjunction.clone();
    }


    public static CanonicalIndexDataContainer createEmptyContainer() {
        return new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), new int[0], new int[0]);
    }
}
