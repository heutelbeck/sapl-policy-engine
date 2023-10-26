/*
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
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.interpreter.Val;

class ApplyStepsExpressionTests {
    @ParameterizedTest
    // @formatter:off
	@ValueSource(strings = {
		// expressionStepPropagatesErrors1
		"[][(10/0)]",
		// expressionStepPropagatesErrors2
		"[(10/0)][(2+2)]",
		// expressionStepOutOfBounds1
		"[1,2,3][(1+100)]",
		// expressionStepOutOfBounds2
		"[1,2,3][(1 - 100)]",
		// expressionStepOutOfBounds3
		"[1,2,3][(1 - 100)]",
		// applyExpressionStepToNonObjectNonArrayFails
		"undefined[(1 + 1)]",
		// expressionEvaluatesToBooleanAndFails
		"[1,2,3][(true)]",
		// applyToArrayWithTextualExpressionResult
		"[0,1,2,3,4,5,6,7,8,9][(\"key\")]",
		// applyToObjectWithNumericalResult
		"{ \"key\" : true }[(5+2)]",
		// applyToObjectWithError
		"{ \"key\" : true }[(10/0)]",
		// filterTypeMismatch1
		"[ [4,1,2,3] ] |- { @[(false)] : filter.remove }",
		// filterTypeMismatch2
		"[ [4,1,2,3] ] |- { @[(\"a\")] : filter.remove }",
		// filterTypeMismatch3
		"{ \"a\": [4,1,2,3] } |- { @[(123)] : filter.remove }"
	}) 
	// @formatter:on
    void expressionEvaluatesToErrors(String expression) {
        assertExpressionReturnsErrors(expression);
    }

    @Test
    void applyToObjectWithTextualResultNonExistingKey() {
        assertExpressionEvaluatesTo("{ \"key\" : true }[(\"no_ke\"+\"y\")]", Val.UNDEFINED);
    }

    private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
				// applyToArrayWithNumberExpressionResult
				Arguments.of("[0,1,2,3,4,5,6,7,8,9][(2+3)]", "5"),
				
	 			// applyToObjectWithTextualResult
	 			Arguments.of("{ \"key\" : true }[(\"ke\"+\"y\")]", "true"),

	 			// filterNonArrayNonObject
	 			Arguments.of("123 |- { @[(1+1)] : mock.nil }", "123"),

	 			// removeExpressionStepArray
	 			Arguments.of("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }",
	 					     "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]"),

	 			// removeExpressionStepObject
	 			Arguments.of("{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }",
	 					     "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
