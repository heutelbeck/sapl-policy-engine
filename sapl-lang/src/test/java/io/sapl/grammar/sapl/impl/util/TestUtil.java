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
package io.sapl.grammar.sapl.impl.util;

import java.io.IOException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class TestUtil {

	public static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = SaplFactory.eINSTANCE.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}

	public static void expressionEvaluatesTo(EvaluationContext ctx, String expression, String... expected) {
		try {
			var expectations = new Val[expected.length];
			var i = 0;
			for (var ex : expected) {
				expectations[i++] = Val.ofJson(ex);
			}
			expressionEvaluatesTo(ctx, ParserUtil.expression(expression), expectations);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionEvaluatesTo(EvaluationContext ctx, String expression, Val... expected) {
		try {
			expressionEvaluatesTo(ctx, ParserUtil.expression(expression), expected);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionErrors(EvaluationContext ctx, String expression) {
		try {
			expressionErrors(ctx, ParserUtil.expression(expression));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionEvaluatesTo(EvaluationContext ctx, Expression expression, Val... expected) {
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNext(expected).verifyComplete();
	}

	public static void expressionErrors(EvaluationContext ctx, Expression expression) {
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}
}
