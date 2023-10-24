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
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.test.StepVerifier;

class EvaluateBasicExpressionsTests {

	@Test
	void evaluateBasicIdentifierNonExisting() {
		assertExpressionEvaluatesTo("unknownVariable", Val.UNDEFINED);
	}

	@Test
	void evaluateBasicRelative() {
		var expression = SaplFactoryImpl.eINSTANCE.createBasicRelative();
		StepVerifier
				.create(expression.evaluate().contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.TRUE)))
				.expectNext(Val.TRUE).verifyComplete();
	}

	private static Stream<Arguments> errorExpressions() {
		// @formatter:off
		return Stream.of(
	 			// evaluateBasicRelativeNotAllowed
	 			Arguments.of("@", "Relative expression error. No relative node."),

	 			// evaluateBasicFunctionError
	 			Arguments.of("error()", "Unknown function error"),

	 			// evaluateBasicFunctionException
	 			Arguments.of("exception(1,2,3)", "Unknown function exception")
	 		);
		// @formater:on
	}
	
	@ParameterizedTest
	@MethodSource("errorExpressions")
	void expressionReturnsError(String expression, String expected) {
		assertExpressionReturnsError(expression, expected);
	}

	private static Stream<Arguments> expressionTestCases() {
		// @formatter:off
		return Stream.of(
	 			// evaluateBasicValue
	 			Arguments.of("null", "null"),

	 			// evaluateBasicIdentifierExisting
	 			Arguments.of("nullVariable", "null"),

	 			// evaluateBasicGroup
	 			Arguments.of("(null)", "null"),

	 			// evaluateBasicFilter
	 			Arguments.of("[1,2,3] |- { @.* : mock.nil }",
	 					     "[null,null,null]"),

	 			// evaluateBasicSubTemplateObject
	 			Arguments.of("{ \"key\" : [1,2,3] } :: { \"newKey\" : @.key[1] }",
	 					     "{ \"newKey\" : 2 }"),

	 			// evaluateBasicSubTemplateArray
	 			Arguments.of("[{ \"key\" : [1,2,3] },{ \"key\" : [4,5,6] }]  :: { \"newKey\" : @.key[1] }",
	 			             "[{ \"newKey\" : 2 }, { \"newKey\" : 5 }]"),

	 			// evaluateBasicSubTemplateEmptyArray
	 			Arguments.of("[]  :: { \"newKey\" : @.key[1] }","[]"),

	 			// evaluateBasicFunctionNoArgs
	 			Arguments.of("mock.nil()", "null"),

	 			// evaluateBasicFunctionOneArg
	 			Arguments.of("mock.parameters(null)", "[null]"),

	 			// evaluateBasicFunctionMoreArgs
	 			Arguments.of("mock.parameters(null, \"Herbert\", 123)",
	 			             "[null, \"Herbert\", 123]")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("expressionTestCases")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}	

	@Test
	void evaluateBasicFunctionOneFluxOfArgs() {
		var expression = "mock.parameters(\"\".<test.numbers>)";
		var expected   = new String[] { "[0]", "[1]", "[2]", "[3]", "[4]", "[5]" };
		assertExpressionEvaluatesTo(expression, expected);
	}
}
