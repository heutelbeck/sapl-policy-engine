package io.sapl.prp.inmemory.indexed;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.Exceptions;

public class Bool {

	static final String BOOL_NOT_IMMUTABLE = "Unable to evaluate volatile Bool in static context.";
	static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

	private boolean constant;
	private Expression expression;
	private int hash;
	private boolean hasHashCode;
	private Map<String, String> imports;
	private boolean isConstantExpression;

	public Bool(boolean value) {
		isConstantExpression = true;
		constant = value;
	}

	public Bool(final Expression expression, final Map<String, String> imports) {
		this.expression = Preconditions.checkNotNull(expression);
		this.imports = imports;
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
		final Bool other = (Bool) obj;
		if (hashCode() != other.hashCode()) {
			return false;
		}
		if (!Objects.equals(isConstantExpression, other.isConstantExpression)) {
			return false;
		}
		if (isConstantExpression) {
			return Objects.equals(constant, other.constant);
		} else {
			return expression.isEqualTo(other.expression, other.imports, imports);
		}
	}

	public boolean evaluate() {
		if (isConstantExpression) {
			return constant;
		}
		throw new IllegalStateException(BOOL_NOT_IMMUTABLE);
	}

	public boolean evaluate(final FunctionContext functionCtx, final VariableContext variableCtx)
			throws PolicyEvaluationException {
		if (!isConstantExpression) {
			EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx, imports);
			try {
				JsonNode result = expression.evaluate(ctx, false, null).blockFirst();
				if (result.isBoolean()) {
					return result.asBoolean();
				}
				throw new PolicyEvaluationException(String.format(CONDITION_NOT_BOOLEAN, result.getNodeType()));
			} catch (RuntimeException e) {
				throw new PolicyEvaluationException(Exceptions.unwrap(e));
			}
		}
		return constant;
	}

	@Override
	public int hashCode() {
		if (!hasHashCode) {
			int h = 7;
			h = 59 * h + Objects.hashCode(isConstantExpression);
			if (isConstantExpression) {
				h = 59 * h + Objects.hashCode(constant);
			} else {
				h = 59 * h + expression.hash(imports);
			}
			hash = h;
			hasHashCode = true;
		}
		return hash;
	}

	public boolean isImmutable() {
		return isConstantExpression;
	}
}
