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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.index.canonical.ordering.DefaultPredicateOrderStrategy;
import io.sapl.prp.index.canonical.ordering.PredicateOrderStrategy;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CanonicalIndexDataCreationStrategy {

    private final PredicateOrderStrategy predicateOrderStrategy;

    public CanonicalIndexDataCreationStrategy() {
        this(new DefaultPredicateOrderStrategy());
    }

    public CanonicalIndexDataContainer constructNew(final Map<String, SAPL> documents,
            final Map<String, DisjunctiveFormula> targets) {
        Map<String, SAPL> documentMap = new HashMap<>(documents);

        Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments = new HashMap<>(targets.size(), 1.0F);
        addNewFormulasToDocumentMapping(targets, documentMap, formulaToDocuments);

        Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas = new HashMap<>();
        addNewFormulasToClauseMapping(formulaToDocuments.keySet(), clauseToFormulas);

        return constructContainerWithOrder(formulaToDocuments, clauseToFormulas);
    }

    private CanonicalIndexDataContainer constructContainerWithOrder(
            Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
            Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {

        Collection<PredicateInfo> predicateInfos = collectPredicateInfos(formulaToDocuments.keySet());

        // manipulates Bitmask of Predicates stored in PredicateInfo as a side effect
        BiMap<ConjunctiveClause, Integer> clauseToIndex = createCandidateIndex(predicateInfos);

        // create predicate order using defined strategy. index will use this order
        List<Predicate> predicateOrder = predicateOrderStrategy.createPredicateOrder(predicateInfos);

        Map<Integer, Set<DisjunctiveFormula>> indexToTargets = mapIndexToFormulas(clauseToIndex, clauseToFormulas);

        Map<DisjunctiveFormula, Bitmask> relatedCandidates = mapFormulaToClauses(formulaToDocuments.keySet(),
                clauseToIndex);

        int[] numberOfLiteralsInConjunction   = mapIndexToNumberOfLiteralsInConjunction(clauseToIndex.inverse());
        int[] numberOfFormulasWithConjunction = mapIndexToNumberOfFormulasWithConjunction(clauseToIndex.inverse(),
                clauseToFormulas);

        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = getConjunctionReferenceMap(
                clauseToFormulas, clauseToIndex, relatedCandidates);

        List<Set<DisjunctiveFormula>> relatedFormulas = flattenIndexMap(indexToTargets);

        return new CanonicalIndexDataContainer(formulaToDocuments, clauseToFormulas, predicateOrder, relatedFormulas,
                relatedCandidates, conjunctionsInFormulasReferencingConjunction, numberOfLiteralsInConjunction,
                numberOfFormulasWithConjunction);
    }

    private void addNewFormulasToClauseMapping(final Collection<DisjunctiveFormula> formulas,
            Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulaMap) {

        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                Set<DisjunctiveFormula> set = clauseToFormulaMap.computeIfAbsent(clause, k -> new HashSet<>());
                set.add(formula);
            }
        }
    }

    private void addNewFormulasToDocumentMapping(final Map<String, DisjunctiveFormula> targets,
            final Map<String, SAPL> documents, Map<DisjunctiveFormula, Set<SAPL>> formulaToDocumentMap) {

        for (Entry<String, DisjunctiveFormula> entry : targets.entrySet()) {
            DisjunctiveFormula formula = entry.getValue();
            Set<SAPL>          set     = formulaToDocumentMap.computeIfAbsent(formula, k -> new HashSet<>());
            set.add(documents.get(entry.getKey()));
        }
    }

    private Map<Integer, Set<CTuple>> getConjunctionReferenceMap(
            Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas,
            BiMap<ConjunctiveClause, Integer> clauseToIndex, Map<DisjunctiveFormula, Bitmask> formulaToClauses) {

        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>(clauseToFormulas.size(),
                1.0F);

        for (Entry<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulaEntry : clauseToFormulas.entrySet()) {

            Integer                 clauseIndex              = clauseToIndex.get(clauseToFormulaEntry.getKey());
            Set<DisjunctiveFormula> formulasContainingClause = clauseToFormulaEntry.getValue();
            Bitmask                 clausesInSameFormulas    = new Bitmask();

            formulasContainingClause.forEach(
                    formulaContainingClause -> clausesInSameFormulas.or(formulaToClauses.get(formulaContainingClause)));
            clausesInSameFormulas.clear(clauseIndex);

            Set<CTuple> cTupleSet = new HashSet<>(clausesInSameFormulas.numberOfBitsSet());
            clausesInSameFormulas.forEachSetBit(relatedClauseIndex -> {
                long numberOfSharedFormulas = formulasContainingClause.stream().map(formulaToClauses::get)
                        .filter(bitmask -> bitmask.isSet(relatedClauseIndex)).count();

                cTupleSet.add(new CTuple(relatedClauseIndex, numberOfSharedFormulas));
            });

            conjunctionsInFormulasReferencingConjunction.put(clauseIndex, cTupleSet);
        }
        return conjunctionsInFormulasReferencingConjunction;
    }

    private Collection<PredicateInfo> collectPredicateInfos(Set<DisjunctiveFormula> formulas) {
        Map<Bool, PredicateInfo> boolToPredicateInfo       = new HashMap<>();
        Set<Bool>                negativesGroupedByFormula = new HashSet<>();
        Set<Bool>                positivesGroupedByFormula = new HashSet<>();

        for (DisjunctiveFormula formula : formulas) {
            negativesGroupedByFormula.clear();
            positivesGroupedByFormula.clear();
            for (ConjunctiveClause clause : formula.getClauses()) {
                List<Literal> literals     = clause.getLiterals();
                final int     sizeOfClause = literals.size();
                for (Literal literal : clause.getLiterals()) {
                    createPredicateInfo(literal, clause, boolToPredicateInfo, negativesGroupedByFormula,
                            positivesGroupedByFormula, sizeOfClause);
                }
            }
        }

        for (PredicateInfo predicateInfo : boolToPredicateInfo.values()) {
            double sum = predicateInfo.getClauseRelevanceList().stream().mapToDouble(Double::doubleValue).sum();
            sum /= predicateInfo.getNumberOfPositives() + predicateInfo.getNumberOfNegatives();
            predicateInfo.setRelevance(sum);
        }

        return boolToPredicateInfo.values();
    }

    void createPredicateInfo(final Literal literal, final ConjunctiveClause clause,
            final Map<Bool, PredicateInfo> boolToPredicateInfo, Set<Bool> negativesGroupedByFormula,
            Set<Bool> positivesGroupedByFormula, int sizeOfClause) {
        Bool          bool          = literal.getBool();
        PredicateInfo predicateInfo = boolToPredicateInfo.computeIfAbsent(bool,
                k -> new PredicateInfo(new Predicate(bool)));

        predicateInfo.addToClauseRelevanceList(1.0 / sizeOfClause);
        if (literal.isNegated()) {
            predicateInfo.addUnsatisfiableConjunctionIfTrue(clause);
            predicateInfo.incNumberOfNegatives();
            if (negativesGroupedByFormula.add(bool)) {
                predicateInfo.incGroupedNumberOfNegatives();
            }
        } else {
            predicateInfo.addUnsatisfiableConjunctionIfFalse(clause);
            predicateInfo.incNumberOfPositives();
            if (positivesGroupedByFormula.add(bool)) {
                predicateInfo.incGroupedNumberOfPositives();
            }
        }
    }

    private BiMap<ConjunctiveClause, Integer> createCandidateIndex(final Collection<PredicateInfo> data) {
        BiMap<ConjunctiveClause, Integer> result = HashBiMap.create();
        int                               i      = 0;
        for (PredicateInfo predicateInfo : data) {
            Predicate predicate = predicateInfo.getPredicate();

            for (ConjunctiveClause clause : predicateInfo.getUnsatisfiableConjunctionsIfTrue()) {
                Integer index = result.get(clause);
                if (index == null) {
                    index = i;
                    result.put(clause, index);
                    i += 1;
                }
                predicate.getFalseForTruePredicate().set(index);
                predicate.getConjunctions().set(index);
            }

            for (ConjunctiveClause clause : predicateInfo.getUnsatisfiableConjunctionsIfFalse()) {
                Integer index = result.get(clause);
                if (index == null) {
                    index = i;
                    result.put(clause, index);
                    i += 1;
                }
                predicate.getFalseForFalsePredicate().set(index);
                predicate.getConjunctions().set(index);
            }

        }
        return result;
    }

    private static <T> List<T> flattenIndexMap(final Map<Integer, T> data) {
        final List<T> result = new ArrayList<>(Collections.nCopies(data.size(), null));
        data.forEach(result::set);
        return result;
    }

    private Map<DisjunctiveFormula, Bitmask> mapFormulaToClauses(final Collection<DisjunctiveFormula> formulas,
            final Map<ConjunctiveClause, Integer> clauseToIndex) {
        final Map<DisjunctiveFormula, Bitmask> result = new HashMap<>(formulas.size(), 1.0F);
        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                Bitmask associatedIndexes = result.computeIfAbsent(formula, k -> new Bitmask());
                Integer clauseIndex       = clauseToIndex.get(clause);
                associatedIndexes.set(clauseIndex);
            }
        }
        return result;
    }

    private int[] mapIndexToNumberOfLiteralsInConjunction(final Map<Integer, ConjunctiveClause> indexToClause) {
        final int[] result = new int[indexToClause.size()];
        indexToClause.forEach((key, value) -> result[key] = value.size());
        return result;
    }

    private int[] mapIndexToNumberOfFormulasWithConjunction(final Map<Integer, ConjunctiveClause> indexToClause,
            final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {
        final int[] result = new int[indexToClause.size()];
        indexToClause.forEach((key, value) -> result[key] = clauseToFormulas.get(value).size());
        return result;
    }

    private Map<Integer, Set<DisjunctiveFormula>> mapIndexToFormulas(
            final Map<ConjunctiveClause, Integer> clauseToIndex,
            final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {

        return clauseToFormulas.entrySet().stream()
                .collect(Collectors.toMap(entry -> clauseToIndex.get(entry.getKey()), Entry::getValue));
    }

}
