/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.grammar.sapl.Expression;
import reactor.core.publisher.Mono;

public class PolicyElementImplCustom extends PolicyElementImpl {

	private static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

	/**
	 * Checks whether the policy element (policy set or policy) matches an
	 * authorization subscription by evaluating the element's target expression. An
	 * import mapping and custom variables can be provided.
	 * 
	 * @return {@code true} if the target expression evaluates to {@code true},
	 *         {@code false} otherwise. @ in case there is an error while evaluating
	 *         the target expression
	 */
	@Override
	public Mono<Val> matches() {
		final Expression targetExpression = getTargetExpression();
		if (targetExpression == null) {
			return Mono.just(Val.TRUE);
		}
		return targetExpression.evaluate(Val.UNDEFINED).next().defaultIfEmpty(Val.FALSE).flatMap(result -> {
			if (result.isError() || !result.isBoolean()) {
				return Val.errorMono(CONDITION_NOT_BOOLEAN, result);
			}
			return Mono.just(result);
		});
	}

}
