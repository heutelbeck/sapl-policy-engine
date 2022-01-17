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
import reactor.core.publisher.Flux;

/**
 * Implements the lazy boolean OR operator, written as '||' in Expressions.
 *
 * Grammar: Addition returns Expression: Multiplication (({Or.left=current}
 * '||') right=Multiplication)* ;
 */
public class OrImplCustom extends OrImpl {

	private static final String LAZY_OPERATOR_IN_TARGET = "Lazy OR operator is not allowed in the target";

	@Override
	public Flux<Val> evaluate() {
		if (TargetExpressionUtil.isInTargetExpression(this)) {
			// lazy evaluation is not allowed in target expressions.
			return Val.errorFlux(LAZY_OPERATOR_IN_TARGET);
		}
		var left = getLeft().evaluate().map(Val::requireBoolean);
		return left.switchMap(leftResult -> {
			if (leftResult.isError()) {
				return Flux.just(leftResult);
			}
			// Lazy evaluation of the right expression
			if (!leftResult.getBoolean()) {
				return getRight().evaluate().map(Val::requireBoolean);
			}
			return Flux.just(Val.TRUE);
		});
	}

}
