/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of the 'in-array' operation. It checks if a value
 * is contained in an array.
 *
 * Grammar: Comparison returns Expression: Prefixed (({ElementOf.left=current}
 * 'in') right=Prefixed)? ;
 */
public class ElementOfImplCustom extends ElementOfImpl {

	@Override
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		final Flux<Val> value = getLeft().evaluate(ctx, relativeNode);
		final Flux<Val> array = getRight().evaluate(ctx, relativeNode);
		return Flux.combineLatest(value, array, this::elementOf);
	}

	/**
	 * Checks if the value is contained in the array. 'undefined' is never contained
	 * in any array.
	 */
	private Val elementOf(Val needle, Val haystack) {
		if (needle.isError()) {
			return needle;
		}
		if (haystack.isError()) {
			return haystack;
		}
		if (needle.isUndefined() 
				|| haystack.isUndefined() 
				|| !haystack.isArray()) {
			return Val.FALSE;
		}
		ArrayNode array = (ArrayNode) haystack.get();
		for (JsonNode arrayItem : array) {
			// numerically equivalent numbers may be noted differently in JSON.
			// This equality is checked for here as well.
			if (needle.get().equals(arrayItem) 
					|| 
					(needle.isNumber() 
							&& arrayItem.isNumber()
					&& needle.get().decimalValue().compareTo(arrayItem.decimalValue()) == 0)) {
				return Val.TRUE;
			}
		}
		return Val.FALSE;
	}

}
