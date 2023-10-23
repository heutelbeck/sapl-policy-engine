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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplyStepsRecursiveIndexTests {

	@Test
	void recursiveIndexStepPropagatesErrors() {
		assertExpressionReturnsErrors("(10/0)..[5]");
	}

	private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
		// @formatter:off
		return Stream.of(
				// recursiveIndexStepOnUndefinedEmpty
	 			Arguments.of("undefined..[2]", "[]"),

	 			// applyToNull
	 			Arguments.of("null..[2]", "[]"),

	 			// applyIndex1
	 			Arguments.of("[ [1,2,3], [4,5,6,7] ]..[1]", "[[4,5,6,7],2,5]"),

	 			// applyIndex2
	 			Arguments.of("[ [1,2,3], [4,5,6,7] ]..[2]", "[3,6]"),

	 			// applyIndex3
	 			Arguments.of("[ [1,2,3], [4,5,6,7] ]..[-1]", "[[4,5,6,7],3,7]"),
	
	 			// applyIndex4
	 			Arguments.of("[ [1,2,3], [4,5,6,7] ]..[-4]", "[4]"),
	 			
	 			// filterApplyIndex
	 			Arguments.of("[ [1,2,3], [4,5,6,7] ] |- { @..[-4] : mock.nil }", "[ [1,2,3], [null,5,6,7] ]"),

	 			// applyToObject
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..[0]",
	 			             "[ { \"key\" : \"value2\" }, 1 ]"),

	 			// removeRecursiveIndexStepObject
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
	 					   + "|- { @..[0] : filter.remove }",
	 					     "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value3\" } ], \"array2\" : [ 2, 3, 4, 5 ] }"),

	 			// removeRecursiveIndexStepObjectDescend
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ [ 1,2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [1,2,3], 2, 3, 4, 5 ] } "
	 					   + "|- { @..[0][0] : filter.remove }",
	 					     "{ \"key\" : \"value1\", \"array1\" : [ [ 2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [2,3], 2, 3, 4, 5 ] }")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
