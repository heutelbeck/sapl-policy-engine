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
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;

public class WildcardStepImplCustom extends io.sapl.grammar.sapl.impl.WildcardStepImpl {

	private static final String WILDCARD_ACCESS_TYPE_MISMATCH = "Type mismatch. Wildcard access expects object or array, but got: '%s'.";

	private static final int HASH_PRIME_04 = 29;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode)
			throws PolicyEvaluationException {
		JsonNode previousResultNode = previousResult.getNode();
		if (previousResultNode.isArray()) {
			return previousResult;
		} else if (previousResultNode.isObject()) {
			ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
			Iterator<String> iterator = previousResultNode.fieldNames();
			while (iterator.hasNext()) {
				String key = iterator.next();
				resultList.add(new JsonNodeWithParentObject(previousResultNode.get(key), previousResultNode, key));
			}
			return new ArrayResultNode(resultList);
		} else {
			throw new PolicyEvaluationException(String.format(WILDCARD_ACCESS_TYPE_MISMATCH,
					previousResultNode.getNodeType()));
		}
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		return previousResult;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_04 * hash + Objects.hashCode(getClass().getTypeName());
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
