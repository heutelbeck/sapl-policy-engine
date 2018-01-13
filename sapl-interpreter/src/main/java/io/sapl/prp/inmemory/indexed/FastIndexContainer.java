package io.sapl.prp.inmemory.indexed;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class FastIndexContainer implements IndexContainer {

	private final List<Set<Integer>> associatedClauses;
	private final List<ConjunctiveClause> clauses;
	private final List<Set<SAPL>> documents;
	private final int[] occurrencesOfClauses;
	private final int[] sizeOfClauses;
	private final Set<SAPL> tautologicalDocuments;
	private final List<Variable> variableOrder;

	public FastIndexContainer(final List<ConjunctiveClause> clauses, final int[] sizeOfClauses,
			final List<Variable> variableOrder, final List<Set<SAPL>> documents, int[] occurrencesOfClauses,
			final List<Set<Integer>> associatedClauses, final Set<SAPL> tautologicalDocuments) {
		this.clauses = ImmutableList.copyOf(clauses);
		this.associatedClauses = ImmutableList.copyOf(associatedClauses);
		this.variableOrder = ImmutableList.copyOf(variableOrder);
		this.documents = ImmutableList.copyOf(documents);
		this.tautologicalDocuments = ImmutableSet.copyOf(tautologicalDocuments);
		this.sizeOfClauses = Arrays.copyOf(sizeOfClauses, sizeOfClauses.length);
		this.occurrencesOfClauses = Arrays.copyOf(occurrencesOfClauses, occurrencesOfClauses.length);
	}

	@Override
	public PolicyRetrievalResult match(final FunctionContext functionCtx, final VariableContext variableCtx) {
		final HashSet<SAPL> result = new HashSet<>();
		result.addAll(tautologicalDocuments);

		final BitSet candidates = new BitSet();
		candidates.set(0, clauses.size());
		final int[] uncheckedVariablesOfClause = Arrays.copyOf(sizeOfClauses, sizeOfClauses.length);
		final int[] uncheckedClausesOfFormula = Arrays.copyOf(occurrencesOfClauses, occurrencesOfClauses.length);

		for (Variable variable : variableOrder) {
			if (!variable.isPartOfCandidates(candidates)) {
				continue;
			}
			boolean value;
			try {
				value = variable.evaluate(functionCtx, variableCtx);
			} catch (PolicyEvaluationException e) {
				return new PolicyRetrievalResult(result, true);
			}
			variable.eliminateUnsatisfiableCandidates(candidates, value);
			variable.getSatisfiableCandidates(value).forEach(index -> {
				if (candidates.get(index)) {
					uncheckedVariablesOfClause[index] -= 1;
					if (uncheckedVariablesOfClause[index] == 0) {
						candidates.clear(index);
						result.addAll(documents.get(index));
						associatedClauses.get(index).forEach(coindex -> {
							uncheckedClausesOfFormula[coindex] -= 1;
							if (uncheckedClausesOfFormula[coindex] == 0) {
								candidates.clear(coindex);
							}
						});
					}
				}
			});
		}
		return new PolicyRetrievalResult(result, false);
	}
}
