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
package io.sapl.prp.index.canonical;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
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

    private EvaluationContext subscriptionScopedEvaluationContext;

    @Before
    public void setUp() {
        subscriptionScopedEvaluationContext = new EvaluationContext(
                new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());

    }

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
    public void test_find_unsatisfiable_candidates() {
        var predicate = new Predicate(new Bool(true));
        var candidates = new Bitmask();

        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, true).numberOfBitsSet()).isEqualTo(0);
        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, false).numberOfBitsSet()).isEqualTo(0);

        predicate.getFalseForTruePredicate().set(0, 3);
        predicate.getFalseForFalsePredicate().set(3, 7);

        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, true).numberOfBitsSet()).isEqualTo(0);
        assertThat(CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, false).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 7);
        matchingCtx.addCandidates(candidates);

        var uc1 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, true);
        assertThat(uc1.numberOfBitsSet()).isEqualTo(3);
        assertThat(uc1.isSet(2)).isTrue();
        assertThat(uc1.isSet(3)).isFalse();

        var uc2 = CanonicalIndexAlgorithm
                .findUnsatisfiableCandidates(matchingCtx, predicate, false);
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

        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        CanonicalIndexAlgorithm
                .reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        var remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 5);
        matchingCtx.addCandidates(candidates);

        CanonicalIndexAlgorithm
                .reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(5);

        satisfiableCandidates.set(4);
        CanonicalIndexAlgorithm
                .reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(4);
        assertThat(remainingCandidates.isSet(4)).isFalse();

        unsatisfiableCandidates.set(3);
        CanonicalIndexAlgorithm
                .reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(3);
        assertThat(remainingCandidates.isSet(3)).isFalse();

        orphanedCandidates.set(0);
        CanonicalIndexAlgorithm
                .reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
        remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(2);
        assertThat(remainingCandidates.isSet(0)).isFalse();
        assertThat(remainingCandidates.isSet(1)).isTrue();
    }

    @Test
    public void test_remove_candidates_related_to_predicate() {
        var predicate = new Predicate(new Bool(true));

        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        var candidates = new Bitmask();
        candidates.set(0, 5);
        matchingCtx.addCandidates(candidates);

        CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
        var remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(5);

        predicate.getConjunctions().set(3);
        CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
        remainingCandidates = matchingCtx.getCopyOfCandidates();
        assertThat(remainingCandidates.numberOfBitsSet()).isEqualTo(4);
        assertThat(remainingCandidates.isSet(3)).isFalse();
    }

    @Test
    public void test_fetch_formulas() {
        var satisfiableCandidates = new Bitmask();
        List<Set<DisjunctiveFormula>> relatedFormulas = new ArrayList<>();
        var dataContainer = createEmptyContainer();

        assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, dataContainer)).isEmpty();

        satisfiableCandidates.set(1);
        satisfiableCandidates.set(2);
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(()
                -> CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, createEmptyContainer()));


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

        dataContainer = createEmptyContainerWithRelatedFormulas(relatedFormulas);

        Set<DisjunctiveFormula> formulas = CanonicalIndexAlgorithm
                .fetchFormulas(satisfiableCandidates, dataContainer);
        assertThat(formulas).isNotEmpty();
        assertThat(formulas.size()).isEqualTo(5);

        Bitmask satisfiableCandidates2 = new Bitmask();
        assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates2, dataContainer)).isEmpty();
    }

    @Test
    public void test_find_satisfiable_candidates() {
        var candidates = new Bitmask();
        var predicate = new Predicate(new Bool(true));
        int[] numberOfLiteralsInConjunction = new int[]{1, 1, 2};

        var matchingCtx = new CanonicalIndexMatchingContext(3, subscriptionScopedEvaluationContext);
        var dataContainer = createEmptyContainerWithNUmberOfLiteralsInConjunction(numberOfLiteralsInConjunction);

        assertThat(CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, false,
                matchingCtx, dataContainer).numberOfBitsSet()).isEqualTo(0);

        candidates.set(0, 3);
        matchingCtx.addCandidates(candidates);
        predicate.getFalseForTruePredicate().set(0, 1);
        predicate.getFalseForFalsePredicate().set(1, 3);

        //true -> {1} satisfiableCandidates
        var satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, true,
                matchingCtx, dataContainer);
        assertThat(satisfiableCandidates.isSet(0)).isFalse();
        assertThat(satisfiableCandidates.isSet(1)).isTrue();
        assertThat(satisfiableCandidates.isSet(2)).isFalse();

        //calling the method again without clearing the array trueLiteralsOfConjunction, so number of true literals
        // is "2" for the third candidate (all literals are true)
        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, true,
                matchingCtx, dataContainer);
        assertThat(satisfiableCandidates.isSet(2)).isTrue();


        satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, false,
                matchingCtx, dataContainer);
        assertThat(satisfiableCandidates.isSet(0)).isTrue();
        assertThat(satisfiableCandidates.isSet(1)).isFalse();
        assertThat(satisfiableCandidates.isSet(2)).isFalse();
    }

    @Test
    public void test_find_orphaned_candidates() {
        var satisfiableCandidates = new Bitmask();
        int[] numberOfFormulasWithConjunction = new int[]{};
        Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction = new HashMap<>();

        CanonicalIndexDataContainer dataContainer = createEmptyContainer();
        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        assertThat(CanonicalIndexAlgorithm.findOrphanedCandidates(satisfiableCandidates,
                matchingCtx, dataContainer).numberOfBitsSet()).isEqualTo(0);
    }

    private List<ConjunctiveClause> createDummyClauseList(int numberOfLiterals) {
        List<Literal> literals = new ArrayList<>();
        for (int i = 0; i < numberOfLiterals; i++) {
            literals.add(new Literal(new Bool(false)));
        }

        return Collections.singletonList(new ConjunctiveClause(literals));
    }

    private CanonicalIndexDataContainer createEmptyContainer() {
        return new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), new int[0], new int[0]);
    }

    private CanonicalIndexDataContainer createEmptyContainerWithRelatedFormulas(
            List<Set<DisjunctiveFormula>> relatedFormulas) {
        return new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                relatedFormulas, Collections.emptyMap(), Collections.emptyMap(), new int[0], new int[0]);
    }

    private CanonicalIndexDataContainer createEmptyContainerWithNUmberOfLiteralsInConjunction(
            int[] numberOfFormulasWithConjunction) {
        return new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                numberOfFormulasWithConjunction, new int[0]);
    }
}
