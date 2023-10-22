/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.arithmeticOperator;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.LessEquals;
import reactor.core.publisher.Flux;

/**
 * Checks for a left value being less than or equal to a right value.
 * <p>
 * Grammar: {@code Comparison returns Expression: Prefixed
 * (({LessEquals.left=current} '&lt;=') right=Prefixed)? ;}
 */
public class LessEqualsImplCustom extends LessEqualsImpl {

	@Override
	public Flux<Val> evaluate() {
		return arithmeticOperator(this, this::lessOrEqual);
	}

	private Val lessOrEqual(Val left, Val right) {
		return Val.of(left.decimalValue().compareTo(right.decimalValue()) <= 0).withTrace(LessEquals.class,
				Map.of(Trace.LEFT, left, Trace.RIGHT, right));
	}

}
