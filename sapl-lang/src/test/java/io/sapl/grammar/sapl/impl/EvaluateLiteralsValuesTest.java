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

import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class EvaluateLiteralsValuesTest {

	private EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void evaluateNullLiteral() throws IOException {
		var expression = ParserUtil.expression("null");
		var expected = Val.NULL;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateTrueLiteral() throws IOException {
		var expression = ParserUtil.expression("true");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateFalseLiteral() throws IOException {
		var expression = ParserUtil.expression("false");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateStringLiteral() throws IOException {
		var expression = ParserUtil.expression("\"Otto\"");
		var expected = Val.of("Otto");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateNumberLiteral() throws IOException {
		var expression = ParserUtil.expression("666");
		var expected = Val.of(666);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateEmptyObject() throws IOException {
		var expression = ParserUtil.expression("{}");
		var expected = Val.ofEmptyObject();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateObject() throws IOException {
		var json = "{ \"key1\" : null, \"key2\" : true }";
		var expression = ParserUtil.expression(json);
		var expected = Val.ofJson(json);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateEmptyArray() throws IOException {
		var expression = ParserUtil.expression("[]");
		var expected = Val.ofEmptyArray();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateArray() throws IOException {
		var json = "[null,true,false]";
		var expression = ParserUtil.expression(json);
		var expected = Val.ofJson(json);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

}
