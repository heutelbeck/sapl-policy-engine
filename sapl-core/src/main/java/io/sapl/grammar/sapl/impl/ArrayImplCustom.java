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
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implementation of an array in SAPL.
 * 
 * Grammar: Array returns Value: {Array} '[' (items+=Expression (','
 * items+=Expression)*)? ']' ;
 */
public class ArrayImplCustom extends ArrayImpl {

	/**
	 * The semantics of evaluating an array is as follows:
	 * 
	 * An array may contain a list of expressions. To get the values of the
	 * individual expressions, these have to be recursively evaluated.
	 * 
	 * Returning a Flux this means to subscribe to all expression result Fluxes and to
	 * combineLatest into a new array each time one of the expression Fluxes emits a
	 * new value.
	 */
	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final List<Flux<JsonNode>> itemFluxes = new ArrayList<>(getItems().size());
		for (Expression item : getItems()) {
			itemFluxes.add(item.evaluate(ctx, isBody, relativeNode).flatMap(Value::toJsonNode));
		}
		return Flux.combineLatest(itemFluxes, Function.identity()).map(this::collectValuesToArrayNode)
				.map(Optional::of);
	}

	/**
	 * Collects a concrete evaluation of all expressions in the array into a single
	 * Array. We do not allow for returning 'undefined'/Optional.empty() as fields
	 * in the array. At runtime, this is primarily a constraint due to to usage of
	 * Jackson JsonNodes which do not have a concept of 'undefined'. Also as we want
	 * to return valid JSON values 'undefined' may not occur anywhere.
	 */
	private JsonNode collectValuesToArrayNode(Object[] values) {
		final ArrayNode resultArr = JsonNodeFactory.instance.arrayNode();
		for (Object untypedValue : values) {
			JsonNode value = (JsonNode) untypedValue;
			resultArr.add(value);
		}
		return resultArr;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		for (Expression expression : getItems()) {
			hash = 37 * hash + ((expression == null) ? 0 : expression.hash(imports));
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
