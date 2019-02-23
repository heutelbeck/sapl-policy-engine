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

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * 
 * Implementation of an array in SAPL.
 * 
 * Grammar: Array returns Value: {Array} '[' (items+=Expression (','
 * items+=Expression)*)? ']' ;
 *
 */
public class ArrayImplCustom extends io.sapl.grammar.sapl.impl.ArrayImpl {

	private static final String CANNOT_ADD_UNDEFINED_VALUE_TO_A_JSON_ARRAY = "Cannot add undefined value to a JSON array.";
	private static final int HASH_PRIME_06 = 37;
	private static final int INIT_PRIME_02 = 5;

	/**
	 * 
	 * The semantics of evaluation an array is as follows:
	 * 
	 * An Array may contains a list of expressions. To get to the values of the
	 * individual expressions, these have to be recursively be evaluate.
	 * 
	 * As a Flux this means to subscribe to all expression result Fluxes and to
	 * coblineLatest into a new Array each time one of the expression Fluxes emits a
	 * new value.
	 * 
	 */
	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final List<Flux<Optional<JsonNode>>> itemFluxes = new ArrayList<>(getItems().size());
		for (Expression item : getItems()) {
			itemFluxes.add(item.evaluate(ctx, isBody, relativeNode));
		}
		return Flux.combineLatest(itemFluxes, this::collectValuesToArrayNode);

	}

	/**
	 * Collects a concrete evaluation of all expressions in the array into a single
	 * Array. We do not allow for returning 'undefined'/Optional.empty() as fields
	 * in the array. At runtime, this is primarily a constraint due to toe usage of
	 * Jackson JsonNodes which do not have a concept of 'undefined'. Also as we want
	 * to return valid JSON values 'undefined' may not occur anywhere.
	 */
	private Optional<JsonNode> collectValuesToArrayNode(Object[] results) {
		final ArrayNode resultArr = JsonNodeFactory.instance.arrayNode();
		for (Object result : results) {
			@SuppressWarnings("unchecked")
			Optional<JsonNode> r = (Optional<JsonNode>) result;
			resultArr.add(r.orElseThrow(() -> Exceptions
					.propagate(new PolicyEvaluationException(CANNOT_ADD_UNDEFINED_VALUE_TO_A_JSON_ARRAY))));
		}
		return Optional.of(resultArr);
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
