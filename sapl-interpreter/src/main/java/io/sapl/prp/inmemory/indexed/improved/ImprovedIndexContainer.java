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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.IndexContainer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

//@Slf4j
//@RequiredArgsConstructor
public class ImprovedIndexContainer implements IndexContainer {

    private final boolean abortOnError;

    private final Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    private final List<Predicate> predicateOrder;

    private final List<Set<DisjunctiveFormula>> relatedFormulas;

    private final Map<DisjunctiveFormula, Bitmask> relatedCandidates;

    //    private final Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;
    private final Map<Integer, long[]> conjunctionsInFormulasReferencingConjunction;

    private final int[] numberOfLiteralsInConjunction;

    private final int[] numberOfFormulasWithConjunction;


    public ImprovedIndexContainer(boolean abortOnError, Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
                                  List<Predicate> predicateOrder, List<Set<DisjunctiveFormula>> relatedFormulas,
                                  Map<DisjunctiveFormula, Bitmask> relatedCandidates,
//                                  Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction,
                                  Map<Integer, long[]> conjunctionsInFormulasReferencingConjunction,
                                  int[] numberOfLiteralsInConjunction, int[] numberOfFormulasWithConjunction) {
        this.abortOnError = abortOnError;
        this.formulaToDocuments = ImmutableMap.copyOf(formulaToDocuments);
        this.predicateOrder = ImmutableList.copyOf(predicateOrder);
        this.relatedFormulas = ImmutableList.copyOf(relatedFormulas);
        this.relatedCandidates = ImmutableMap.copyOf(relatedCandidates);
//        this.conjunctionsInFormulasReferencingConjunction = ImmutableMap
//                .copyOf(conjunctionsInFormulasReferencingConjunction);
        this.conjunctionsInFormulasReferencingConjunction = conjunctionsInFormulasReferencingConjunction;
        this.numberOfLiteralsInConjunction = numberOfLiteralsInConjunction;
        this.numberOfFormulasWithConjunction = numberOfFormulasWithConjunction;
    }

    @Override
    public PolicyRetrievalResult match(final FunctionContext functionCtx, final VariableContext variableCtx) {
        Set<DisjunctiveFormula> result = new HashSet<>();
        boolean errorOccurred = false;

        Bitmask candidates = new Bitmask();
        candidates.set(0, numberOfLiteralsInConjunction.length);

        int[] trueLiteralsOfConjunction = new int[numberOfLiteralsInConjunction.length];
        int[] eliminatedFormulasWithConjunction = new int[numberOfLiteralsInConjunction.length];

        List<Predicate> referencedPredicates = predicateOrder.stream()
                .filter(predicate -> isReferenced(predicate, candidates))
                .collect(Collectors.toList());

        for (Predicate predicate : referencedPredicates) {

            Optional<Boolean> outcome = predicate.evaluate(functionCtx, variableCtx);
            if (!outcome.isPresent()) {
                if (abortOnError) {
                    return new PolicyRetrievalResult(fetchPolicies(result), true);
                } else {
                    removeCandidatesRelatedToPredicate(predicate, candidates);
                    errorOccurred = true;
                    continue;
                }
            }
            boolean evaluationResult = outcome.get();

            Bitmask satisfiableCandidates = findSatisfiableCandidates(candidates, predicate, evaluationResult,
                    trueLiteralsOfConjunction);
            result.addAll(fetchFormulas(satisfiableCandidates));
            Bitmask unsatisfiableCandidates = findUnsatisfiableCandidates(candidates, predicate, evaluationResult);
            Bitmask orphanedCandidates = findOrphanedCandidates(candidates, satisfiableCandidates,
                    eliminatedFormulasWithConjunction);

            eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);

        }

        return new PolicyRetrievalResult(fetchPolicies(result), errorOccurred);
    }

    private void removeCandidatesRelatedToPredicate(final Predicate predicate, Bitmask candidates) {
        Bitmask affectedCandidates = findRelatedCandidates(predicate);
        candidates.andNot(affectedCandidates);
    }

    protected Bitmask findRelatedCandidates(final Predicate predicate) {
        Bitmask result = new Bitmask();
        Set<DisjunctiveFormula> formulasContainingVariable = fetchFormulas(predicate.getConjunctions());
        for (DisjunctiveFormula formula : formulasContainingVariable) {
            result.or(relatedCandidates.get(formula));
        }
        return result;
    }

    private Bitmask findOrphanedCandidates(final Bitmask candidates, final Bitmask satisfiableCandidates,
                                           int[] eliminatedFormulasWithConjunction) {
        Bitmask result = new Bitmask();

        satisfiableCandidates.forEachSetBit(index -> {
//            Set<CTuple> cTuples = conjunctionsInFormulasReferencingConjunction.get(index);
            long[] cTupleArray = conjunctionsInFormulasReferencingConjunction.get(index);

//            for (CTuple cTuple : cTuples) {
            for (int clauseIndex = 0; clauseIndex < cTupleArray.length; clauseIndex++) {
//                if (!candidates.isSet(cTuple.getCI())) continue;
                if (!candidates.isSet(clauseIndex)) continue;
//                eliminatedFormulasWithConjunction[cTuple.getCI()] += cTuple.getN();
                eliminatedFormulasWithConjunction[clauseIndex] += cTupleArray[clauseIndex];

                // if all formular of conjunction have been eliminated
//                if (eliminatedFormulasWithConjunction[cTuple.getCI()] == numberOfFormulasWithConjunction[cTuple
//                        .getCI()])
                if (eliminatedFormulasWithConjunction[clauseIndex] == numberOfFormulasWithConjunction[clauseIndex])
//                    result.set(cTuple.getCI());
                    result.set(clauseIndex);
            }
        });


        return result;
    }

    protected void eliminateCandidates(final Bitmask candidates, final Bitmask unsatisfiableCandidates,
                                       final Bitmask satisfiableCandidates, final Bitmask orphanedCandidates) {
        candidates.andNot(unsatisfiableCandidates);
        candidates.andNot(satisfiableCandidates);
        candidates.andNot(orphanedCandidates);
    }

    protected Set<DisjunctiveFormula> fetchFormulas(final Bitmask satisfiableCandidates) {
        final Set<DisjunctiveFormula> result = new HashSet<>();
        satisfiableCandidates.forEachSetBit(index -> result.addAll(relatedFormulas.get(index)));
        return result;
    }

    private Bitmask findSatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
                                              final boolean evaluationResult, final int[] trueLiteralsOfConjunction) {
        Bitmask result = new Bitmask();
        // calling method with negated evaluation result will return satisfied clauses
        Bitmask satisfiableCandidates = findUnsatisfiableCandidates(candidates, predicate, !evaluationResult);

        satisfiableCandidates.forEachSetBit(index -> {
            //increment number of true literals
            trueLiteralsOfConjunction[index] += 1;
            //if all literals in conjunction are true, add conjunction to result
            if (trueLiteralsOfConjunction[index] == numberOfLiteralsInConjunction[index]) result.set(index);
        });

        return result;
    }

    protected boolean isReferenced(final Predicate predicate, final Bitmask candidates) {
        return predicate.getConjunctions().intersects(candidates);
    }

    protected Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas) {
        return formulas.parallelStream().map(formulaToDocuments::get)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    protected Bitmask findUnsatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
                                                  final boolean predicateEvaluationResult) {
        Bitmask result = new Bitmask(candidates);
        if (predicateEvaluationResult) {
            result.and(predicate.getFalseForTruePredicate());
        } else {
            result.and(predicate.getFalseForFalsePredicate());
        }
        return result;
    }

}
