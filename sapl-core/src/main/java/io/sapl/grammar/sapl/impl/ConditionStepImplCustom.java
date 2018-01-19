/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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

public class ConditionStepImplCustom extends io.sapl.grammar.sapl.impl.ConditionStepImpl {

	private static final String CONDITION_ACCESS_TYPE_MISMATCH = "Type mismatch. Condition access is only possible for array or object, but got '%s'.";

	private static final int HASH_PRIME_10 = 53;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		JsonNode previousResultNode = previousResult.getNode();

		if (previousResultNode.isArray()) {
			for (int i = 0; i < previousResultNode.size(); i++) {
				JsonNode result = getExpression().evaluate(ctx, isBody, previousResultNode.get(i));

				if (result.isBoolean() && result.asBoolean()) {
					resultList.add(new JsonNodeWithParentArray(previousResultNode.get(i), previousResultNode, i));
				}
			}
		} else if (previousResultNode.isObject()) {
			Iterator<String> it = previousResultNode.fieldNames();
			while (it.hasNext()) {
				String key = it.next();
				JsonNode result = getExpression().evaluate(ctx, isBody, previousResultNode.get(key));

				if (result.isBoolean() && result.asBoolean()) {
					resultList.add(new JsonNodeWithParentObject(previousResultNode.get(key), previousResultNode, key));
				}
			}
		} else {
			throw new PolicyEvaluationException(String.format(CONDITION_ACCESS_TYPE_MISMATCH,
					previousResult.getNode().getNodeType()));
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode)
			throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode resultNode : previousResult) {
			JsonNode result = getExpression().evaluate(ctx, isBody, resultNode.getNode());
			if (result.isBoolean() && result.asBoolean()) {
				resultList.add(resultNode);
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_10 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_10 * hash + ((getExpression() == null) ? 0 : getExpression().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other)
			return true;
		if (other == null || getClass() != other.getClass())
			return false;
		final ConditionStepImplCustom otherImpl = (ConditionStepImplCustom) other;
		return (getExpression() == null) ? (getExpression() == otherImpl.getExpression())
				: getExpression().isEqualTo(otherImpl.getExpression(), otherImports, imports);
	}

}
