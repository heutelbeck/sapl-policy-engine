package de.ftk.saplindexingtest;

import de.ftk.saplindexingtest.Algorithms.Conjunction.MTuple;
import io.sapl.api.pdp.AuthorizationSubscription;

import java.util.*;
import java.util.function.Function;


public class Algorithms {

    Set<Formula> F = new HashSet<>();
    Set<Conjunction> C = new HashSet<>();
    Set<Literal> L = new HashSet<>();
    Set<Predicate> P = new HashSet<>();

    Map<Conjunction, Integer> numberOfLiteralsInConjunction = new HashMap<>();
    Map<Conjunction, Integer> numberOfFormulasWithConjunction = new HashMap<>();

    Map<Conjunction, Set<MTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>();
    Map<Predicate, Set<Conjunction>> predicateInConjunction = new HashMap<>();
    Map<Conjunction, Set<Formula>> predicateInFormula = new HashMap<>();

    //    Data: F, C, L, P,
    //    numberOfLiteralsInConjuction, numberOfFormulasWithConjunction, conjunctionsInFormulasReferencingConjunction, predicateInConjunction,
    //    predicateInFormula<
    public Set<Formula> countAndEliminate(AuthorizationSubscription subscription) {
        Set<Conjunction> candidateSet = C;
        Map<Conjunction, Integer> trueLiteralsOfConjunction = new HashMap<>();
        Map<Conjunction, Integer> eliminatedFormulasWithConjunction = new HashMap<>();
        Set<Formula> resultSet = new HashSet<>();

        for (Predicate predicate : P) {
            if (!isReferenced(predicate, candidateSet)) continue;

            boolean b = predicate.isSatisfied(subscription);
            Set<Conjunction> satisfiableCandidates = findSatisfiableCandidates(candidateSet, predicate, b, trueLiteralsOfConjunction);
            resultSet.addAll(fetchFormulas(satisfiableCandidates));
            Set<Conjunction> unsatisfiableCandidates = findUnsatisfiableCandidates(predicate, b);
            Set<Conjunction> orphanedCandidates = findOrphanedCandidates(satisfiableCandidates, eliminatedFormulasWithConjunction);

            candidateSet.removeAll(satisfiableCandidates);
            candidateSet.removeAll(unsatisfiableCandidates);
            candidateSet.removeAll(orphanedCandidates);
        }

        return resultSet;
    }

    private Set<Conjunction> findOrphanedCandidates(Set<Conjunction> satisfiableCandidates, Map<Conjunction, Integer> eliminatedFormulasWithConjunction) {
        Set<Conjunction> orphanedCandidates = new HashSet<>();

        for (Conjunction satisfiableCandidate : satisfiableCandidates) {
            for (MTuple mTuple : conjunctionsInFormulasReferencingConjunction.get(satisfiableCandidate)) {
                eliminatedFormulasWithConjunction
                        .compute(mTuple.conjunction,
                                (conjunction, integer) -> integer == null ? mTuple.numbersOfFormulasWithConjunction :
                                        integer + mTuple.numbersOfFormulasWithConjunction);

                if (eliminatedFormulasWithConjunction.get(mTuple.conjunction).equals(numberOfFormulasWithConjunction
                        .get(mTuple.conjunction)))
                    orphanedCandidates.add(mTuple.conjunction);
            }
        }

        return orphanedCandidates;
    }

    private Set<Conjunction> findUnsatisfiableCandidates(Predicate predicate, boolean b) {
        return b ? predicate.falseForTruePredicate : predicate.falseForFalsePredicate;
    }

    private Set<Formula> fetchFormulas(Set<Conjunction> satisfiedConjunctions) {
        Set<Formula> formulas = new HashSet<>();

        for (Conjunction conjunction : satisfiedConjunctions) {
            formulas.addAll(predicateInFormula.get(conjunction));
        }

        return formulas;
    }

    private Set<Conjunction> findSatisfiableCandidates(Set<Conjunction> candidateSet, Predicate predicate, boolean b, Map<Conjunction, Integer> trueLiteralsOfConjunction) {
        //TODO
        return Collections.emptySet();
    }

    private boolean isReferenced(Predicate predicate, Set<Conjunction> candidateSet) {
        return candidateSet.parallelStream().anyMatch(conjunction -> conjunction.containsPredicate(predicate));
    }


    public static class Predicate {

        Function<AuthorizationSubscription, Boolean> function;

        Set<Conjunction> falseForTruePredicate = new HashSet<>();
        Set<Conjunction> falseForFalsePredicate = new HashSet<>();

        boolean isSatisfied(AuthorizationSubscription subscription) {
            return function.apply(subscription);
        }
    }

    public static class Literal {
        Predicate predicate;
        boolean isNegated;

        boolean isSatisfied(AuthorizationSubscription subscription) {
            return isNegated != predicate.isSatisfied(subscription);
        }

    }

    public static class Conjunction {
        Set<Literal> literals = new HashSet<>();

        Set<Formula> formulasReferencing = new HashSet<>();

        boolean containsPredicate(Predicate predicate) {
            return literals.parallelStream().anyMatch(literal -> literal.predicate == predicate);
        }


        // A conjunction c is fulfilled for a subscription s, if and only ifallliteralsevaluatetotrue for s
        boolean isSatisfied(AuthorizationSubscription subscription) {
            return literals.stream().allMatch(l -> l.isSatisfied(subscription));
        }

        public static class MTuple {
            Conjunction conjunction;
            int numbersOfFormulasWithConjunction;

            public MTuple(Conjunction c) {
                this.conjunction = c;
                this.numbersOfFormulasWithConjunction = c.formulasReferencing.size();
            }
        }
    }

    public static class Formula {
        Set<Conjunction> conjunctions = new HashSet<>();

        // A formula f is a subset of the available conjunctions, and is satisfied when any conjunction is satisfied.
        boolean isSatisfied(AuthorizationSubscription subscription) {
            return conjunctions.stream().anyMatch(c -> c.isSatisfied(subscription));
        }
    }

}
