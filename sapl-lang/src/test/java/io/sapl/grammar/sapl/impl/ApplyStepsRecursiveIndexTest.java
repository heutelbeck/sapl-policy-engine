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

class ApplyStepsRecursiveIndexTest {

	@Test
	void recursiveIndexStepPropagatesErrors() {
		assertExpressionReturnsErrors("(10/0)..[5]");
	}

	@Test
	void recursiveIndexStepOnUndefinedEmpty() {
		assertExpressionEvaluatesTo("undefined..[2]", "[]");
	}

	@Test
	void applyToNull() {
		assertExpressionEvaluatesTo("null..[2]", "[]");
	}

	@Test
	void applyIndex1() {
		assertExpressionEvaluatesTo("[ [1,2,3], [4,5,6,7] ]..[1]", "[[4,5,6,7],2,5]");
	}

	@Test
	void applyIndex2() {
		assertExpressionEvaluatesTo("[ [1,2,3], [4,5,6,7] ]..[2]", "[3,6]");
	}

	@Test
	void applyIndex3() {
		assertExpressionEvaluatesTo("[ [1,2,3], [4,5,6,7] ]..[-1]", "[[4,5,6,7],3,7]");
	}

	@Test
	void applyIndex4() {
		assertExpressionEvaluatesTo("[ [1,2,3], [4,5,6,7] ]..[-4]", "[4]");
	}

	@Test
	void filterApplyIndex() {
		assertExpressionEvaluatesTo("[ [1,2,3], [4,5,6,7] ] |- { @..[-4] : mock.nil }", "[ [1,2,3], [null,5,6,7] ]");
	}

	@Test
	void applyToObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..[0]";
		var expected   = "[ { \"key\" : \"value2\" }, 1 ]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void removeRecursiveIndexStepObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..[0] : filter.remove }";
		var expected   = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value3\" } ], \"array2\" : [ 2, 3, 4, 5 ] }";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void removeRecursiveIndexStepObjectDescend() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ [ 1,2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [1,2,3], 2, 3, 4, 5 ] } "
				+ "|- { @..[0][0] : filter.remove }";
		var expected   = "{ \"key\" : \"value1\", \"array1\" : [ [ 2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [2,3], 2, 3, 4, 5 ] }";
		assertExpressionEvaluatesTo(expression, expected);
	}

}
