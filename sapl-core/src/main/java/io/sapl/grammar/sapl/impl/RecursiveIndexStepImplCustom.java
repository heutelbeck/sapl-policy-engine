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
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;

public class RecursiveIndexStepImplCustom extends io.sapl.grammar.sapl.impl.RecursiveIndexStepImpl {

	private static final int HASH_PRIME_06 = 37;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		resultList.addAll(resolveRecursive(previousResult.getNode()));
		return new ArrayResultNode(resultList);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode)
			throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode target : previousResult) {
			resultList.addAll(resolveRecursive(target.getNode()));
		}
		return new ArrayResultNode(resultList);
	}

	private ArrayList<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		int intIndex = index.toBigInteger().intValue();
		if (node.isArray() && node.has(intIndex)) {
			resultList.add(new JsonNodeWithParentArray(node.get(intIndex), node, intIndex));
		}
		for (JsonNode child : node) {
			resultList.addAll(resolveRecursive(child));
		}
		return resultList;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_06 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_06 * hash + Objects.hashCode(getIndex());
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
		final RecursiveIndexStepImplCustom otherImpl = (RecursiveIndexStepImplCustom) other;
		return Objects.equals(getIndex(), otherImpl.getIndex());
	}

}
