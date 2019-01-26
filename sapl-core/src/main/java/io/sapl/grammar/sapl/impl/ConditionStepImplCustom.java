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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class ConditionStepImplCustom extends io.sapl.grammar.sapl.impl.ConditionStepImpl {

	private static final String CONDITION_ACCESS_TYPE_MISMATCH = "Type mismatch. Condition access is only possible for array or object, but got '%s'.";

	private static final int HASH_PRIME_10 = 53;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
        final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
        final JsonNode previousResultNode = previousResult.getNode();
        if (previousResultNode.isArray()) {
            final List<Flux<JsonNode>> itemFluxes = new ArrayList<>(previousResultNode.size());
            IntStream.range(0, previousResultNode.size()).forEach(idx -> {
                itemFluxes.add(getExpression().evaluate(ctx, isBody, previousResultNode.get(idx)));
            });
            // the indices of the elements in the previousResultNode array correspond to the indices of the flux results, because combineLatest()
            // preserves the order of the given list of fluxes in the results array passed to the combinator function
            return Flux.combineLatest(itemFluxes, results -> {
                IntStream.range(0, results.length).forEach(idx -> {
                    final JsonNode result = (JsonNode) results[idx];
                    if (result.isBoolean() && result.asBoolean()) {
                        resultList.add(new JsonNodeWithParentArray(previousResultNode.get(idx), previousResultNode, idx));
                    }
                });
                return new ArrayResultNode(resultList);
            });
        } else if (previousResultNode.isObject()) {
            final List<String> fieldNames = new ArrayList<>();
            final List<JsonNode> fieldValues = new ArrayList<>();
            final List<Flux<JsonNode>> valueFluxes = new ArrayList<>();
            final Iterator<String> it = previousResultNode.fieldNames();
            while (it.hasNext()) {
                final String fieldName = it.next();
                final JsonNode fieldValue = previousResultNode.get(fieldName);
                fieldNames.add(fieldName);
                fieldValues.add(fieldValue);
                valueFluxes.add(getExpression().evaluate(ctx, isBody, fieldValue));
            }
            // the indices of the elements in the fieldNames list and the fieldValues list correspond to the indices of the flux results,
            // because combineLatest() preserves the order of the given list of fluxes in the results array passed to the combinator function
            return Flux.combineLatest(valueFluxes, results -> {
                IntStream.range(0, results.length).forEach(idx -> {
                    final JsonNode result = (JsonNode) results[idx];
                    if (result.isBoolean() && result.asBoolean()) {
                        resultList.add(new JsonNodeWithParentObject(fieldValues.get(idx), previousResultNode, fieldNames.get(idx)));
                    }
                });
                return new ArrayResultNode(resultList);
            });
        } else {
            final JsonNodeType previousNodeType = previousResult.getNode().getNodeType();
            return Flux.error(new PolicyEvaluationException(String.format(CONDITION_ACCESS_TYPE_MISMATCH, previousNodeType)));
        }
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
        final List<AbstractAnnotatedJsonNode> resultNodes = new ArrayList<>(previousResult.getNodes().size());
        final List<Flux<JsonNode>> itemFluxes = new ArrayList<>(previousResult.getNodes().size());
        for (AbstractAnnotatedJsonNode resultNode : previousResult) {
            resultNodes.add(resultNode);
            itemFluxes.add(getExpression().evaluate(ctx, isBody, resultNode.getNode()));
        }
        final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
        // the indices of the elements in the resultNodes list correspond to the indices of the flux results, because combineLatest() preserves
        // the order of the given list of fluxes in the results array passed to the combinator function
        return Flux.combineLatest(itemFluxes, results -> {
            IntStream.range(0, results.length).forEach(idx -> {
                final JsonNode result = (JsonNode) results[idx];
                if (result.isBoolean() && result.asBoolean()) {
                    resultList.add(resultNodes.get(idx));
                }
            });
            return new ArrayResultNode(resultList);
        });
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
