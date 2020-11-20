package io.sapl.reimpl.prp.index.canonical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.Literal;
import io.sapl.prp.inmemory.indexed.improved.CTuple;
import io.sapl.prp.inmemory.indexed.improved.Predicate;

public class CanonicalIndexAlgorithmTest {

    @Test
    public void test_or_bitmask() {
        Bitmask b1 = new Bitmask();
        Bitmask b2 = new Bitmask();

        b1.set(0, 3);
        b2.set(2, 6);

        Bitmask b3 = CanonicalIndexAlgorithm.orBitMask(b1, b2);

        Assertions.assertThat(b3).isNotNull();
        Assertions.assertThat(b3.isSet(0)).isTrue();
        Assertions.assertThat(b3.isSet(2)).isTrue();
        Assertions.assertThat(b3.isSet(4)).isTrue();


        Assertions.assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(null, b2));
        Assertions.assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(b1, null));
        Assertions.assertThatNullPointerException().isThrownBy(() -> CanonicalIndexAlgorithm.orBitMask(null, null));
    }

    @Test
    public void test_is_referenced() {
        Bitmask candidates = new Bitmask();
        Predicate predicate = new Predicate(new Bool(true));

        Assertions.assertThat(CanonicalIndexAlgorithm.isReferenced(predicate, candidates)).isFalse();

        predicate.getConjunctions().set(5);
        Assertions.assertThat(CanonicalIndexAlgorithm.isReferenced(predicate, candidates)).isFalse();

        candidates.set(5);
        Assertions.assertThat(CanonicalIndexAlgorithm.isReferenced(predicate, candidates)).isTrue();
    }

    @Test
    public void test_find_unsatisfiable_candidates() {
        Predicate predicate = new Predicate(new Bool(true));
        Bitmask candidates = new Bitmask();

        Assertions.assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true).numberOfBitsSet()).isEqualTo(0);
        Assertions.assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false).numberOfBitsSet()).isEqualTo(0);

        predicate.getFalseForTruePredicate().set(0, 3);
        predicate.getFalseForFalsePredicate().set(3, 7);

        Assertions.assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true).numberOfBitsSet()).isEqualTo(0);
        Assertions.assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 7);

        Bitmask uc1 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, true);
        Assertions.assertThat(uc1.numberOfBitsSet()).isEqualTo(3);
        Assertions.assertThat(uc1.isSet(2)).isTrue();
        Assertions.assertThat(uc1.isSet(3)).isFalse();

        Bitmask uc2 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(candidates, predicate, false);
        Assertions.assertThat(uc2.numberOfBitsSet()).isEqualTo(4);
        Assertions.assertThat(uc2.isSet(3)).isTrue();
        Assertions.assertThat(uc2.isSet(2)).isFalse();
    }

    @Test
    public void test_eliminate_candidates() {
        Bitmask candidates = new Bitmask();
        Bitmask satisfiableCandidates = new Bitmask();
        Bitmask unsatisfiableCandidates = new Bitmask();
        Bitmask orphanedCandidates = new Bitmask();

        CanonicalIndexAlgorithm
                .eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 5);

        CanonicalIndexAlgorithm
                .eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(5);

        satisfiableCandidates.set(4);
        CanonicalIndexAlgorithm
                .eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(4);
        Assertions.assertThat(candidates.isSet(4)).isFalse();

        unsatisfiableCandidates.set(3);
        CanonicalIndexAlgorithm
                .eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(3);
        Assertions.assertThat(candidates.isSet(3)).isFalse();

        orphanedCandidates.set(0);
        CanonicalIndexAlgorithm
                .eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(2);
        Assertions.assertThat(candidates.isSet(0)).isFalse();

        Assertions.assertThat(candidates.isSet(1)).isTrue();
    }

    @Test
    public void test_remove_candidates_related_to_predicate() {
        Bitmask candidates = new Bitmask();
        Predicate predicate = new Predicate(new Bool(true));

        candidates.set(0, 5);
        CanonicalIndexAlgorithm.removeCandidatesRelatedToPredicate(predicate, candidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(5);

        predicate.getConjunctions().set(3);
        CanonicalIndexAlgorithm.removeCandidatesRelatedToPredicate(predicate, candidates);
        Assertions.assertThat(candidates.numberOfBitsSet()).isEqualTo(4);
        Assertions.assertThat(candidates.isSet(3)).isFalse();
    }

    @Test
    public void test_fetch_formulas() {
        final Bitmask satisfiableCandidates = new Bitmask();
        List<Set<DisjunctiveFormula>> relatedFormulas = new ArrayList<>();

        Assertions.assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, relatedFormulas)).isEmpty();

        satisfiableCandidates.set(1);
        satisfiableCandidates.set(2);
        Assertions.assertThatExceptionOfType(IndexOutOfBoundsException.class)
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
        Assertions.assertThat(formulas).isNotEmpty();
        Assertions.assertThat(formulas.size()).isEqualTo(5);

        Bitmask satisfiableCandidates2 = new Bitmask();
        Assertions.assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates2, relatedFormulas)).isEmpty();
    }

    @Test
    public void test_find_satisfiable_candidates() {
        Bitmask candidates = new Bitmask();
        Predicate predicate = new Predicate(new Bool(true));
        int[] trueLiteralsOfConjunction = new int[]{0, 0, 0};
        int[] numberOfLiteralsInConjunction = new int[]{1, 1, 2};

        Assertions.assertThat(CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, false,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 3);
        predicate.getFalseForTruePredicate().set(0, 1);
        predicate.getFalseForFalsePredicate().set(1, 3);

        //true -> {1} satisfiableCandidates
        Bitmask satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, true,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        Assertions.assertThat(satisfiableCandidates.isSet(0)).isFalse();
        Assertions.assertThat(satisfiableCandidates.isSet(1)).isTrue();
        Assertions.assertThat(satisfiableCandidates.isSet(2)).isFalse();

        //calling the method again without clearing the array trueLiteralsOfConjunction, so number of true literals
        // is "2" for the third candidate (all literals are true)
        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, true,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        Assertions.assertThat(satisfiableCandidates.isSet(2)).isTrue();


        trueLiteralsOfConjunction = new int[]{0, 0, 0};
        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(candidates, predicate, false,
                trueLiteralsOfConjunction, numberOfLiteralsInConjunction);
        Assertions.assertThat(satisfiableCandidates.isSet(0)).isTrue();
        Assertions.assertThat(satisfiableCandidates.isSet(1)).isFalse();
        Assertions.assertThat(satisfiableCandidates.isSet(2)).isFalse();
    }

    @Test
    public void test_find_orphaned_candidates() {
        Bitmask candidates = new Bitmask();
        Bitmask satisfiableCandidates = new Bitmask();
        int[] eliminatedFormulasWithConjunction = new int[]{};
        int[] numberOfFormulasWithConjunction = new int[]{};
        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>();

        Assertions.assertThat(CanonicalIndexAlgorithm.findOrphanedCandidates(candidates, satisfiableCandidates,
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
