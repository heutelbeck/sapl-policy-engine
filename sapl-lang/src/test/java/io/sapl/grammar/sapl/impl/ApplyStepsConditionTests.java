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
import static io.sapl.testutil.TestUtil.assertExpressionReturnsError;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplyStepsConditionTests {
    @ParameterizedTest
    @MethodSource("errorCases")
    void expressionEvaluatesToExpectedError(String expression, String expectedError) {
        assertExpressionReturnsError(expression, expectedError);
    }

    private static Stream<Arguments> errorCases() {
        // @formatter:off
		return Stream.of(
	 			// Propagates errors
	 			Arguments.of("(10/0)[?(@>0)]", "Division by zero"),

	 			// On undefined error
	 			Arguments.of("undefined[?(@>0)]",
	 					"Type mismatch. Expected an Object or Array, but got: 'undefined'."),

                // Apply to array with error
                Arguments.of("[20, 10/0, 5][?(@>10)]", "Division by zero"),

                // Apply to array with error 2
                Arguments.of("[20, 10/0, 2/0][?(@>10)]", "Division by zero"),

	 			// Apply to array with error condition
	 			Arguments.of("[20,  5][?(@>(10/0)]", "Division by zero"),

	 			// Non-object non-array
	 			Arguments.of("\"Toastbrot\"[?(@>0)]",
	 					     "Type mismatch. Expected an Object or Array, but got: '\"Toastbrot\"'."),

	 			// Filter error propagation
	 			Arguments.of("[(10/0)] |- { @[?(@>2)] : mock.emptyString }", "Division by zero"),

	 			// Filter error in array
	 			Arguments.of("[1, (10/0), 3] |- { @[?(@>2)] : mock.emptyString }", "Division by zero"),

	 			// Filter object error in condition
	 			Arguments.of("{ \"name\" : \"Otto\" } |- { @[?(10/0))] : filter.remove }", 
	 					     "Division by zero"),

	 			// Filter object non-Boolean in condition
	 			Arguments.of("{ \"name\" : \"Otto\" } |- { @[?(123)] : filter.remove }", 
	 					     "Type mismatch. Expected the condition expression to return a Boolean, but was '123'."),

	 			// Filter error in condition
	 			Arguments.of("[10,1] |- { @[?(@>(10/0))] : mock.emptyString }", "Division by zero"),

	 			// Filter non-Boolean in condition
	 			Arguments.of("[10,1] |- { @[?(123)] : mock.emptyString }",
	 					     "Type mismatch. Expected the condition expression to return a Boolean, but was '123'.")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}

	private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
		// @formatter:off
		return Stream.of(
	 			// Apply to object condition not boolean
	 			Arguments.of("{ \"key\" : null }[?(null)]", "[]"),

	 			// Apply to array
	 			Arguments.of("[20, 5][?(@>10)]", "[20]"),

	 			// Apply to empty object
	 			Arguments.of("{}[?(@>10)]", "[]"),

	 			// Apply to empty array
	 			Arguments.of("[][?(@>10)]", "[]"),

	 			// Apply to object node
	 			Arguments.of("{ \"key1\" : 20, \"key2\" : 5 }[?(@>10)]", "[20]"),

	 			// Remove condition step from object
	 			Arguments.of("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @[?(@>2)] : filter.remove }",
	 					     "{ \"a\" : 1, \"b\" : 2 }"),

	 			// Replace condition step from array
	 			Arguments.of("[1,2,3,4,5] |- { @[?(@>2)] : mock.emptyString }",
	 					     "[1,2,\"\", \"\", \"\"]"),

	 			// Filter non-array or object
	 			Arguments.of("null |- { @[?(@>2)] : mock.emptyString }", "null"),

	 			// Filter empty array
	 			Arguments.of("[] |- { @[?(@>2)] : mock.emptyString }", "[]"),

	 			// Filter in array descend
	 			Arguments.of("[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\", \"job\" : \"warmonger\" } ] "
	 					   + "|- { @[?(@.name == \"Willi\")].job : filter.remove }",
	 					     "[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\" } ]"),

	 			// Filter in object descend
	 			Arguments.of("{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
	 					   + "|- { @[?(\"Paul\" in @.children)]..children[1] : filter.remove }",
	 					     "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Paul\" ] } } "),

	 			// filter empty object
	 			Arguments.of("{} |- { @[?(@>2)] : mock.emptyString }", "{}")
			);
		// @formater:on
	}

}
