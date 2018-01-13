package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;

public class NotImplCustom extends io.sapl.grammar.sapl.impl.NotImpl {

	private static final String BOOLEAN_NEGATION_TYPE_MISMATCH = "Type mismatch. Boolean negation expects boolean value, but got: '%s'.";

	private static final int HASH_PRIME_09 = 47;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
			throws PolicyEvaluationException {
		JsonNode expressionResult = expression.evaluate(ctx, isBody, relativeNode);
		if (!expressionResult.isBoolean()) {
			throw new PolicyEvaluationException(
					String.format(BOOLEAN_NEGATION_TYPE_MISMATCH, expressionResult.getNodeType()));
		}
		return JSON.booleanNode(!expressionResult.asBoolean());
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_09 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_09 * hash + getExpression().hash(imports);
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final NotImplCustom otherImpl = (NotImplCustom) other;
		return (getExpression() == null) ? (getExpression() == otherImpl.getExpression())
				: getExpression().isEqualTo(otherImpl.getExpression(), otherImports, imports);
	}

}
