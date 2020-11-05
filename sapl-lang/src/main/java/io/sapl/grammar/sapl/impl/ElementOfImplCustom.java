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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
		return Flux.combineLatest(value, array, Tuples::of).map(this::elementOf).distinctUntilChanged();
	}

	/**
	 * Checks if the value is contained in the array. 'undefined' is never contained
	 * in any array.
	 * 
	 * @param tuple a tuple containing the value (T1) and the array (T2)
	 * @return true if the value is contained in the array
	 */
	private Val elementOf(Tuple2<Val, Val> tuple) {
		if (tuple.getT1().isUndefined() || tuple.getT2().isUndefined() || !tuple.getT2().get().isArray()) {
			return Val.ofFalse();
		}
		ArrayNode array = (ArrayNode) tuple.getT2().get();
		for (JsonNode arrayItem : array) {
			// numerically equivalent numbers may be noted differently in JSON.
			// This equality is checked for here as well.
			if (tuple.getT1().get().equals(arrayItem) || (tuple.getT1().get().isNumber() && arrayItem.isNumber()
					&& tuple.getT1().get().decimalValue().compareTo(arrayItem.decimalValue()) == 0)) {
				return Val.ofTrue();
			}
		}
		return Val.ofFalse();
	}

}
