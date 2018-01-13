package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;

public class RegexImplCustom extends io.sapl.grammar.sapl.impl.RegexImpl {

	private static final String REGEX_TYPE_MISMATCH = "Type mismatch. Matching regular expressions expects string values, but got: '%s' and '%s'.";
	private static final String REGEX_SYNTAX_ERROR = "Syntax error in regular expression '%s'.";

	private static final int HASH_PRIME_13 = 67;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
			throws PolicyEvaluationException {
		JsonNode left = getLeft().evaluate(ctx, isBody, relativeNode);
		JsonNode right = getRight().evaluate(ctx, isBody, relativeNode);
		if (left.isTextual() && right.isTextual()) {
			try {
				return JSON.booleanNode(Pattern.matches(right.asText(), left.asText()));
			} catch (PatternSyntaxException e) {
				throw new PolicyEvaluationException(String.format(REGEX_SYNTAX_ERROR, right.asText()), e);
			}
		} else {
			throw new PolicyEvaluationException(
					String.format(REGEX_TYPE_MISMATCH, left.getNodeType(), right.getNodeType()));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_13 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_13 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_13 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
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
		final RegexImplCustom otherImpl = (RegexImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl, otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl, otherImports, imports);
	}

}
