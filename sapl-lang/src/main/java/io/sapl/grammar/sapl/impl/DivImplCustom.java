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

import static io.sapl.grammar.sapl.impl.OperatorUtil.arithmeticOperator;

import java.math.BigDecimal;

import io.sapl.api.interpreter.Val;
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
	public Flux<Val> evaluate() {
		return arithmeticOperator(this, this::divide);
	}

	private Val divide(Val dividend, Val divisor) {
		if (divisor.decimalValue().compareTo(BigDecimal.ZERO) == 0)
			return Val.error("Division by zero");
		return Val.of(dividend.decimalValue().divide(divisor.decimalValue()));
	}

}
