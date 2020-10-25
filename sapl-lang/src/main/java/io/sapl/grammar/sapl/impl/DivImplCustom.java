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

import java.math.BigDecimal;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the numerical division operator, written as '/' in Expressions.
 *
 * Grammar: Multiplication returns Expression: Comparison (({Multi.left=current}
 * '*' | {Div.left=current} '/' | {And.left=current} '&amp;&amp;' | '&amp;'
 * {EagerAnd.left=current}) right=Comparison)* ;
 */
public class DivImplCustom extends DivImpl {

	@Override
	public Flux<Val> evaluate(EvaluationContext ctx, boolean isBody, Val relativeNode) {
		final Flux<BigDecimal> divident = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Val::toBigDecimal);
		final Flux<BigDecimal> divisor = getRight().evaluate(ctx, isBody, relativeNode).flatMap(Val::toBigDecimal);
		return Flux.combineLatest(divident, divisor, BigDecimal::divide).map(Val::of).distinctUntilChanged();
	}

}
