package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;

public class RecursiveWildcardStepImplCustom extends io.sapl.grammar.sapl.impl.RecursiveWildcardStepImpl {

	private static final int HASH_PRIME_11 = 59;
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
		for (AbstractAnnotatedJsonNode child : previousResult) {
			resultList.add(child);
			resultList.addAll(resolveRecursive(child.getNode()));
		}
		return new ArrayResultNode(resultList);
	}

	private static ArrayList<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				resultList.add(new JsonNodeWithParentArray(node.get(i), node, i));
				resultList.addAll(resolveRecursive(node.get(i)));
			}
		} else {
			Iterator<String> it = node.fieldNames();
			while (it.hasNext()) {
				String key = it.next();
				resultList.add(new JsonNodeWithParentObject(node.get(key), node, key));
				resultList.addAll(resolveRecursive(node.get(key)));
			}
		}
		return resultList;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_11 * hash + Objects.hashCode(getClass().getTypeName());
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		return !(other == null || getClass() != other.getClass());
	}

}
