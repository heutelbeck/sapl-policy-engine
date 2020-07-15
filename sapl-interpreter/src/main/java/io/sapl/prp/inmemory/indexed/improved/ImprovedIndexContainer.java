package io.sapl.prp.inmemory.indexed.improved;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.IndexContainer;
import io.sapl.prp.inmemory.indexed.Literal;
import io.sapl.prp.inmemory.indexed.Variable;
import io.sapl.prp.inmemory.indexed.VariableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ImprovedIndexContainer implements IndexContainer {

    private final Set<Bool> boolSet;

    private final Map<ConjunctiveClause, Integer> numberOfFormulasWithConjunction;

    private final Map<ConjunctiveClause, Set<MTuple>> conjunctionsInFormulasReferencingConjunction;
    private final Map<ConjunctiveClause, Set<DisjunctiveFormula>> conjunctiveClauseInFormula;

    private final Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    private final Map<Bool, VariableInfo> variableInfo;


    @Override
    public PolicyRetrievalResult match(FunctionContext functionCtx, VariableContext variableCtx) {
        //create new candidate set, that can be modified
        Set<ConjunctiveClause> candidateSet = new HashSet<>(conjunctiveClauseInFormula.keySet());
        Bitmask candidatesMask = new Bitmask();
        // set all bits to true
        candidatesMask.set(0, candidateSet.size());

        LOGGER.info("number of candidates: {}, number of predicates: {}", candidatesMask.numberOfBitsSet(), boolSet
                .size());

        Map<ConjunctiveClause, Integer> trueLiteralsOfConjunction = new HashMap<>();
        Map<ConjunctiveClause, Integer> eliminatedFormulasWithConjunction = new HashMap<>();
        Set<DisjunctiveFormula> resultSet = new HashSet<>();
        boolean errorOccurred = false;

//        try {
        for (VariableInfo variableInfo : variableInfo.values()) {
//            for (Bool predicate : boolSet) {
            Bool predicate = variableInfo.getVariable().getBool();
            LOGGER.info("candidates remaining: {}", candidateSet.size());
            if (!isReferenced(predicate, candidateSet)) continue;

            Optional<Boolean> outcome = isPredicateSatisfied(variableInfo
                    .getVariable(), functionCtx, variableCtx);
            if (!outcome.isPresent()) {
                //TODO error handling
                LOGGER.info("missing context for predicate {}", predicate);
                Set<ConjunctiveClause> missingContextCandidates = getCandidatesContainingPredicate(predicate);
                candidateSet.removeAll(missingContextCandidates);
                continue;
            }

            boolean predicateEvaluationResult = outcome.get();

            Set<ConjunctiveClause> satisfiableCandidates = findSatisfiableCandidates(predicate, predicateEvaluationResult, trueLiteralsOfConjunction);
            resultSet.addAll(fetchFormulas(satisfiableCandidates));
            Set<ConjunctiveClause> unsatisfiableCandidates = findUnsatisfiableCandidates(predicate, predicateEvaluationResult);
            Set<ConjunctiveClause> orphanedCandidates = findOrphanedCandidates(satisfiableCandidates, eliminatedFormulasWithConjunction);

            candidateSet.removeAll(satisfiableCandidates);
            candidateSet.removeAll(unsatisfiableCandidates);
            candidateSet.removeAll(orphanedCandidates);
        }
//        } catch (PolicyEvaluationException e) {
//            LOGGER.error("policy evaluation failed", e);
//            errorOccurred = true;
//        }

        return new PolicyRetrievalResult(fetchPolicies(resultSet), errorOccurred);
    }

    private Set<ConjunctiveClause> getCandidatesContainingPredicate(Bool predicate) {
        VariableInfo info = this.variableInfo.get(predicate);
        HashSet<ConjunctiveClause> clauses = new HashSet<>(info.getSetOfUnsatisfiableClausesIfFalse());
        clauses.addAll(info.getSetOfUnsatisfiableClausesIfTrue());

        return clauses;
    }

    private Optional<Boolean> isPredicateSatisfied(Variable predicate, FunctionContext functionCtx,
                                                   VariableContext variableCtx) {
        return predicate.evaluate(functionCtx, variableCtx);
    }

    protected Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas) {
        return formulas.stream().map(formulaToDocuments::get)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }


    private Set<ConjunctiveClause> findOrphanedCandidates(Set<ConjunctiveClause> satisfiableCandidates,
                                                          Map<ConjunctiveClause, Integer> eliminatedFormulasWithConjunction) {
        Set<ConjunctiveClause> orphanedCandidates = new HashSet<>();

        for (ConjunctiveClause c : satisfiableCandidates) {
            for (MTuple mTuple : conjunctionsInFormulasReferencingConjunction.get(c)) {
                eliminatedFormulasWithConjunction.merge(mTuple.getConjunctiveClause(), mTuple
                        .getNumbersOfFormularsWithConjunctiveClaus(), Integer::sum);

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
            trueLiteralsOfConjunction.merge(satisfiableCandidate, 1, Integer::sum);

            if (trueLiteralsOfConjunction.get(satisfiableCandidate)
                    .equals(satisfiableCandidate.size())) {
                LOGGER.info("conjunction {} is satisfied", satisfiableCandidate);
                resultSet.add(satisfiableCandidate);
            }
        }

        return resultSet;
    }

    private boolean isReferenced(Bool predicate, Set<ConjunctiveClause> candidateSet) {
        return candidateSet.parallelStream()
                .anyMatch(conjunction -> conjunctionContainsPredicate(conjunction, predicate));
    }

    private boolean conjunctionContainsPredicate(ConjunctiveClause conjunctiveClause, Bool predicate) {
        return conjunctiveClause.getLiterals().stream()
                .map(Literal::getBool).collect(Collectors.toSet()).contains(predicate);
    }

}
