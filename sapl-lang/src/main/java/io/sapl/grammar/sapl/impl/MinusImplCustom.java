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

public class MinusImplCustom extends MinusImpl {

	@Override
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		var leftFlux = getLeft().evaluate(ctx, relativeNode).map(Val::requireBigDecimal);
		var rightFlux = getRight().evaluate(ctx, relativeNode).map(Val::requireBigDecimal);
		return Flux.combineLatest(leftFlux, rightFlux, this::subtract);
	}

	private Val subtract(Val left, Val right) {
		if (left.isError()) {
			return left;
		}
		if (right.isError()) {
			return right;
		}
		return Val.of(left.getBigDecimal().subtract(right.getBigDecimal()));
	}
}
