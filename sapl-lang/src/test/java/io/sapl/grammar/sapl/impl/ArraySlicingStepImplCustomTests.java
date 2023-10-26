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
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsError;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArraySlicingStepImplCustomTests {

    private static Stream<Arguments> errorExpressions() {
        // @formatter:off
		return Stream.of(
	 			// slicingPropagatesErrors
	 			Arguments.of("(1/0)[0:1]","Division by zero"),

	 			// applySlicingToNoArray
	 			Arguments.of("\"abc\"[0:1]", "Type mismatch. Expected an Array, but got: '\"abc\"'."),

	 			// applySlicingStepZeroErrors
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][1:5:0]", "Step must not be zero."),

	 			// filterErrorOnZeroStep
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[: :0] : mock.nil }", "Step must not be zero.")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("errorExpressions")
	void expressionReturnsError(String expression, String expected) {
		assertExpressionReturnsError(expression, expected);
	}

	private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
		// @formatter:off
		return Stream.of(
	 			// defaultsToIdentity
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][:]",
	 			             "[0,1,2,3,4,5,6,7,8,9]"),

	 			// useCaseTestTwoNull
	 			Arguments.of("[1,2,3,4,5][2:]",
	 			             "[3,4,5]"),

	 			// negativeToTest
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][7:-1]",
	 			             "[7,8]"),

	 			// applySlicingToArrayNodeNegativeFrom
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-3:9]",
	 			             "[7,8]"),

	 			// applySlicingToArrayWithFromGreaterThanToReturnsEmptyArray
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][4:1]",
	 			             "[]"),

	 			// applySlicingToArrayNodeWithoutTo
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][7:]",
	 			             "[7,8,9]"),

	 			// applySlicingToArrayNodeWithoutFrom
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][:3]",
	 			             "[0,1,2]"),

	 			// applySlicingToArrayNodeWithNegativeFrom
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-3:]",
	 			             "[7,8,9]"),

	 			// applySlicingToArrayNodeWithNegativeStep
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][: :-1]",
	 			             "[0,1,2,3,4,5,6,7,8,9]"),

	 			// applySlicingToArrayNodeWithNegativeStepAndNegativeFrom
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-2:6:-1]",
	 			             "[]"),

	 			// applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-2:-5:-1]",
	 			             "[]"),

	 			// applySlicingToArrayWithNegativeStepAndToGreaterThanFrom
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][1:5:-1]",
	 			             "[1,2,3,4]"),

	 			// applySlicingToResultArray
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][3:6]",
	 					     "[3,4,5]"),

	 			// applySlicingToArrayWithThreeStep
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][: :3]",
	 			             "[0,3,6,9]"),

	 			// applySlicingToArrayWithNegativeStep
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][: :-3]",
	 			             "[1,4,7]"),

	 			// filterDefaultsToIdentity
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[:] : mock.nil }",
	 			             "[null,null,null,null,null,null,null,null,null,null]"),

	 			// filterDefaultsToIdentityDescendStep
	 			Arguments.of("[[10,11,12,13,14],0,1,2,3,4,5,6,7,8,9] |- { @[:][-2:] : mock.nil }",
	 			             "[[10,11,12,null,null],0,1,2,3,4,5,6,7,8,9]"),

	 			// filterEmptyArray
	 			Arguments.of("[] |- { @[:] : mock.nil }",
	 			             "[]"),

	 			// filterNegativeStepArray
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[: :-2] : mock.nil }",
	 			             "[null,1,null,3,null,5,null,7,null,9]"),

	 			// filterNegativeTo
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[:-2] : mock.nil }",
	 			             "[null,null,null,null,null,null,null,null,8,9]")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
