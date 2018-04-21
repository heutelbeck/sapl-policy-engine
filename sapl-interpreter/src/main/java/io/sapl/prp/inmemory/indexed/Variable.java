package io.sapl.prp.inmemory.indexed;

import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Variable {

	private final Bool bool;
	private final Bitmask occurencesInCandidates = new Bitmask();
	private final Bitmask unsatisfiableCandidatesWhenFalse = new Bitmask();
	private final Bitmask unsatisfiableCandidatesWhenTrue = new Bitmask();

	public Variable(final Bool bool) {
		this.bool = Preconditions.checkNotNull(bool);
	}

	public Bool getBool() {
		return bool;
	}

	public Bitmask getCandidates() {
		return occurencesInCandidates;
	}

	public Bitmask getUnsatisfiedCandidatesWhenFalse() {
		return unsatisfiableCandidatesWhenFalse;
	}

	public Bitmask getUnsatisfiedCandidatesWhenTrue() {
		return unsatisfiableCandidatesWhenTrue;
	}

	protected Optional<Boolean> evaluate(final FunctionContext functionCtx, final VariableContext variableCtx) {
		Boolean result = null;
		try {
			result = getBool().evaluate(functionCtx, variableCtx);
		} catch (PolicyEvaluationException e) {
			LOGGER.debug(Throwables.getStackTraceAsString(e));
		}
		return Optional.ofNullable(result);
	}
}
