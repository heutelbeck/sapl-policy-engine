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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.EvaluationContext;

class EvaluateLiteralsValuesTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	void evaluateNullLiteral() {
		expressionEvaluatesTo(CTX, "null", "null");
	}

	@Test
	void evaluateTrueLiteral() {
		expressionEvaluatesTo(CTX, "true", "true");
	}

	@Test
	void evaluateFalseLiteral() {
		expressionEvaluatesTo(CTX, "false", "false");
	}

	@Test
	void evaluateStringLiteral() {
		expressionEvaluatesTo(CTX, "\"Otto\"", "\"Otto\"");
	}

	@Test
	void evaluateNumberLiteral() {
		expressionEvaluatesTo(CTX, "666", "666");
	}

	@Test
	void evaluateNumberLiteral2() {
		expressionEvaluatesTo(CTX, "1", "1.0");
	}

	@Test
	void evaluateEmptyObject() {
		expressionEvaluatesTo(CTX, "{}", "{}");
	}

	@Test
	void evaluateObject() {
		var json = "{ \"key1\" : null, \"key2\" : true }";
		expressionEvaluatesTo(CTX, json, json);
	}

	@Test
	void evaluateEmptyArray() {
		expressionEvaluatesTo(CTX, "[]", "[]");
	}

	@Test
	void evaluateArray() {
		var json = "[null,true,false]";
		expressionEvaluatesTo(CTX, json, json);
	}

}
