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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.test.StepVerifier;

class EvaluateBasicExpressionsTest {

	@Test
	void evaluateBasicValue() {
		assertExpressionEvaluatesTo("null", "null");
	}

	@Test
	void evaluateBasicIdentifierExisting() {
		assertExpressionEvaluatesTo("nullVariable", "null");
	}

	@Test
	void evaluateBasicIdentifierNonExisting() {
		assertExpressionEvaluatesTo("unknownVariable", Val.UNDEFINED);
	}

	@Test
	void evaluateBasicRelative() {
		var expression = SaplFactoryImpl.eINSTANCE.createBasicRelative();
		StepVerifier.create(expression.evaluate()
				.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.TRUE))).expectNext(Val.TRUE)
				.verifyComplete();
	}

	@Test
	void evaluateBasicRelativeNotAllowed() {
		assertExpressionReturnsErrors("@");
	}

	@Test
	void evaluateBasicGroup() {
		assertExpressionEvaluatesTo("(null)", "null");
	}

	@Test
	void evaluateBasicFilter() {
		var expression = "[1,2,3] |- { @.* : mock.nil }";
		var expected   = "[null,null,null]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicSubTemplateObject() {
		var expression = "{ \"key\" : [1,2,3] } :: { \"newKey\" : @.key[1] }";
		var expected   = "{ \"newKey\" : 2 }";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicSubTemplateArray() {
		var expression = "[{ \"key\" : [1,2,3] },{ \"key\" : [4,5,6] }]  :: { \"newKey\" : @.key[1] }";
		var expected   = "[{ \"newKey\" : 2 }, { \"newKey\" : 5 }]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicSubTemplateEmptyArray() {
		var expression = "[]  :: { \"newKey\" : @.key[1] }";
		var expected   = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicFunctionNoArgs() {
		var expression = "mock.nil()";
		var expected   = "null";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicFunctionOneArg() {
		var expression = "mock.parameters(null)";
		var expected   = "[null]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicFunctionOneFluxOfArgs() {
		var expression = "mock.parameters(\"\".<test.numbers>)";
		var expected   = new String[] { "[0]", "[1]", "[2]", "[3]", "[4]", "[5]" };
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicFunctionMoreArgs() {
		var expression = "mock.parameters(null, \"Herbert\", 123)";
		var expected   = "[null, \"Herbert\", 123]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void evaluateBasicFunctionError() {
		assertExpressionReturnsErrors("error()");
	}

	@Test
	void evaluateBasicFunctionException() {
		assertExpressionReturnsErrors("exception(1,2,3)");
	}

}
