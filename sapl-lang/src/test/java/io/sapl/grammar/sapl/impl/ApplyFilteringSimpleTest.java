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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class ApplyFilteringSimpleTest {

	@Test
	void filterPropagatesError() {
		assertExpressionReturnsErrors("(10/0) |- filter.remove");
	}

	@Test
	void filterUndefined() {
		assertExpressionReturnsErrors("undefined |- filter.remove");
	}

	@Test
	void removeNoEach() {
		var expression = "{} |- filter.remove";
		var expected = Val.UNDEFINED;
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void removeEachNoArray() {
		assertExpressionReturnsErrors("{} |- each filter.remove");
	}

	@Test
	void removeEachArray() {
		var expression = "[null] |- each filter.remove";
		var expected = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void emptyStringNoEach() {
		var expression = "[] |- mock.emptyString";
		var expected = "\"\"";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void emptyStringEach() {
		var expression = "[ null, 5 ] |- each mock.emptyString(null)";
		var expected = "[ \"\", \"\" ]";
		assertExpressionEvaluatesTo(expression, expected);
	}

}
