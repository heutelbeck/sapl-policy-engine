package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;

public class KeyStepImplCustom extends io.sapl.grammar.sapl.impl.KeyStepImpl {

	private static final String KEY_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing a JSON key '%s' expects object value, but got: '%s'.";
	private static final String KEY_ACCESS_NOT_FOUND = "Key not found. Failed to access JSON key '%s'.";

	private static final int HASH_PRIME_05 = 31;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode previousResultNode = previousResult.getNode();

		if (previousResultNode.isObject()) {
			if (!previousResultNode.has(id)) {
				throw new PolicyEvaluationException(String.format(KEY_ACCESS_NOT_FOUND, id));
			}
			return new JsonNodeWithParentObject(previousResultNode.get(id), previousResultNode, id);
		} else if (previousResultNode.isArray()) {
			return applyToJsonArray(previousResultNode);
		} else {
			throw new PolicyEvaluationException(
					String.format(KEY_ACCESS_TYPE_MISMATCH, id, previousResultNode.getNodeType()));
		}
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		return applyToJsonArray(previousResult.asJsonWithoutAnnotations());
	}

	private ArrayResultNode applyToJsonArray(Iterable<JsonNode> array) {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();

		for (JsonNode item : array) {
			if (item.isObject() && item.has(id)) {
				resultList.add(new JsonNodeWithParentObject(item.get(id), item, id));
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getId());
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
		final KeyStepImplCustom otherImpl = (KeyStepImplCustom) other;
		return Objects.equals(getId(), otherImpl.getId());
	}

}
