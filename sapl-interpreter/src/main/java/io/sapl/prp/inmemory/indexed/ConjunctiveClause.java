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

public class ConjunctiveClause {

	static final String CONSTRUCTION_FAILED = "Failed to create instance, empty collection provided.";
	static final String EVALUATION_NOT_POSSIBLE = "Evaluation Error: Attempting to evaluate empty clause.";

	private int hash;

	private boolean hasHashCode;

	private final List<Literal> literals;

	public ConjunctiveClause(final Collection<Literal> literals) {
		Preconditions.checkArgument(!literals.isEmpty(), CONSTRUCTION_FAILED);
		this.literals = new ArrayList<>(literals);
	}

	public ConjunctiveClause(Literal... literals) {
		this(Arrays.asList(literals));
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
		final ConjunctiveClause other = (ConjunctiveClause) obj;
		if (hashCode() != other.hashCode()) {
			return false;
		}
		if (literals.size() != other.literals.size()) {
			return false;
		}
		return literals.containsAll(other.literals)
				&& other.literals.containsAll(literals);
	}

	public boolean evaluate() {
		ListIterator<Literal> iter = literals.listIterator();
		if (!iter.hasNext()) {
			throw new IllegalStateException(EVALUATION_NOT_POSSIBLE);
		}
		Literal first = iter.next();
		boolean result = first.evaluate();
		while (iter.hasNext()) {
			if (!result) {
				return false;
			}
			result = result && iter.next().evaluate();
		}
		return result;
	}

	public boolean evaluate(final FunctionContext functionCtx,
			final VariableContext variableCtx) throws PolicyEvaluationException {
		ListIterator<Literal> iter = literals.listIterator();
		if (!iter.hasNext()) {
			throw new PolicyEvaluationException(EVALUATION_NOT_POSSIBLE);
		}
		Literal first = iter.next();
		boolean result = first.evaluate(functionCtx, variableCtx);
		while (iter.hasNext()) {
			if (!result) {
				return false;
			}
			result = result && iter.next().evaluate(functionCtx, variableCtx);
		}
		return result;
	}

	public List<Literal> getLiterals() {
		return Collections.unmodifiableList(literals);
	}

	@Override
	public int hashCode() {
		if (!hasHashCode) {
			int h = 7;
			h = 23 * h + literals.stream().mapToInt(Objects::hashCode).sum();
			hash = h;
			hasHashCode = true;
		}
		return hash;
	}

	public boolean isImmutable() {
		for (Literal literal : literals) {
			if (!literal.isImmutable()) {
				return false;
			}
		}
		return true;
	}

	public boolean isSubsetOf(final ConjunctiveClause other) {
		for (Literal lhs : literals) {
			boolean match = false;
			for (Literal rhs : other.literals) {
				if (lhs.sharesBool(rhs) && lhs.sharesNegation(rhs)) {
					match = true;
					break;
				}
			}
			if (!match) {
				return false;
			}
		}
		return true;
	}

	public List<ConjunctiveClause> negate() {
		ListIterator<Literal> iter = literals.listIterator();
		if (!iter.hasNext()) {
			return Collections.singletonList(this);
		}
		List<ConjunctiveClause> result = new ArrayList<>(literals.size());
		while (iter.hasNext()) {
			Literal literal = iter.next();
			result.add(new ConjunctiveClause(literal.negate()));
		}
		return Collections.unmodifiableList(result);
	}

	public ConjunctiveClause reduce() {
		if (size() > 1) {
			List<Literal> result = new ArrayList<>(getLiterals());
			ConjunctiveClauseReductionSupport.reduceConstants(result);
			ConjunctiveClauseReductionSupport.reduceFormula(result);
			return new ConjunctiveClause(result);
		}
		return this;
	}

	public int size() {
		return literals.size();
	}

}
