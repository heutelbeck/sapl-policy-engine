/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class CanonicalIndexAlgorithm {

	public Mono<PolicyRetrievalResult> match(CanonicalIndexDataContainer dataContainer) {
		return matchCollectorNewest(dataContainer);
	}

	public Mono<PolicyRetrievalResult> matchCollectorNewest(CanonicalIndexDataContainer dataContainer) {
		var matchingCtxMono = Flux.fromIterable(dataContainer.getPredicateOrder())
				.reduce(Mono.just(new CanonicalIndexMatchingContext(dataContainer.getNumberOfConjunctions())),
						(previousCtxMono, predicate) -> previousCtxMono
								.flatMap(previousCtx -> previousCtx.isPredicateReferencedInCandidates(predicate)
										// if referenced by an active candidate ->
										// evaluate predicate
										? evaluatePredicate(dataContainer, predicate, previousCtx)
										// else -> just return context
										: skipPredicate(previousCtx)) // result is updated
																		// ctx (candidates
																		// removed based
																		// on predicate
																		// evaluation
																		// result)
				).flatMap(Function.identity()); // mono of mono is flattened

		return matchingCtxMono.map(matchingCtx -> {
			var matching = matchingCtx.getMatchingCandidatesMask();
			var formulas = fetchFormulas(matching, dataContainer);
			var policies = fetchPolicies(formulas, dataContainer);

			return new PolicyRetrievalResult(policies, matchingCtx.isErrorsInTargets(), true);
		}).onErrorReturn(new PolicyRetrievalResult(Collections.emptyList(), true, true));
	}

	Mono<CanonicalIndexMatchingContext> skipPredicate(CanonicalIndexMatchingContext previousCtx) {
		return Mono.just(previousCtx);
	}

	Mono<CanonicalIndexMatchingContext> evaluatePredicate(
			CanonicalIndexDataContainer dataContainer,
			Predicate predicate,
			CanonicalIndexMatchingContext ctx) {
		return predicate.evaluate()
				.map(evaluationResult -> handleEvaluationResult(dataContainer, predicate, ctx, evaluationResult));
	}

	CanonicalIndexMatchingContext handleEvaluationResult(
			CanonicalIndexDataContainer dataContainer,
			Predicate predicate,
			CanonicalIndexMatchingContext ctx,
			Val evaluationResult) {
		if (evaluationResult.isError()) {
			handleErrorEvaluationResult(predicate, ctx);
		} else {
			updateCandidatesInMatchingContext(predicate, evaluationResult.getBoolean(), ctx, dataContainer);
		}
		return ctx;
	}

	Bitmask orBitMask(@NonNull Bitmask b1, @NonNull Bitmask b2) {
		var result = new Bitmask(b1);
		result.or(b2);
		return result;
	}

	private void updateCandidatesInMatchingContext(
			Predicate predicate,
			Boolean evaluationResult,
			CanonicalIndexMatchingContext matchingCtx,
			CanonicalIndexDataContainer dataContainer) {

		var satisfiedCandidates = findSatisfiableCandidates(predicate, evaluationResult, matchingCtx, dataContainer);
		// add satisfied candidates to mask of matching candidates
		matchingCtx.addSatisfiedCandidates(satisfiedCandidates);

		var unsatisfiedCandidates = findUnsatisfiableCandidates(matchingCtx, predicate, evaluationResult);

		var orphanedCandidates = findOrphanedCandidates(satisfiedCandidates, matchingCtx, dataContainer);

		reduceCandidates(matchingCtx, unsatisfiedCandidates, satisfiedCandidates, orphanedCandidates);
	}

	void handleErrorEvaluationResult(final Predicate predicate, CanonicalIndexMatchingContext matchingCtx) {
		matchingCtx.setErrorsInTargets(true);
		// remove all conjunctions used by the predicate that returned an error during
		matchingCtx.removeCandidates(predicate.getConjunctions());
	}

	Bitmask findOrphanedCandidates(
			final Bitmask satisfiableCandidates,
			CanonicalIndexMatchingContext matchingCtx,
			CanonicalIndexDataContainer dataContainer) {
		var result = new Bitmask();

		satisfiableCandidates.forEachSetBit(index -> {
			var cTuples = dataContainer.getConjunctionsInFormulasReferencingConjunction(index);
			for (CTuple cTuple : cTuples) {
				if (!matchingCtx.isRemainingCandidate(cTuple.getCI()))
					continue;

				matchingCtx.increaseNumberOfEliminatedFormulasForConjunction(cTuple.getCI(), cTuple.getN());

				// if all formulas of conjunction have been eliminated
				if (matchingCtx.areAllFunctionsEliminated(cTuple.getCI(),
						dataContainer.getNumberOfFormulasWithConjunction(cTuple.getCI()))) {
					result.set(cTuple.getCI());
				}

			}
		});

		return result;
	}

	void reduceCandidates(
			final CanonicalIndexMatchingContext matchingCtx,
			final Bitmask unsatisfiedCandidates,
			final Bitmask satisfiedCandidates,
			final Bitmask orphanedCandidates) {
		matchingCtx.removeCandidates(unsatisfiedCandidates);
		matchingCtx.removeCandidates(satisfiedCandidates);
		matchingCtx.removeCandidates(orphanedCandidates);
	}

	Set<DisjunctiveFormula> fetchFormulas(
			final Bitmask satisfiableCandidates,
			CanonicalIndexDataContainer dataContainer) {
		final Set<DisjunctiveFormula> result = new HashSet<>();
		satisfiableCandidates.forEachSetBit(index -> result.addAll(dataContainer.getRelatedFormulas(index)));
		return result;
	}

	Bitmask findSatisfiableCandidates(
			final Predicate predicate,
			final boolean evaluationResult,
			CanonicalIndexMatchingContext matchingCtx,
			CanonicalIndexDataContainer dataContainer) {
		var result = new Bitmask();
		// calling method with negated evaluation result will return satisfied clauses
		var satisfiableCandidates = findUnsatisfiableCandidates(matchingCtx, predicate, !evaluationResult);

		satisfiableCandidates.forEachSetBit(index -> {
			// increment number of true literals
			matchingCtx.incrementTrueLiteralsForConjunction(index);

			// if all literals in conjunction are true, add conjunction to result
			if (matchingCtx.isConjunctionSatisfied(index, dataContainer.getNumberOfLiteralsInConjunction(index)))
				result.set(index);
		});

		return result;
	}

	private List<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas, CanonicalIndexDataContainer dataContainer) {
		return formulas.parallelStream().map(dataContainer::getPoliciesIncludingFormula)
				.flatMap(Collection::parallelStream).distinct().collect(Collectors.toList());
	}

	Bitmask findUnsatisfiableCandidates(
			final CanonicalIndexMatchingContext matchingCtx,
			final Predicate predicate,
			final boolean predicateEvaluationResult) {
		var result = matchingCtx.getCopyOfCandidates();

		if (predicateEvaluationResult)
			result.and(predicate.getFalseForTruePredicate());
		else
			result.and(predicate.getFalseForFalsePredicate());

		return result;
	}

}
