package io.sapl.prp.inmemory.indexed.improved;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ImprovedIndexContainer implements IndexContainer {

    private final Set<ConjunctiveClause> candidateSet;
    private final Set<Bool> boolSet;

    //replaced by calling size() method of clause
    //    private final Map<ConjunctiveClause, Integer> numberOfLiteralsInConjunction;
    private final Map<ConjunctiveClause, Integer> numberOfFormulasWithConjunction;

    private final Map<ConjunctiveClause, Set<MTuple>> conjunctionsInFormulasReferencingConjunction;
    private final Map<ConjunctiveClause, Set<DisjunctiveFormula>> conjunctiveClauseInFormula;

    private final Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    private final Map<Bool, VariableInfo> variableInfo;


    @Override
    public PolicyRetrievalResult match(FunctionContext functionCtx, VariableContext variableCtx) {
        Map<ConjunctiveClause, Integer> trueLiteralsOfConjunction = new HashMap<>();
        Map<ConjunctiveClause, Integer> eliminatedFormulasWithConjunction = new HashMap<>();
        Set<DisjunctiveFormula> resultSet = new HashSet<>();
        boolean errorOccurred = false;

        try {
            for (Bool predicate : boolSet) {
                if (!isReferenced(predicate)) continue;

                boolean predicateEvaluationResult = isPredicateSatisfied(predicate, functionCtx, variableCtx);
                Set<ConjunctiveClause> satisfiableCandidates = findSatisfiableCandidates(predicate, predicateEvaluationResult, trueLiteralsOfConjunction);
                resultSet.addAll(fetchFormulas(satisfiableCandidates));
                Set<ConjunctiveClause> unsatisfiableCandidates = findUnsatisfiableCandidates(predicate, predicateEvaluationResult);
                Set<ConjunctiveClause> orphanedCandidates = findOrphanedCandidates(satisfiableCandidates, eliminatedFormulasWithConjunction);

                candidateSet.removeAll(satisfiableCandidates);
                candidateSet.removeAll(unsatisfiableCandidates);
                candidateSet.removeAll(orphanedCandidates);
            }
        } catch (PolicyEvaluationException e) {
            LOGGER.error("policy evaluation failed", e);
            errorOccurred = true;
        }

        return new PolicyRetrievalResult(fetchPolicies(resultSet), errorOccurred);
    }

    private boolean isPredicateSatisfied(Bool predicate, FunctionContext functionCtx,
                                         VariableContext variableCtx) throws PolicyEvaluationException {
        return predicate.evaluate(functionCtx, variableCtx);
    }

    protected Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas) {
        return formulas.stream().map(formulaToDocuments::get)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }


    private Set<ConjunctiveClause> findOrphanedCandidates(Set<ConjunctiveClause> satisfiableCandidates,
                                                          Map<ConjunctiveClause, Integer> eliminatedFormulasWithConjunction) {
        Set<ConjunctiveClause> orphanedCandidates = new HashSet<>();

        for (ConjunctiveClause satisfiableCandidate : satisfiableCandidates) {
            for (MTuple mTuple : conjunctionsInFormulasReferencingConjunction.get(satisfiableCandidate)) {
                eliminatedFormulasWithConjunction
                        .compute(mTuple.getConjunctiveClause(),
                                (conjunction, integer) -> integer == null ? mTuple
                                        .getNumbersOfFormularsWithConjunctiveClaus() :
                                        integer + mTuple.getNumbersOfFormularsWithConjunctiveClaus());

                if (eliminatedFormulasWithConjunction.get(mTuple.getConjunctiveClause())
                        .equals(numberOfFormulasWithConjunction
                                .get(mTuple.getConjunctiveClause())))
                    orphanedCandidates.add(mTuple.getConjunctiveClause());
            }
        }

        return orphanedCandidates;
    }

    private Set<ConjunctiveClause> findUnsatisfiableCandidates(Bool predicate, boolean predicateEvaluationResult) {
        return predicateEvaluationResult ? this.variableInfo.get(predicate).getSetOfUnsatisfiableClausesIfTrue() :
                this.variableInfo.get(predicate).getSetOfUnsatisfiableClausesIfFalse();
    }

    private Set<DisjunctiveFormula> fetchFormulas(Set<ConjunctiveClause> satisfiedConjunctions) {
        return satisfiedConjunctions.stream().map(conjunctiveClauseInFormula::get).flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private Set<ConjunctiveClause> findSatisfiableCandidates(Bool predicate, boolean predicateEvaluationResult,
                                                             Map<ConjunctiveClause, Integer> trueLiteralsOfConjunction) {
        // calling method with negated evaluation result will return satisfied clauses
        Set<ConjunctiveClause> satisfiableCandidates = findUnsatisfiableCandidates(predicate, !predicateEvaluationResult);
        Set<ConjunctiveClause> resultSet = new HashSet<>();

        for (ConjunctiveClause satisfiableCandidate : satisfiableCandidates) {
            trueLiteralsOfConjunction
                    .compute(satisfiableCandidate, (conjunction, integer) -> integer == null ? 1 : integer++);

            if (trueLiteralsOfConjunction.get(satisfiableCandidate)
                    .equals(satisfiableCandidate.size()))
                resultSet.add(satisfiableCandidate);
        }

        return resultSet;
    }

    private boolean isReferenced(Bool predicate) {
        return candidateSet.parallelStream()
                .anyMatch(conjunction -> conjunctionContainsPredicate(conjunction, predicate));
    }

    private boolean conjunctionContainsPredicate(ConjunctiveClause conjunctiveClause, Bool predicate) {
        return conjunctiveClause.getLiterals().stream()
                .map(Literal::getBool).collect(Collectors.toSet()).contains(predicate);
    }

}
