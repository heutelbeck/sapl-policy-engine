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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.jupiter.api.Test;

class EvaluateLiteralsValuesTest {

	@Test
	void evaluateNullLiteral() {
		expressionEvaluatesTo("null", "null");
	}

	@Test
	void evaluateTrueLiteral() {
		expressionEvaluatesTo("true", "true");
	}

	@Test
	void evaluateFalseLiteral() {
		expressionEvaluatesTo("false", "false");
	}

	@Test
	void evaluateStringLiteral() {
		expressionEvaluatesTo("\"Otto\"", "\"Otto\"");
	}

	@Test
	void evaluateNumberLiteral() {
		expressionEvaluatesTo("666", "666");
	}

	@Test
	void evaluateNumberLiteral2() {
		expressionEvaluatesTo("1", "1.0");
	}

	@Test
	void evaluateEmptyObject() {
		expressionEvaluatesTo("{}", "{}");
	}

	@Test
	void evaluateObject() {
		var json = "{ \"key1\" : null, \"key2\" : true }";
		expressionEvaluatesTo(json, json);
	}

	@Test
	void evaluateEmptyArray() {
		expressionEvaluatesTo("[]", "[]");
	}

	@Test
	void evaluateArray() {
		var json = "[null,true,false]";
		expressionEvaluatesTo(json, json);
	}

}
