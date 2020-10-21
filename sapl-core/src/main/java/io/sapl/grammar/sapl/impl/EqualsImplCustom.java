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
import reactor.core.publisher.Flux;

/**
 * Checks for equality of two values.
 *
 * Grammar: Comparison returns Expression: Prefixed (({Equals.left=current}
 * '==') right=Prefixed)? ;
 */
public class EqualsImplCustom extends EqualsImpl {

	@Override
	public Flux<Val> evaluate(EvaluationContext ctx, boolean isBody, Val relativeNode) {
		final Flux<Val> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<Val> right = getRight().evaluate(ctx, isBody, relativeNode);
		return Flux.combineLatest(left, right, this::equals).distinctUntilChanged();
	}

	/**
	 * Compares two values
	 * 
	 * @param left  a value
	 * @param right a value
	 * @return true if both values are equal
	 */
	private Val equals(Val left, Val right) {
		// if both values are undefined, they are equal
		if (left.isUndefined() && right.isUndefined()) {
			return Val.ofTrue();
		}
		// only one value is undefined the two values are not equal
		if (left.isUndefined() || right.isUndefined()) {
			return Val.ofFalse();
		}
		// if both values are numbers do a numerical comparison, as they may be
		// represented differently in JSON
		if (left.get().isNumber() && right.get().isNumber()) {
			return Val.of(left.get().decimalValue().compareTo(right.get().decimalValue()) == 0);
		} else {
			// else do a deep comparison
			return Val.of(left.get().equals(right.get()));
		}
	}

}
