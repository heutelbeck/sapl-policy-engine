/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.IndexContainer;
import io.sapl.prp.inmemory.indexed.IndexCreationStrategy;
import io.sapl.prp.inmemory.indexed.Literal;

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

public class ImprovedIndexCreationStrategy implements IndexCreationStrategy {

    @Override
    public IndexContainer construct(final Map<String, SAPL> documents, final Map<String, DisjunctiveFormula> targets) {
        Map<String, SAPL> idToDocument = ImmutableMap.copyOf(documents);

        Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments = mapFormulaToDocuments(targets, idToDocument);
        Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas = mapClauseToFormulas(
                formulaToDocuments.keySet());

        Collection<PredicateInfo> predicateInfos = collectPredicateInfos(formulaToDocuments.keySet());
        List<Predicate> predicateOrder = createPredicateOrder(predicateInfos);

        BiMap<ConjunctiveClause, Integer> clauseToIndex = createCandidateOrder(predicateInfos);

        Map<Integer, Set<DisjunctiveFormula>> indexToTargets = mapIndexToFormulas(clauseToIndex, clauseToFormulas);

        Map<DisjunctiveFormula, Bitmask> relatedCandidates = mapFormulaToClauses(formulaToDocuments.keySet(),
                clauseToIndex);

        int[] numberOfLiteralsInConjunction = mapIndexToNumberOfLiteralsInConjunction(clauseToIndex.inverse());
        int[] numberOfFormulasWithConjunction = mapIndexToNumberOfFormulasWithConjunction(clauseToIndex
                .inverse(), clauseToFormulas);

        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction =
                getConjunctionReferenceMap(clauseToFormulas, clauseToIndex, relatedCandidates);

        List<Set<DisjunctiveFormula>> relatedFormulas = flattenIndexMap(indexToTargets);

        return new ImprovedIndexContainer(false, formulaToDocuments, predicateOrder,
                relatedFormulas, relatedCandidates, conjunctionsInFormulasReferencingConjunction,
                numberOfLiteralsInConjunction, numberOfFormulasWithConjunction);
    }

    private Map<Integer, Set<CTuple>> getConjunctionReferenceMap(Map<ConjunctiveClause,
            Set<DisjunctiveFormula>> clauseToFormulas, BiMap<ConjunctiveClause, Integer> clauseToIndex,
                                                                 Map<DisjunctiveFormula, Bitmask> formulaToClauses) {
        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>();
        for (Entry<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulaEntry : clauseToFormulas
                .entrySet()) {

            Integer clauseIndex = clauseToIndex.get(clauseToFormulaEntry.getKey());
            Set<DisjunctiveFormula> formulasContainingClause = clauseToFormulaEntry.getValue();
            Bitmask clausesInSameFormulas = new Bitmask();

            formulasContainingClause.forEach(formulaContainingClause -> clausesInSameFormulas
                    .or(formulaToClauses.get(formulaContainingClause)));
            clausesInSameFormulas.clear(clauseIndex);

            Set<CTuple> cTupleSet = new HashSet<>();
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
        Map<Bool, PredicateInfo> boolToPredicateInfo = new HashMap<>();
        Set<Bool> negativesGroupedByFormula = new HashSet<>();
        Set<Bool> positivesGroupedByFormula = new HashSet<>();

        for (DisjunctiveFormula formula : formulas) {
            negativesGroupedByFormula.clear();
            positivesGroupedByFormula.clear();
            for (ConjunctiveClause clause : formula.getClauses()) {
                List<Literal> literals = clause.getLiterals();
                final int sizeOfClause = literals.size();
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

    private void createPredicateInfo(final Literal literal, final ConjunctiveClause clause,
                                     final Map<Bool, PredicateInfo> boolToPredicateInfo, Set<Bool> negativesGroupedByFormula, Set<Bool> positivesGroupedByFormula, int sizeOfClause) {
        Bool bool = literal.getBool();
        PredicateInfo predicateInfo = boolToPredicateInfo
                .computeIfAbsent(bool, k -> new PredicateInfo(new Predicate(bool)));

        predicateInfo.addToClauseRelevanceList(1.0 / sizeOfClause);
        if (literal.isNegated()) {
            predicateInfo.addUnsatisfiableConjunctionIfTrue(clause);
            predicateInfo.incNumberOfNegatives();
            if (!negativesGroupedByFormula.contains(bool)) {
                negativesGroupedByFormula.add(bool);
                predicateInfo.incGroupedNumberOfNegatives();
            }
        } else {
            predicateInfo.addUnsatisfiableConjunctionIfFalse(clause);
            predicateInfo.incNumberOfPositives();
            if (!positivesGroupedByFormula.contains(bool)) {
                positivesGroupedByFormula.add(bool);
                predicateInfo.incGroupedNumberOfPositives();
            }
        }
    }


    private BiMap<ConjunctiveClause, Integer> createCandidateOrder(final Collection<PredicateInfo> data) {
        BiMap<ConjunctiveClause, Integer> result = HashBiMap.create();
        int i = 0;
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


    private List<Predicate> createPredicateOrder(final Collection<PredicateInfo> data) {
        List<PredicateInfo> predicateInfos = new ArrayList<>(data);
        for (PredicateInfo predicateInfo : predicateInfos) {
            predicateInfo.setScore(createScore(predicateInfo));

        }
//        Collections.sort(predicateInfos, Collections.reverseOrder());

        return predicateInfos.stream().sorted(Collections.reverseOrder()).map(PredicateInfo::getPredicate)
                .collect(Collectors.toList());
    }


    private static double createScore(final PredicateInfo predicateInfo) {
        final double square = 2.0;
        final double groupedPositives = predicateInfo.getGroupedNumberOfPositives();
        final double groupedNegatives = predicateInfo.getGroupedNumberOfNegatives();
        final double relevance = predicateInfo.getRelevance();
        final double costs = 1.0;

        return Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives) / costs * (square
                - Math.pow((groupedPositives - groupedNegatives) / (groupedPositives + groupedNegatives), square));
    }

    private static <T> List<T> flattenIndexMap(final Map<Integer, T> data) {
        final List<T> result = new ArrayList<>(Collections.nCopies(data.size(), null));
        data.forEach(result::set);
        return result;
    }

    private Map<ConjunctiveClause, Set<DisjunctiveFormula>> mapClauseToFormulas(
            final Collection<DisjunctiveFormula> formulas) {
        Map<ConjunctiveClause, Set<DisjunctiveFormula>> result = new HashMap<>();

        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                Set<DisjunctiveFormula> set = result.computeIfAbsent(clause, k -> new HashSet<>());
                set.add(formula);
            }
        }
        return result;
    }

    private Map<DisjunctiveFormula, Bitmask> mapFormulaToClauses(final Collection<DisjunctiveFormula> formulas,
                                                                 final Map<ConjunctiveClause, Integer> clauseToIndex) {
        final Map<DisjunctiveFormula, Bitmask> result = new HashMap<>();
        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                Bitmask associatedIndexes = result.get(formula);
                if (associatedIndexes == null) {
                    associatedIndexes = new Bitmask();
                    result.put(formula, associatedIndexes);
                }
                Integer clauseIndex = clauseToIndex.get(clause);
                associatedIndexes.set(clauseIndex);
            }
        }
        return result;
    }

    private Map<DisjunctiveFormula, Set<SAPL>> mapFormulaToDocuments(
            final Map<String, DisjunctiveFormula> targets, final Map<String, SAPL> documents) {
        Map<DisjunctiveFormula, Set<SAPL>> result = new HashMap<>();

        for (Map.Entry<String, DisjunctiveFormula> entry : targets.entrySet()) {
            DisjunctiveFormula formula = entry.getValue();
            Set<SAPL> set = result.computeIfAbsent(formula, k -> new HashSet<>());
            set.add(documents.get(entry.getKey()));
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

    private Map<Integer, Set<DisjunctiveFormula>> mapIndexToFormulas(final Map<ConjunctiveClause, Integer> clauseToIndex,
                                                                     final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {

        return clauseToFormulas.entrySet().stream()
                .collect(Collectors.toMap(entry -> clauseToIndex.get(entry.getKey()), Entry::getValue));
    }
}
