package io.sapl.prp.inmemory.indexed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class DisjunctiveFormula {

	static final String CONSTRUCTION_FAILED = "Failed to create instance, empty collection provided.";
	static final String EVALUATION_NOT_POSSIBLE = "Evaluation Error: Attempting to evaluate empty formula.";

	private final List<ConjunctiveClause> clauses;

	private int hash;

	private boolean hasHashCode;

	public DisjunctiveFormula(final Collection<ConjunctiveClause> clauses) {
		Preconditions.checkArgument(!clauses.isEmpty(), CONSTRUCTION_FAILED);
		this.clauses = new ArrayList<>(clauses);
	}

	public DisjunctiveFormula(ConjunctiveClause... clauses) {
		this(Arrays.asList(clauses));
	}

	public DisjunctiveFormula combine(final DisjunctiveFormula formula) {
		List<ConjunctiveClause> result = new ArrayList<>(clauses);
		result.addAll(formula.clauses);
		return new DisjunctiveFormula(result).reduce();
	}

	public DisjunctiveFormula distribute(final DisjunctiveFormula formula) {
		List<ConjunctiveClause> result = new ArrayList<>();
		ListIterator<ConjunctiveClause> left = clauses.listIterator();
		while (left.hasNext()) {
			ConjunctiveClause lhs = left.next();
			ListIterator<ConjunctiveClause> right = formula.clauses.listIterator();
			while (right.hasNext()) {
				ConjunctiveClause rhs = right.next();
				List<Literal> literals = new ArrayList<>(lhs.size() + rhs.size());
				literals.addAll(lhs.getLiterals());
				literals.addAll(rhs.getLiterals());
				result.add(new ConjunctiveClause(literals));
			}
		}
		return new DisjunctiveFormula(result).reduce();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final DisjunctiveFormula other = (DisjunctiveFormula) obj;
		if (hashCode() != other.hashCode()) {
			return false;
		}
		if (clauses.size() != other.clauses.size()) {
			return false;
		}
		return clauses.containsAll(other.clauses) && other.clauses.containsAll(clauses);
	}

	public boolean evaluate() {
		ListIterator<ConjunctiveClause> iter = clauses.listIterator();
		if (!iter.hasNext()) {
			throw new IllegalStateException(EVALUATION_NOT_POSSIBLE);
		}
		ConjunctiveClause first = iter.next();
		boolean result = first.evaluate();
		while (iter.hasNext()) {
			if (result) {
				return true;
			}
			result = result || iter.next().evaluate();
		}
		return result;
	}

	public boolean evaluate(final FunctionContext functionCtx, final VariableContext variableCtx)
			throws PolicyEvaluationException {
		ListIterator<ConjunctiveClause> iter = clauses.listIterator();
		if (!iter.hasNext()) {
			throw new PolicyEvaluationException(EVALUATION_NOT_POSSIBLE);
		}
		ConjunctiveClause first = iter.next();
		boolean result = first.evaluate(functionCtx, variableCtx);
		while (iter.hasNext()) {
			if (result) {
				return true;
			}
			result = result || iter.next().evaluate(functionCtx, variableCtx);
		}
		return result;
	}

	public List<ConjunctiveClause> getClauses() {
		return Collections.unmodifiableList(clauses);
	}

	@Override
	public int hashCode() {
		if (!hasHashCode) {
			int h = 5;
			h = 17 * h + clauses.stream().mapToInt(Objects::hashCode).sum();
			hash = h;
			hasHashCode = true;
		}
		return hash;
	}

	public boolean isImmutable() {
		for (ConjunctiveClause clause : clauses) {
			if (!clause.isImmutable()) {
				return false;
			}
		}
		return true;
	}

	public DisjunctiveFormula negate() {
		ListIterator<ConjunctiveClause> iter = clauses.listIterator();
		if (!iter.hasNext()) {
			return this;
		}
		ConjunctiveClause first = iter.next();
		DisjunctiveFormula result = new DisjunctiveFormula(first.negate());
		while (iter.hasNext()) {
			ConjunctiveClause clause = iter.next();
			result = result.distribute(new DisjunctiveFormula(clause.negate()));
		}
		return result.reduce();
	}

	public DisjunctiveFormula reduce() {
		List<ConjunctiveClause> clauses = getClauses();
		List<ConjunctiveClause> result = new ArrayList<>(clauses.size());
		for (ConjunctiveClause clause : clauses) {
			result.add(clause.reduce());
		}
		if (result.size() > 1) {
			DisjunctiveFormulaReductionSupport.reduceConstants(result);
			DisjunctiveFormulaReductionSupport.reduceFormula(result);
		}
		return new DisjunctiveFormula(result);
	}

	public int size() {
		return clauses.size();
	}

}
