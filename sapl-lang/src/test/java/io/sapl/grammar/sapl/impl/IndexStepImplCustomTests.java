/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IndexStepImplCustomTests {

    @Test
    void applyIndexStepToNonArrayFails() {
        assertExpressionReturnsErrors("undefined[0]");
    }

    private static Stream<Arguments> expressionTestCases() {
        // @formatter:off
		return Stream.of(
	 			// applyPositiveExistingToArrayNode
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][5]", "5"),

	 			// applyPositiveExistingToArrayNodeUpperEdge
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][9]", "9"),

	 			// applyPositiveExistingToArrayNodeLowerEdge
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][0]", "0"),

	 			// applyPositiveExistingToArrayNodeLowerEdgeNegative
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-1]", "9"),

	 			// applyPositiveExistingToArrayNodeUpperEdgeNegative
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-10]", "0"),

	 			// applyNegativeExistingToArrayNode
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-2]", "8"),

	 			// filterOutOfBounds1
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[-12] : mock.nil }",
	 			             "[0,1,2,3,4,5,6,7,8,9]"),

	 			// filterElementsInDescend
	 			Arguments.of("[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[3][2] : mock.nil }",
	 			             "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,null,3]]"),

	 			// filterOutOfBounds2
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9] |- { @[12] : mock.nil }",
	 			             "[0,1,2,3,4,5,6,7,8,9]"),

	 			// filterNonArray
	 			Arguments.of("666 |- { @[2] : mock.nil }", "666")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("expressionTestCases")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}	

	private static Stream<Arguments> errorExpressions() {
		// @formatter:off
		return Stream.of(
	 			// applyPositiveOutOfBoundsToArrayNode1
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][100]", "Index out of bounds. Index must be between 0 and 10, was: 100"),

	 			// applyPositiveOutOfBoundsToArrayNodeUpperEdge
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][10]", "Index out of bounds. Index must be between 0 and 10, was: 10"),

	 			// applyPositiveOutOfBoundsToArrayLowerUpperEdgeNegative
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-11]", "Index out of bounds. Index must be between 0 and 10, was: -1"),

	 			// applyNegativeOutOfBoundsToArrayNode
	 			Arguments.of("[0,1,2,3,4,5,6,7,8,9][-12]", "Index out of bounds. Index must be between 0 and 10, was: -2")
	 		);
		// @formater:on
	}
	
	@ParameterizedTest
	@MethodSource("errorExpressions")
	void expressionReturnsError(String expression, String expected) {
		assertExpressionReturnsError(expression, expected);
	}
}
