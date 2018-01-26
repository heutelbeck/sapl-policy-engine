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

import java.util.List;
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

public class ExpressionStepImplCustom extends io.sapl.grammar.sapl.impl.ExpressionStepImpl {

	private static final String EXPRESSION_ACCESS_INDEX_NOT_FOUND = "Index not found. Failed to access item with index '%s' after expression evaluation.";
	private static final String EXPRESSION_ACCESS_KEY_NOT_FOUND = "Key not found. Failed to access JSON key '%s' after expression evaluation.";
	private static final String EXPRESSION_ACCESS_TYPE_MISMATCH = "Type mismatch. Expression evaluates to '%s' which can not be used.";

	private static final int HASH_PRIME_02 = 19;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode result = getExpression().evaluate(ctx, isBody, relativeNode);

		if (result.isNumber()) {
			if (previousResult.getNode().isArray()) {
				int index = result.asInt();
				JsonNode node = previousResult.getNode();
				if (!node.has(index)) {
					throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_INDEX_NOT_FOUND, index));
				}
				return new JsonNodeWithParentArray(node.get(index), node, index);
			} else {
				throw new PolicyEvaluationException(
						String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType()));
			}
		} else if (result.isTextual()) {
			String attribute = result.asText();
			JsonNode previousResultNode = previousResult.getNode();
			if (!previousResultNode.has(attribute)) {
				throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_KEY_NOT_FOUND, attribute));
			}
			return new JsonNodeWithParentObject(previousResultNode.get(attribute), previousResultNode, attribute);
		} else {
			throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType()));
		}
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode result = getExpression().evaluate(ctx, isBody, previousResult.asJsonWithoutAnnotations());

		if (result.isNumber()) {
			int index = result.asInt();
			List<AbstractAnnotatedJsonNode> nodes = previousResult.getNodes();
			if (index < 0 || index >= nodes.size()) {
				throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_INDEX_NOT_FOUND, index));
			}
			return nodes.get(index);
		} else {
			throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType()));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_02 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_02 * hash + ((getExpression() == null) ? 0 : getExpression().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other)
			return true;
		if (other == null || getClass() != other.getClass())
			return false;
		final ExpressionStepImplCustom otherImpl = (ExpressionStepImplCustom) other;
		return (getExpression() == null) ? (getExpression() == otherImpl.getExpression())
				: getExpression().isEqualTo(otherImpl.getExpression(), otherImports, imports);
	}

}
