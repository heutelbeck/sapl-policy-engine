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

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AttributeUnionStepImplCustomTests {

    @Test
    void applySlicingToNonObject() {
        var expression = "\"Otto\"['key1','key2']";
        assertExpressionReturnsErrors(expression);
    }

    private static Stream<Arguments> expressionTestCases() {
        // @formatter:off
		return Stream.of(
	 			// applyToEmptyObject
	 			Arguments.of("{}['key1','key2']",
	 					     "[]"),
	 			
	 			// applyToObject
	 			Arguments.of("{ \"key1\" : null, \"key2\" : true,  \"key3\" : false }['key3','key2']",
	 					     "[ true, false ]"),
	 			
	 			// applyFilterToNonObject
	 			Arguments.of("\"Otto\" |- { @['key1','key2'] : mock.nil }",
	 					     "\"Otto\""),
	 			
	 			// filterElementsInObject
	 			Arguments.of("{ \"key1\" : 1, \"key2\" : 2,  \"key3\" : 3 } |- { @['key3','key1'] : mock.nil }",
	 					     "{ \"key1\" : null, \"key2\" : 2,  \"key3\" : null }"),
	 			
	 			// filterElementsInDescend
	 			Arguments.of("{ \"key1\" : [1,2,3], \"key2\" : [1,2,3],  \"key3\" : [1,2,3] } |- { @['key3','key1'][2] : mock.nil }",
	 					     "{ \"key1\" : [1,2,null], \"key2\" : [1,2,3],  \"key3\" : [1,2,null] }")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("expressionTestCases")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}	
}
