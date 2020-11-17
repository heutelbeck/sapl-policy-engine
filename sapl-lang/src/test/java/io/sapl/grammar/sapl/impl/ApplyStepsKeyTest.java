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

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsKeyTest {

	private EvaluationContext ctx;

	@Before
	public void before() {
		ctx = MockUtil.mockEvaluationContext();
	}

	@Test
	public void keyStepPropagatesErrors() throws IOException {
		var expression = ParserUtil.expression("(10/0).key");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void keyStepToNonObjectUndefined() throws IOException {
		var expression = ParserUtil.expression("true.key");
		var expected = Val.UNDEFINED;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void keyStepToEmptyObject() throws IOException {
		var expression = ParserUtil.expression("{}.key");
		var expected = Val.UNDEFINED;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void keyStepToObject() throws IOException {
		var expression = ParserUtil.expression("{\"key\" : true}.key");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void keyStepToArray() throws IOException {
		var expression = ParserUtil.expression("[{\"key\" : true},{\"key\": 123}].key");
		var expected = Val.ofJson("[true,123]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	// FIXME: {"key",123} should be rejected at parse time
	@Test
	public void keyStepToArrayNoMatch() throws IOException {
		var expression = ParserUtil.expression("[{\"key\" : true},{\"key\": 123}].x");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterNonObjectOrArray() throws IOException {
		var expression = ParserUtil.expression("\"Gudrun\" |- { @.key : nil }");
		var expected = Val.ofJson("\"Gudrun\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterObject() throws IOException {
		var expression = ParserUtil.expression("{\"key\" : true, \"other\" : false} |- { @.key : nil}");
		var expected = Val.ofJson("{\"key\" : null, \"other\" : false}");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterObjectDescend() throws IOException {
		var expression = ParserUtil.expression("{\"key\" : { \"key2\" : true}, \"other\" : false} |- { @.key.key2 : nil}");
		var expected = Val.ofJson("{\"key\" : {\"key2\" : null }, \"other\" : false}");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterArray() throws IOException {
		var expression = ParserUtil.expression("[ {\"key\" : true, \"other\" : false} , false ] |- { @.key : nil}");
		var expected = Val.ofJson("[ {\"key\" : null, \"other\" : false} , false ]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterEmptyrray() throws IOException {
		var expression = ParserUtil.expression("[] |- { @.key : nil}");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

}
