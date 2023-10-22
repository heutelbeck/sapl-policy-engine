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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.Val;

class ApplyStepsEscapedKeyTests {

	@Test
	void keyStepPropagatesErrors() {
		assertExpressionReturnsError("(10/0).\"k e y\"", "Division by zero");
	}

	@Test
	void keyStepToNonObjectToUndefined() {
		assertExpressionEvaluatesTo("true.\"k e y\"", Val.UNDEFINED);
	}

	@Test
	void keyStepToEmptyObject() {
		assertExpressionEvaluatesTo("{}.\"k e y\"", Val.UNDEFINED);
	}

	@Test
	void keyStepToObject() {
		assertExpressionEvaluatesTo("{\"k e y\" : true}.\"k e y\"", "true");
	}

	private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
		// @formatter:off
		return Stream.of(
	 			// Key step to array
	 			Arguments.of("[{\"k e y\" : true},{\"k e y\": 123}].\"k e y\"",
	 					     "[true,123]"),

	 			// Key step to array no match
	 			Arguments.of("[{\"k e y\" : true},{\"k e y\": 123}].\"x\"",
	 					     "[]"),

	 			// Filter non-object or array
	 			Arguments.of("\"Gudrun\" |- { @.\"k e y\" : mock.nil }",
	 					     "\"Gudrun\""),

	 			// Filter object
	 			Arguments.of("{\"k e y\" : true, \"other\" : false} |- { @.\"k e y\" : mock.nil}",
	 					     "{\"k e y\" : null, \"other\" : false}"),
	
	 			// Filter object descend
	 			Arguments.of("{\"k e y\" : { \"k e y2\" : true}, \"other\" : false} |- { @.\"k e y\".\"k e y2\" : mock.nil}",
	 					     "{\"k e y\" : {\"k e y2\" : null }, \"other\" : false}"),
	 			
	 			// Filter array
	 			Arguments.of("[ {\"k e y\" : true, \"other\" : false} , false ] |- { @.\"k e y\" : mock.nil}",
	 					     "[ {\"k e y\" : null, \"other\" : false} , false ]"),
	 			
	 			// Filter empty array
	 			Arguments.of("[] |- { @.\"k e y\" : mock.nil}",
	 					     "[]")
				);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
