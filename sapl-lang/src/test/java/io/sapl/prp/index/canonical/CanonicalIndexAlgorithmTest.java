/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

class CanonicalIndexAlgorithmTest {

	@Test
	void return_matching_context_when_predicate_is_referenced_in_candidates() {
		var matchingCtx = mock(CanonicalIndexMatchingContext.class);
		when(matchingCtx.isPredicateReferencedInCandidates(any())).thenReturn(Boolean.TRUE);

		var predicate = new Predicate(new Bool(true));

		var dataContainer = mock(CanonicalIndexDataContainer.class);
		when(dataContainer.getPredicateOrder()).thenReturn(ImmutableList.copyOf(Collections.singletonList(predicate)));

		var result = CanonicalIndexAlgorithm.match(dataContainer).block();

		assertThat(result, notNullValue());
	}

	@Test
	void skip_predicates_without_candidate_references() {
		var p1 = new Predicate(new Bool(true));
		var p2 = new Predicate(new Bool(false));

		var matchingCtx = mock(CanonicalIndexMatchingContext.class);
		when(matchingCtx.isPredicateReferencedInCandidates(p1)).thenReturn(Boolean.FALSE);
		when(matchingCtx.isPredicateReferencedInCandidates(p2)).thenReturn(Boolean.TRUE);

		var dataContainer = mock(CanonicalIndexDataContainer.class);
		when(dataContainer.getPredicateOrder()).thenReturn(ImmutableList.copyOf(Arrays.asList(p1, p2)));

		try (MockedStatic<CanonicalIndexAlgorithm> mock = mockStatic(CanonicalIndexAlgorithm.class,
				Mockito.CALLS_REAL_METHODS)) {
			try (MockedConstruction<CanonicalIndexMatchingContext> mocked = Mockito.mockConstruction(
					CanonicalIndexMatchingContext.class,
					(mockCtx, context) -> when(mockCtx.isPredicateReferencedInCandidates(any(Predicate.class)))
							.thenAnswer(invocation -> matchingCtx
									.isPredicateReferencedInCandidates(invocation.getArgument(0, Predicate.class))))) {

				var result = CanonicalIndexAlgorithm.match(dataContainer).block();

				assertThat(result, notNullValue());

				verify(mocked.constructed().get(0), times(2)).isPredicateReferencedInCandidates(any(Predicate.class));

				mock.verify(() -> CanonicalIndexAlgorithm.skipPredicate(any()), times(1));
				mock.verify(() -> CanonicalIndexAlgorithm.handleEvaluationResult(any(), eq(p2), any(), any()),
						times(1));

			}
		}
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
		var predicate  = new Predicate(new Bool(true));
		var candidates = new Bitmask();

		var matchingCtx = new CanonicalIndexMatchingContext(0);

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
		var candidates              = new Bitmask();
		var satisfiableCandidates   = new Bitmask();
		var unsatisfiableCandidates = new Bitmask();
		var orphanedCandidates      = new Bitmask();

		var matchingCtx = new CanonicalIndexMatchingContext(0);

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
		var predicate   = new Predicate(new Bool(true));
		var matchingCtx = new CanonicalIndexMatchingContext(0);
		var candidates  = new Bitmask();
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
		var                           satisfiableCandidates = new Bitmask();
		List<Set<DisjunctiveFormula>> relatedFormulas       = new ArrayList<>();
		var                           dataContainer         = createEmptyContainer();

		assertThat(CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, dataContainer), empty());

		satisfiableCandidates.set(1);
		satisfiableCandidates.set(2);
		assertThrows(IndexOutOfBoundsException.class,
				() -> CanonicalIndexAlgorithm.fetchFormulas(satisfiableCandidates, createEmptyContainer()));

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
		var   candidates                    = new Bitmask();
		var   predicate                     = new Predicate(new Bool(true));
		int[] numberOfLiteralsInConjunction = new int[] { 1, 1, 2 };

		var matchingCtx   = new CanonicalIndexMatchingContext(3);
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
	void return_no_orphaned_candidates_on_empty_input() {
		var satisfiableCandidates = new Bitmask();

		CanonicalIndexDataContainer dataContainer = createEmptyContainer();
		var                         matchingCtx   = new CanonicalIndexMatchingContext(0);

		assertEquals(0, CanonicalIndexAlgorithm
				.findOrphanedCandidates(satisfiableCandidates, matchingCtx, dataContainer).numberOfBitsSet());
	}

	@Test
	void testOrphanedCandidates() {
		var satisfiableCandidates = new Bitmask(); // mock(Bitmask.class);
		satisfiableCandidates.set(0, 1);

		var c1 = mock(CTuple.class);
		when(c1.getCI()).thenReturn(0);
		var c2 = mock(CTuple.class);
		when(c2.getCI()).thenReturn(1);
		var c3 = mock(CTuple.class);
		when(c3.getCI()).thenReturn(2);

		var dataContainer = mock(CanonicalIndexDataContainer.class);
		when(dataContainer.getConjunctionsInFormulasReferencingConjunction(0))
				.thenReturn(new HashSet<>(Arrays.asList(c1, c2, c3)));

		var matchingCtx = mock(CanonicalIndexMatchingContext.class);
		when(matchingCtx.isRemainingCandidate(0)).thenReturn(Boolean.TRUE);
		when(matchingCtx.isRemainingCandidate(1)).thenReturn(Boolean.TRUE);
		when(matchingCtx.isRemainingCandidate(2)).thenReturn(Boolean.FALSE);
		when(matchingCtx.areAllFunctionsEliminated(eq(1), anyInt())).thenReturn(Boolean.TRUE);

		var orphanedCandidates = CanonicalIndexAlgorithm.findOrphanedCandidates(satisfiableCandidates, matchingCtx,
				dataContainer);

		assertThat(orphanedCandidates, notNullValue());
		assertThat(orphanedCandidates.numberOfBitsSet(), is(1));
		assertThat(orphanedCandidates.isSet(1), is(true));
	}

	private List<ConjunctiveClause> createDummyClauseList(int numberOfLiterals) {
		List<Literal> literals = new ArrayList<>(numberOfLiterals);
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
