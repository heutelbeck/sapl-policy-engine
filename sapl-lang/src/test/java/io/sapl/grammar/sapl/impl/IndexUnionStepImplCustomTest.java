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

public class IndexUnionStepImplCustomTest {

	private EvaluationContext ctx;

	@Before
	public void before() {
		ctx = MockUtil.mockEvaluationContext();
	}

	@Test
	public void applyIndexUnionStepToNonArrayFails() throws IOException {
		var expression = ParserUtil.expression("(undefined)[1,2]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void applyToArray() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][0,1,-2,10,-10]");
		var expected = Val.ofJson("[0,1,8]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applyToArrayOutOfBounds() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][100,-100]");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterNegativeStepArray() throws IOException {
		var expression = ParserUtil.expression("\"Otto\" |- { @[1,2,3] : nil }");
		var expected = Val.ofJson("\"Otto\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterElementsInArray() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9] |- { @[0,1,-2,10,-10] : nil }");
		var expected = Val.ofJson("[null,null,2,3,4,5,6,7,null,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterElementsInDescend() throws IOException {
		var expression = ParserUtil.expression("[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[1,3][3] : nil }");
		var expected = Val.ofJson("[[0,1,2,3],[0,1,2,null],[0,1,2,3],[0,1,2,null]]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}
}
