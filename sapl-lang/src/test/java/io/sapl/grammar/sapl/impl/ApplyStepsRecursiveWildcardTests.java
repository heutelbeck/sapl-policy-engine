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

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ArrayUtil;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import reactor.test.StepVerifier;

class ApplyStepsRecursiveWildcardTests {

	@ParameterizedTest
	// @formatter:off
	@ValueSource(strings = {
		// stepPropagatesErrors
		"(10/0)..*",
		// stepOnUndefinedEmpty
		"undefined..*"
	}) 
	// @formatter:on
	void expressionEvaluatesToErrors(String expression) {
		assertExpressionReturnsErrors(expression);
	}

	private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
		// @formatter:off
		return Stream.of(
				// applyToNull
	 			Arguments.of("null..*", "[]"),

	 			// applyToArray
	 			Arguments.of("[1,2,[3,4,5], { \"key\" : [6,7,8], \"key2\": { \"key3\" : 9 } }]..*",
	 			             "[1,2,[3,4,5],3,4,5,{\"key\":[6,7,8],\"key2\":{\"key3\":9}},[6,7,8],6,7,8,{\"key3\":9},9]"),

	 			// filterArray
	 			Arguments.of("[1,2,[3,4,5], { \"key\" : [6,7,8], \"key2\": { \"key3\" : 9 } }] |- { @..* : mock.nil }",
	 			             "[null,null,null,null]")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applyToObject() throws IOException {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..*";
		var expected   = Val.ofJson(
				"[1,2,3,4,5,\"value1\",[{\"key\":\"value2\"},{\"key\":\"value3\"}],{\"key\":\"value2\"},\"value2\",{\"key\":\"value3\"},\"value3\",[1,2,3,4,5]]");
		assertExpressionEvaluatesTo("null..*", "[]");
		StepVerifier
				.create(ParserUtil.expression(expression).evaluate()
						.contextWrite(MockUtil::setUpAuthorizationContext))
				.expectNextMatches(result -> ArrayUtil.arraysMatchWithSetSemantics(result, expected)).verifyComplete();
	}

}
