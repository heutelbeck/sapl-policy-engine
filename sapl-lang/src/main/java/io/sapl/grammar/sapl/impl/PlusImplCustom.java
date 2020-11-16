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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

public class PlusImplCustom extends PlusImpl {

	private static final String UNDEFINED = "undefined";

	@Override
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		final Flux<Val> left = getLeft().evaluate(ctx, relativeNode);
		final Flux<Val> right = getRight().evaluate(ctx, relativeNode);
		return Flux.combineLatest(left, right, this::plus);
	}

	private Val plus(Val left, Val right) {
		if (left.isError()) {
			return left;
		}
		if (right.isError()) {
			return right;
		}
		if (left.isDefined() && right.isDefined() && left.isNumber() && right.isNumber()) {
			return Val.of(left.get().decimalValue().add(right.get().decimalValue()));
		}
		// The left or right value (or both) is/are not numeric. The plus operator is
		// therefore interpreted as a string concatenation operator.
		String lStr = left.orElse(Val.JSON.textNode(UNDEFINED)).asText();
		String rStr = right.orElse(Val.JSON.textNode(UNDEFINED)).asText();
		return Val.of(lStr.concat(rStr));
	}

}
