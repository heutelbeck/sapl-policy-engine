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

public class AttributeUnionStepImplCustomTest {

	private EvaluationContext ctx;

	@Before
	public void before() {
		ctx = MockUtil.mockEvaluationContext();
	}

	@Test
	public void applySlicingToNonObject() throws IOException {
		var expression = ParserUtil.expression("\"Otto\"['key1','key2']");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void applyToEmptyObject() throws IOException {
		var expression = ParserUtil.expression("{}['key1','key2']");
		var expected = Val.ofEmptyArray();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applyToObject() throws IOException {
		var expression = ParserUtil
				.expression("{ \"key1\" : null, \"key2\" : true,  \"key3\" : false }['key3','key2']");
		var expected = Val.ofJson("[ true, false ]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applyFilterToNonObject() throws IOException {
		var expression = ParserUtil.expression("\"Otto\" |- { @['key1','key2'] : nil }");
		var expected = Val.of("Otto");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterElementsInObject() throws IOException {
		var expression = ParserUtil
				.expression("{ \"key1\" : 1, \"key2\" : 2,  \"key3\" : 3 } |- { @['key3','key1'] : nil }");
		var expected = Val.ofJson("{ \"key1\" : null, \"key2\" : 2,  \"key3\" : null }");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterElementsInDescend() throws IOException {
		var expression = ParserUtil.expression(
				"{ \"key1\" : [1,2,3], \"key2\" : [1,2,3],  \"key3\" : [1,2,3] } |- { @['key3','key1'][2] : nil }");
		var expected = Val.ofJson("{ \"key1\" : [1,2,null], \"key2\" : [1,2,3],  \"key3\" : [1,2,null] }");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}
}
