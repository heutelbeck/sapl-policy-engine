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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.sapl.interpreter.EvaluationContext;

public class EvaluateLiteralsValuesTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void evaluateNullLiteral() {
		expressionEvaluatesTo(CTX, "null", "null");
	}

	@Test
	public void evaluateTrueLiteral() {
		expressionEvaluatesTo(CTX, "true", "true");
	}

	@Test
	public void evaluateFalseLiteral() {
		expressionEvaluatesTo(CTX, "false", "false");
	}

	@Test
	public void evaluateStringLiteral() {
		expressionEvaluatesTo(CTX, "\"Otto\"", "\"Otto\"");
	}

	@Test
	public void evaluateNumberLiteral() {
		expressionEvaluatesTo(CTX, "666", "666");
	}

	@Test
	public void evaluateNumberLiteral2() {
		expressionEvaluatesTo(CTX, "1", "1.0");
	}

	@Test
	public void evaluateEmptyObject() {
		expressionEvaluatesTo(CTX, "{}", "{}");
	}

	@Test
	public void evaluateObject() {
		var json = "{ \"key1\" : null, \"key2\" : true }";
		expressionEvaluatesTo(CTX, json, json);
	}

	@Test
	public void evaluateEmptyArray() {
		expressionEvaluatesTo(CTX, "[]", "[]");
	}

	@Test
	public void evaluateArray() {
		var json = "[null,true,false]";
		expressionEvaluatesTo(CTX, json, json);
	}

}
