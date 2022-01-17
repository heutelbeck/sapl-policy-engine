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
package io.sapl.grammar.sapl.impl.util;

import java.io.IOException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import reactor.test.StepVerifier;

public class TestUtil {

	public static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = SaplFactory.eINSTANCE.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}

	public static void expressionEvaluatesTo(String expression, String... expected) {
		try {
			var expectations = new Val[expected.length];
			var i            = 0;
			for (var ex : expected) {
				expectations[i++] = Val.ofJson(ex);
			}
			expressionEvaluatesTo(ParserUtil.expression(expression), expectations);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionEvaluatesTo(String expression, Val... expected) {
		try {
			expressionEvaluatesTo(ParserUtil.expression(expression), expected);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionErrors(String expression) {
		try {
			expressionErrors(ParserUtil.expression(expression));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void expressionEvaluatesTo(Expression expression, Val... expected) {
		StepVerifier.create(expression.evaluate().contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNext(expected).verifyComplete();
	}

	public static void expressionErrors(Expression expression) {
		StepVerifier.create(expression.evaluate().contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNextMatches(Val::isError).verifyComplete();
	}

}
