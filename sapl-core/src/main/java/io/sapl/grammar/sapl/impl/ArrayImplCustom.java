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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;

public class ArrayImplCustom extends io.sapl.grammar.sapl.impl.ArrayImpl {

	private static final int HASH_PRIME_06 = 37;
	private static final int INIT_PRIME_02 = 5;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		final ArrayNode result = JsonNodeFactory.instance.arrayNode(getItems().size());
		for (Expression item : getItems()) {
			result.add(item.evaluate(ctx, isBody, relativeNode));
		}
		return result;
	}

	@Override
	public Flux<JsonNode> reactiveEvaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		final List<Flux<JsonNode>> itemFluxes = new ArrayList<>(getItems().size());
        for (Expression item : getItems()) {
            itemFluxes.add(item.reactiveEvaluate(ctx, isBody, relativeNode));
        }
        return Flux.combineLatest(itemFluxes, results -> {
            final ArrayNode resultArr = JsonNodeFactory.instance.arrayNode(results.length);
            for (Object result : results) {
                resultArr.add((JsonNode) result);
            }
            return resultArr;
        });
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_02;
		hash = HASH_PRIME_06 * hash + Objects.hashCode(getClass().getTypeName());
		for (Expression expression : getItems()) {
			hash = HASH_PRIME_06 * hash + ((expression == null) ? 0 : expression.hash(imports));
		}
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
		final ArrayImplCustom otherImpl = (ArrayImplCustom) other;
		if (getItems().size() != otherImpl.getItems().size()) {
			return false;
		}
		ListIterator<Expression> left = getItems().listIterator();
		ListIterator<Expression> right = otherImpl.getItems().listIterator();
		while (left.hasNext()) {
			Expression lhs = left.next();
			Expression rhs = right.next();
			if (!lhs.isEqualTo(rhs, otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
