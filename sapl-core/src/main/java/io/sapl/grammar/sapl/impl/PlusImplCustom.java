/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class PlusImplCustom extends PlusImpl {

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<Optional<JsonNode>> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<Optional<JsonNode>> right = getRight().evaluate(ctx, isBody, relativeNode);
		return Flux.combineLatest(left, right, Tuples::of).distinctUntilChanged().flatMap(this::plus);
	}

	private Flux<Optional<JsonNode>> plus(Tuple2<Optional<JsonNode>, Optional<JsonNode>> tuple) {
		if (tuple.getT1().isPresent() && tuple.getT2().isPresent() && tuple.getT1().get().isNumber()
				&& tuple.getT2().get().isNumber()) {
			return Value.fluxOf(tuple.getT1().get().decimalValue().add(tuple.getT2().get().decimalValue()));
		}
		// The left or right value (or both) is/are not numeric. The plus operator is
		// therefore interpreted as a string concatenation operator.
		String left = tuple.getT1().orElseGet(() -> JSON.textNode("undefined")).asText();
		String right = tuple.getT2().orElseGet(() -> JSON.textNode("undefined")).asText();
		return Value.fluxOf(left.concat(right));
	}

}
