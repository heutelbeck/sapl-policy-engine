package io.sapl.reimpl.prp.index.canonical;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.Literal;
import io.sapl.prp.inmemory.indexed.improved.CTuple;
import io.sapl.prp.inmemory.indexed.improved.Predicate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class CanonicalIndexAlgorithmTest {

    @Test
    public void test_or_bitmask() {
        var b1 = new Bitmask();
        var b2 = new Bitmask();

        b1.set(0, 3);
        b2.set(2, 6);

        var b3 = CanonicalIndexAlgorithm.orBitMask(b1, b2);

        assertThat(b3).isNotNull();
        assertThat(b3.isSet(0)).isTrue();
        assertThat(b3.isSet(2)).isTrue();
        assertThat(b3.isSet(4)).isTrue();


        assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(null, b2));
        assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(b1, null));
        assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(null, null));
    }

    @Test
    public void test_is_referenced() {
        var candidates = new Bitmask();
        var predicate = new Predicate(new Bool(true));

        assertThat(CanonicalIndexAlgorithm.isPredicateReferencedInCandidates(predicate, candidates)).isFalse();

        predicate.getConjunctions().set(5);
        assertThat(CanonicalIndexAlgorithm.isPredicateReferencedInCandidates(predicate, candidates)).isFalse();

        candidates.set(5);
        assertThat(CanonicalIndexAlgorithm.isPredicateReferencedInCandidates(predicate, candidates)).isTrue();
    }

    @Test
    public void test_find_unsatisfiable_candidates() {
        var predicate = new Predicate(new Bool(true));
        var candidates = new Bitmask();

        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true).numberOfBitsSet()).isEqualTo(0);
        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false).numberOfBitsSet()).isEqualTo(0);

        predicate.getFalseForTruePredicate().set(0, 3);
        predicate.getFalseForFalsePredicate().set(3, 7);

        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true).numberOfBitsSet()).isEqualTo(0);
        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 7);

        var uc1 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true);
        assertThat(uc1.numberOfBitsSet()).isEqualTo(3);
        assertThat(uc1.isSet(2)).isTrue();
        assertThat(uc1.isSet(3)).isFalse();

        var uc2 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false);
        assertThat(uc2.numberOfBitsSet()).isEqualTo(4);
        assertThat(uc2.isSet(3)).isTrue();
        assertThat(uc2.isSet(2)).isFalse();
    }

    @Test
    public void test_eliminate_candidates() {
        var candidates = new Bitmask();
        var satisfiableCandidates = new Bitmask();
        var unsatisfiableCandidates = new Bitmask();
        var orphanedCandidates = new Bitmask();

        CanonicalIndexAlgorithm
                .reduceCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 5);

        CanonicalIndexAlgorithm
                .reduceCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(5);

        satisfiableCandidates.set(4);
        CanonicalIndexAlgorithm
                .reduceCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(4);
        assertThat(candidates.isSet(4)).isFalse();

        unsatisfiableCandidates.set(3);
        CanonicalIndexAlgorithm
                .reduceCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(3);
        assertThat(candidates.isSet(3)).isFalse();

        orphanedCandidates.set(0);
        CanonicalIndexAlgorithm
                .reduceCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(2);
        assertThat(candidates.isSet(0)).isFalse();

        assertThat(candidates.isSet(1)).isTrue();
    }

    @Test
    public void test_remove_candidates_related_to_predicate() {
        var predicate = new Predicate(new Bool(true));
        var matchingCtx = new CanonicalIndexMatchingContext(CanonicalIndexDataContainer.createEmptyContainer(),
                new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                        Collections.emptyMap()));

        var candidates = matchingCtx.getCandidatesMask();

        candidates.set(0, 5);
        CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(5);

        predicate.getConjunctions().set(3);
        CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
        assertThat(candidates.numberOfBitsSet()).isEqualTo(4);
        assertThat(candidates.isSet(3)).isFalse();
    }

    @Test
    public void test_fetch_formulas() {
        var satisfiableCandidates = new Bitmask();
        List<Set<DisjunctiveFormula>> relatedFormulas = new ArrayList<>();

        assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, relatedFormulas)).isEmpty();

        satisfiableCandidates.set(1);
        satisfiableCandidates.set(2);
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, relatedFormulas));


        Set<DisjunctiveFormula> set0 = new HashSet<>();

        Set<DisjunctiveFormula> set1 = new HashSet<>();
        set1.add(new DisjunctiveFormula(createDummyClauseList(1)));
        set1.add(new DisjunctiveFormula(createDummyClauseList(2)));

        Set<DisjunctiveFormula> set2 = new HashSet<>();
        set2.add(new DisjunctiveFormula(createDummyClauseList(3)));
        set2.add(new DisjunctiveFormula(createDummyClauseList(4)));
        set2.add(new DisjunctiveFormula(createDummyClauseList(5)));

        relatedFormulas.add(0, set0);
        relatedFormulas.add(1, set1);
        relatedFormulas.add(2, set2);

        Set<DisjunctiveFormula> formulas = CanonicalIndexAlgorithm
                .fetchFormulas(satisfiableCandidates, relatedFormulas);
        assertThat(formulas).isNotEmpty();
        assertThat(formulas.size()).isEqualTo(5);

        Bitmask satisfiableCandidates2 = new Bitmask();
        assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates2, relatedFormulas)).isEmpty();
    }

    @Test
    public void test_find_satisfiable_candidates() {
        var candidates = new Bitmask();
        var predicate = new Predicate(new Bool(true));
        int[] trueLiteralsOfConjunction = new int[]{0, 0, 0};
        int[] numberOfLiteralsInConjunction = new int[]{1, 1, 2};

        assertThat(CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, false,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 3);
        predicate.getFalseForTruePredicate().set(0, 1);
        predicate.getFalseForFalsePredicate().set(1, 3);

        //true -> {1} satisfiableCandidates
        var satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, true,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        assertThat(satisfiableCandidates.isSet(0)).isFalse();
        assertThat(satisfiableCandidates.isSet(1)).isTrue();
        assertThat(satisfiableCandidates.isSet(2)).isFalse();

        //calling the method again without clearing the array trueLiteralsOfConjunction, so number of true literals
        // is "2" for the third candidate (all literals are true)
        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, true,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        assertThat(satisfiableCandidates.isSet(2)).isTrue();


        trueLiteralsOfConjunction = new int[]{0, 0, 0};
        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, false,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        assertThat(satisfiableCandidates.isSet(0)).isTrue();
        assertThat(satisfiableCandidates.isSet(1)).isFalse();
        assertThat(satisfiableCandidates.isSet(2)).isFalse();
    }

    @Test
    public void test_find_orphaned_candidates() {
        var candidates = new Bitmask();
        var satisfiableCandidates = new Bitmask();
        int[] eliminatedFormulasWithConjunction = new int[]{};
        int[] numberOfFormulasWithConjunction = new int[]{};
        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>();

        assertThat(CanonicalIndexAlgorithm.findOrphanedCandidates(candidates, satisfiableCandidates,
                eliminatedFormulasWithConjunction, conjunctionsInFormulasReferencingConjunction,
                numberOfFormulasWithConjunction).numberOfBitsSet()).isEqualTo(0);
    }

    private List<ConjunctiveClause> createDummyClauseList(int numberOfLiterals) {
        List<Literal> literals = new ArrayList<>();
        for (int i = 0; i < numberOfLiterals; i++) {
            literals.add(new Literal(new Bool(false)));
        }

        return Arrays.asList(new ConjunctiveClause(literals));
    }
}
