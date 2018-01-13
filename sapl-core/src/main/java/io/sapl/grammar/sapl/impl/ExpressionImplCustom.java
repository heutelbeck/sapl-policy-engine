package io.sapl.grammar.sapl.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;

public class ExpressionImplCustom extends io.sapl.grammar.sapl.impl.ExpressionImpl {

	private static final String ARITHMETIC_OPERATION_TYPE_MISMATCH = "Type mismatch. Arithmetic operation expects number values, but got: '%s' and '%s'.";
	private static final String BOOLEAN_OPERATION_TYPE_MISMATCH = "Type mismatch. Boolean opration expects boolean values, but got: '%s' and '%s'.";

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	protected static void assertNumbers(JsonNode left, JsonNode right) throws PolicyEvaluationException {
		if (!left.isNumber() || !right.isNumber()) {
			throw new PolicyEvaluationException(
					String.format(ARITHMETIC_OPERATION_TYPE_MISMATCH, left.getNodeType(), right.getNodeType()));
		}
	}

	protected static void assertBoolean(JsonNode left, JsonNode right) throws PolicyEvaluationException {
		if (!left.isBoolean() || !right.isBoolean()) {
			throw new PolicyEvaluationException(
					String.format(BOOLEAN_OPERATION_TYPE_MISMATCH, left.getNodeType(), right.getNodeType()));
		}
	}

}
