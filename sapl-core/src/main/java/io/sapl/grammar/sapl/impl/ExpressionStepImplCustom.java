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
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class ExpressionStepImplCustom extends io.sapl.grammar.sapl.impl.ExpressionStepImpl {

	private static final String EXPRESSION_ACCESS_INDEX_NOT_FOUND = "Index not found. Failed to access item with index '%s' after expression evaluation.";
	private static final String EXPRESSION_ACCESS_TYPE_MISMATCH = "Type mismatch. Expression evaluates to '%s' which can not be used.";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return getExpression().evaluate(ctx, isBody, relativeNode).flatMap(expressionResult -> {
			try {
				return Flux.just(handleExpressionResultFor(previousResult, expressionResult));
			} catch (PolicyEvaluationException e) {
				return Flux.error(e);
			}
		});
	}

	private ResultNode handleExpressionResultFor(AbstractAnnotatedJsonNode previousResult, Optional<JsonNode> optResult)
			throws PolicyEvaluationException {
		JsonNode result = optResult.orElseThrow(() -> new PolicyEvaluationException("undefined value"));
		if (result.isNumber()) {
			if (previousResult.getNode().isPresent() && previousResult.getNode().get().isArray()) {
				final int index = result.asInt();
				final Optional<JsonNode> previousResultNode = previousResult.getNode();
				if (previousResultNode.isPresent() && !previousResultNode.get().has(index)) {
					throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_INDEX_NOT_FOUND, index));
				}
				return new JsonNodeWithParentArray(Optional.of(previousResultNode.get().get(index)), previousResultNode,
						index);
			} else {
				throw new PolicyEvaluationException(
						String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType()));
			}
		} else if (result.isTextual()) {
			final String attribute = result.asText();
			final Optional<JsonNode> previousResultNode = previousResult.getNode();
			if (!previousResultNode.get().has(attribute)) {
				return new JsonNodeWithParentObject(Optional.empty(), previousResultNode, attribute);
			}
			return new JsonNodeWithParentObject(Optional.of(previousResultNode.get().get(attribute)),
					previousResultNode, attribute);
		} else {
			throw new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType()));
		}
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return getExpression().evaluate(ctx, isBody, previousResult.asJsonWithoutAnnotations())
				.flatMap(Value::toJsonNode)
				.flatMap(expressionResult -> handleExpressionResultFor(previousResult, expressionResult));
	}

	private Flux<ResultNode> handleExpressionResultFor(ArrayResultNode previousResult, JsonNode result) {
		if (result.isNumber()) {
			int index = result.asInt();
			List<AbstractAnnotatedJsonNode> nodes = previousResult.getNodes();
			if (index < 0 || index >= nodes.size()) {
				return Flux
						.error(new PolicyEvaluationException(String.format(EXPRESSION_ACCESS_INDEX_NOT_FOUND, index)));
			}
			return Flux.just(nodes.get(index));
		} else {
			return Flux.error(new PolicyEvaluationException(
					String.format(EXPRESSION_ACCESS_TYPE_MISMATCH, result.getNodeType())));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + ((getExpression() == null) ? 0 : getExpression().hash(imports));
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
