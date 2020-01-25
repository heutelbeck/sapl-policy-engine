/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.inmemory.indexed;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class FastIndexContainer implements IndexContainer {

	private final AuxiliaryMatrix initialMatrix;

	private final boolean isCompilant;

	private final Map<DisjunctiveFormula, Bitmask> relatedCandidates;

	private final Map<DisjunctiveFormula, Set<SAPL>> relatedDocuments;

	private final List<Set<DisjunctiveFormula>> relatedFormulas;

	private final List<Variable> variableOrder;

	public FastIndexContainer(boolean compilant, final List<Variable> variableOrder,
			final Map<DisjunctiveFormula, Bitmask> relatedCandidates,
			final List<Set<DisjunctiveFormula>> relatedFormulas,
			final Map<DisjunctiveFormula, Set<SAPL>> relatedDocuments, final AuxiliaryMatrix initialMatrix) {
		isCompilant = compilant;
		this.variableOrder = ImmutableList.copyOf(variableOrder);
		this.relatedCandidates = ImmutableMap.copyOf(relatedCandidates);
		this.relatedFormulas = ImmutableList.copyOf(relatedFormulas);
		this.relatedDocuments = ImmutableMap.copyOf(relatedDocuments);
		this.initialMatrix = new AuxiliaryMatrix(initialMatrix);
	}

	@Override
	public PolicyRetrievalResult match(final FunctionContext functionCtx, final VariableContext variableCtx) {
		Set<DisjunctiveFormula> result = new HashSet<>();
		final boolean[] hasError = { false };

		AuxiliaryMatrix matrix = new AuxiliaryMatrix(initialMatrix);
		Bitmask candidates = new Bitmask();
		candidates.set(0, matrix.size());

		Bitmask inspectedVariables = new Bitmask();
		final Bitmask satisfiedCandidates = new Bitmask();
		final Bitmask insignificantCandidates = new Bitmask();

		final int len = variableOrder.size();
		for (int i = 0; i < len; ++i) {
			Variable variable = variableOrder.get(i);
			if (isPartOfCandidates(variable, candidates)) {
				inspectedVariables.set(i);
				Optional<Boolean> outcome = variable.evaluate(functionCtx, variableCtx);
				if (handleErrorHarshly(outcome)) {
					return new PolicyRetrievalResult(fetchPolicies(result), true);
				}
				processVariable(candidates, variable, outcome, matrix, satisfiedCandidates, insignificantCandidates,
						hasError, result);
			}
		}
		if (isCompilant) {
			inspectedVariables.flip(0, len);
			inspectedVariables.forEachSetBit(index -> {
				Variable variable = variableOrder.get(index);
				if (isPartOfCandidates(variable, insignificantCandidates)) {
					Optional<Boolean> outcome = variable.evaluate(functionCtx, variableCtx);
					if (!outcome.isPresent()) {
						removeCandidatesRelatedToVariable(variable, satisfiedCandidates, insignificantCandidates);
						hasError[0] = true;
					}
				}
			});
			result.addAll(fetchFormulas(satisfiedCandidates));
		}
		return new PolicyRetrievalResult(fetchPolicies(result), hasError[0]);
	}

	protected Bitmask checkDispensableRelatingToCandidate(final Bitmask dispensableCandidates, AuxiliaryMatrix matrix) {
		final Bitmask result = new Bitmask();
		dispensableCandidates.forEachSetBit(index -> {
			if (matrix.decrementAndGetRemainingOccurrencesOfClause(index) == 0) {
				result.set(index);
			}
		});
		return result;
	}

	protected void eliminateCandidates(final Bitmask candidates, final Bitmask unsatisfiableCandidates,
			final Bitmask satisfiableCandidates, final Bitmask dispensableCandidates) {
		candidates.andNot(unsatisfiableCandidates);
		candidates.andNot(satisfiableCandidates);
		candidates.andNot(dispensableCandidates);
	}

	protected Set<DisjunctiveFormula> fetchFormulas(final Bitmask satisfiableCandidates) {
		final Set<DisjunctiveFormula> result = new HashSet<>();
		satisfiableCandidates.forEachSetBit(index -> result.addAll(relatedFormulas.get(index)));
		return result;
	}

	protected Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas) {
		Set<SAPL> result = new HashSet<>();
		for (DisjunctiveFormula formula : formulas) {
			result.addAll(relatedDocuments.get(formula));
		}
		return result;
	}

	protected Bitmask findDispensableCandidates(final Bitmask candidates, final Bitmask satisfiableCandidates,
			final AuxiliaryMatrix matrix) {
		final Bitmask result = new Bitmask();
		satisfiableCandidates
				.forEachSetBit(index -> result.or(identifyDispensableRelatingToCandidate(candidates, index, matrix)));
		return result;
	}

	protected Bitmask findRelatedCandidates(final Variable variable) {
		Bitmask result = new Bitmask();
		Set<DisjunctiveFormula> formulas = fetchFormulas(variable.getCandidates());
		for (DisjunctiveFormula formula : formulas) {
			result.or(relatedCandidates.get(formula));
		}
		return result;
	}

	protected Bitmask findSatisfiableCandidates(final Bitmask candidates, final Variable variable, boolean value,
			final AuxiliaryMatrix matrix) {
		final Bitmask result = new Bitmask();
		Bitmask affectedCandidates = findUnsatisfiableCandidates(candidates, variable, !value);
		affectedCandidates.forEachSetBit(index -> {
			if (matrix.decrementAndGetRemainingLiteralsOfClause(index) == 0) {
				result.set(index);
			}
		});
		return result;
	}

	protected Bitmask findUnsatisfiableCandidates(final Bitmask candidates, final Variable variable, boolean value) {
		Bitmask result = new Bitmask(candidates);
		if (value) {
			result.and(variable.getUnsatisfiedCandidatesWhenTrue());
		}
		else {
			result.and(variable.getUnsatisfiedCandidatesWhenFalse());
		}
		return result;
	}

	protected boolean handleErrorGracefully(Optional<Boolean> outcome) {
		return !outcome.isPresent() && isCompilant;
	}

	protected boolean handleErrorHarshly(Optional<Boolean> outcome) {
		return !outcome.isPresent() && !isCompilant;
	}

	protected Bitmask identifyDispensableRelatingToCandidate(final Bitmask candidates, int index,
			final AuxiliaryMatrix matrix) {
		Bitmask result = new Bitmask();
		for (DisjunctiveFormula formula : relatedFormulas.get(index)) {
			Bitmask affectedCandidates = new Bitmask(candidates);
			affectedCandidates.clear(index);
			affectedCandidates.and(relatedCandidates.get(formula));
			result.or(checkDispensableRelatingToCandidate(affectedCandidates, matrix));
		}
		return result;
	}

	protected boolean isPartOfCandidates(final Variable variable, final Bitmask candidates) {
		return variable.getCandidates().intersects(candidates);
	}

	protected void processVariable(final Bitmask candidates, final Variable variable, Optional<Boolean> outcome,
			final AuxiliaryMatrix matrix, final Bitmask satisfiedCandidates, final Bitmask insignificantCandidates,
			final boolean[] hasError, final Set<DisjunctiveFormula> result) {
		if (handleErrorGracefully(outcome)) {
			removeCandidatesRelatedToVariable(variable, candidates, satisfiedCandidates, insignificantCandidates);
			hasError[0] = true;
			return;
		}
		boolean value = outcome.get();

		Bitmask unsatisfiableCandidates = findUnsatisfiableCandidates(candidates, variable, value);
		Bitmask satisfiableCandidates = findSatisfiableCandidates(candidates, variable, value, matrix);
		Bitmask dispensableCandidates = findDispensableCandidates(candidates, satisfiableCandidates, matrix);

		eliminateCandidates(candidates, unsatisfiableCandidates, satisfiableCandidates, dispensableCandidates);

		if (isCompilant) {
			satisfiedCandidates.or(satisfiableCandidates);
			insignificantCandidates.or(dispensableCandidates);
		}
		else {
			result.addAll(fetchFormulas(satisfiableCandidates));
		}
	}

	protected void removeCandidatesRelatedToVariable(final Variable variable, final Bitmask... candidates) {
		Bitmask affectedCandidates = findRelatedCandidates(variable);
		for (Bitmask clauses : candidates) {
			clauses.andNot(affectedCandidates);
		}
	}

}
