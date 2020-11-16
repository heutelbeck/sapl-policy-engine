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
package io.sapl.grammar.tests;

import java.io.IOException;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class EvaluateStepsFilterSubtemplateTest {

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFilteringContext();
	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void basicExpressionWithStep() throws IOException {
		// [ null ].[0] == null
		Expression expression = ParserUtil.expression("[ null ].[0]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.ofNull()).verifyComplete();
	}

	@Test
	public void basicExpressionWithFilter() throws IOException {
		// null |- EMPTY_STRING == ""
		Expression expression = ParserUtil.expression("null |- EMPTY_STRING");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.of("")).verifyComplete();
	}

	@Test
	public void subtemplateNoArray() throws IOException {
		// null :: { "name" : @ } == { "name" : null }
		Expression expression = ParserUtil.expression("null :: { \"name\" : @ }");
		Val expectedResult = Val.ofJson("{ \"name\" : null }");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void subtemplateArray() throws IOException {
		// [true, false] :: null == [ null,null ]
		Expression expression = ParserUtil.expression("[true, false] :: null");
		Val expectedResult = Val.ofJson("[ null,null ]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

}
