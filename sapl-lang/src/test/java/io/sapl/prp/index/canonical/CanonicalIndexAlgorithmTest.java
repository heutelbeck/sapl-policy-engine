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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

class CanonicalIndexAlgorithmTest {

	private EvaluationContext subscriptionScopedEvaluationContext;

	@BeforeEach
	void setUp() {
		subscriptionScopedEvaluationContext = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), new HashMap<>());

	}

	@Test
	void test_or_bitmask() {
		var b1 = new Bitmask();
		var b2 = new Bitmask();

		b1.set(0, 3);
		b2.set(2, 6);

		var b3 = CanonicalIndexAlgorithm.orBitMask(b1, b2);

		assertAll(() -> assertNotNull(b3), () -> assertTrue(b3.isSet(0)), () -> assertTrue(b3.isSet(2)),
				() -> assertTrue(b3.isSet(4)),
				() -> assertThrows(NullPointerException.class, () -> CanonicalIndexAlgorithm.orBitMask(null, b2)),
				() -> assertThrows(NullPointerException.class, () -> CanonicalIndexAlgorithm.orBitMask(b1, null)),
				() -> assertThrows(NullPointerException.class, () -> CanonicalIndexAlgorithm.orBitMask(null, null)));
	}

	@Test
	void test_find_unsatisfiable_candidates() {
		var predicate = new Predicate(new Bool(true));
		var candidates = new Bitmask();

		var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

		assertEquals(0,
				CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, true).numberOfBitsSet());
		assertEquals(0,
				CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, false).numberOfBitsSet());

		predicate.getFalseForTruePredicate().set(0, 3);
		predicate.getFalseForFalsePredicate().set(3, 7);

		assertEquals(0,
				CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, true).numberOfBitsSet());
		assertEquals(0,
				CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, false).numberOfBitsSet());

		candidates.set(0, 7);
		matchingCtx.addCandidates(candidates);

		var uc1 = CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, true);
		assertEquals(3, uc1.numberOfBitsSet());
		assertTrue(uc1.isSet(2));
		assertFalse(uc1.isSet(3));

		var uc2 = CanonicalIndexAlgorithm.findUnsatisfiableCandidates(matchingCtx, predicate, false);
		assertEquals(4, uc2.numberOfBitsSet());
		assertTrue(uc2.isSet(3));
		assertFalse(uc2.isSet(2));
	}

	@Test
	void test_eliminate_candidates() {
		var candidates = new Bitmask();
		var satisfiableCandidates = new Bitmask();
		var unsatisfiableCandidates = new Bitmask();
		var orphanedCandidates = new Bitmask();

		var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

		CanonicalIndexAlgorithm.reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates,
				orphanedCandidates);
		var remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(0, remainingCandidates.numberOfBitsSet());

		candidates.set(0, 5);
		matchingCtx.addCandidates(candidates);

		CanonicalIndexAlgorithm.reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates,
				orphanedCandidates);
		remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(5, remainingCandidates.numberOfBitsSet());

		satisfiableCandidates.set(4);
		CanonicalIndexAlgorithm.reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates,
				orphanedCandidates);
		remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(4, remainingCandidates.numberOfBitsSet());
		assertFalse(remainingCandidates.isSet(4));

		unsatisfiableCandidates.set(3);
		CanonicalIndexAlgorithm.reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates,
				orphanedCandidates);
		remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(3, remainingCandidates.numberOfBitsSet());
		assertFalse(remainingCandidates.isSet(3));

		orphanedCandidates.set(0);
		CanonicalIndexAlgorithm.reduceCandidates(matchingCtx, unsatisfiableCandidates, satisfiableCandidates,
				orphanedCandidates);
		remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(2, remainingCandidates.numberOfBitsSet());
		assertFalse(remainingCandidates.isSet(0));
		assertTrue(remainingCandidates.isSet(1));
	}

	@Test
	void test_remove_candidates_related_to_predicate() {
		var predicate = new Predicate(new Bool(true));
		var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);
		var candidates = new Bitmask();
		candidates.set(0, 5);
		matchingCtx.addCandidates(candidates);

		CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
		var remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(5, remainingCandidates.numberOfBitsSet());

		predicate.getConjunctions().set(3);
		CanonicalIndexAlgorithm.handleErrorEvaluationResult(predicate, matchingCtx);
		remainingCandidates = matchingCtx.getCopyOfCandidates();
		assertEquals(4, remainingCandidates.numberOfBitsSet());
		assertFalse(remainingCandidates.isSet(3));
	}

	@Test
	void test_fetch_formulas() {
		var satisfiableCandidates = new Bitmask();
		List<Set<DisjunctiveFormula>> relatedFormulas = new ArrayList<>();
		var dataContainer = createEmptyContainer();

		assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, dataContainer), empty());

		satisfiableCandidates.set(1);
		satisfiableCandidates.set(2);
		assertThrows(IndexOutOfBoundsException.class, () -> {
			CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, createEmptyContainer());
		});

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

		Set<DisjunctiveFormula> formulas = CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, dataContainer);
		assertThat(formulas, not(empty()));
		assertThat(formulas, hasSize(5));

		Bitmask satisfiableCandidates2 = new Bitmask();
		assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates2, dataContainer), empty());
	}

	@Test
	void test_find_satisfiable_candidates() {
		var candidates = new Bitmask();
		var predicate = new Predicate(new Bool(true));
		int[] numberOfLiteralsInConjunction = new int[] { 1, 1, 2 };

		var matchingCtx = new CanonicalIndexMatchingContext(3, subscriptionScopedEvaluationContext);
		var dataContainer = createEmptyContainerWithNUmberOfLiteralsInConjunction(numberOfLiteralsInConjunction);

		assertEquals(0, CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, false, matchingCtx, dataContainer)
				.numberOfBitsSet());

		candidates.set(0, 3);
		matchingCtx.addCandidates(candidates);
		predicate.getFalseForTruePredicate().set(0, 1);
		predicate.getFalseForFalsePredicate().set(1, 3);

		// true -> {1} satisfiableCandidates
		var satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, true, matchingCtx,
				dataContainer);
		assertFalse(satisfiableCandidates.isSet(0));
		assertTrue(satisfiableCandidates.isSet(1));
		assertFalse(satisfiableCandidates.isSet(2));

		// calling the method again without clearing the array
		// trueLiteralsOfConjunction, so number of true literals
		// is "2" for the third candidate (all literals are true)
		satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, true, matchingCtx,
				dataContainer);
		assertTrue(satisfiableCandidates.isSet(2));

		satisfiableCandidates = CanonicalIndexAlgorithm.findSatisfiableCandidates(predicate, false, matchingCtx,
				dataContainer);
		assertTrue(satisfiableCandidates.isSet(0));
		assertFalse(satisfiableCandidates.isSet(1));
		assertFalse(satisfiableCandidates.isSet(2));
	}

	@Test
	void test_find_orphaned_candidates() {
		var satisfiableCandidates = new Bitmask();

		CanonicalIndexDataContainer dataContainer = createEmptyContainer();
		var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

		assertEquals(0, CanonicalIndexAlgorithm
				.findOrphanedCandidates(satisfiableCandidates, matchingCtx, dataContainer).numberOfBitsSet());
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
