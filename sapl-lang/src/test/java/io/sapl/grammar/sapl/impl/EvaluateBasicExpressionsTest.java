/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

class EvaluateBasicExpressionsTest {

	private static final EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	void evaluateBasicValue() {
		expressionEvaluatesTo(CTX, "null", "null");
	}

	@Test
	void evaluateBasicIdentifierExisting() {
		expressionEvaluatesTo(CTX, "nullVariable", "null");
	}

	@Test
	void evaluateBasicIdentifierNonExisting() {
		expressionEvaluatesTo(CTX, "unknownVariable", Val.UNDEFINED);
	}

	@Test
	void evaluateBasicRelative() {
		var expression = SaplFactoryImpl.eINSTANCE.createBasicRelative();
		StepVerifier.create(expression.evaluate(CTX, Val.TRUE)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	void evaluateBasicRelativeNotAllowed() {
		expressionErrors(CTX, "@");
	}

	@Test
	void evaluateBasicGroup() {
		expressionEvaluatesTo(CTX, "(null)", "null");
	}

	@Test
	void evaluateBasicFilter() {
		var expression = "[1,2,3] |- { @.* : mock.nil }";
		var expected = "[null,null,null]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicSubtemplateObject() {
		var expression = "{ \"key\" : [1,2,3] } :: { \"newkey\" : @.key[1] }";
		var expected = "{ \"newkey\" : 2 }";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicSubtemplateArray() {
		var expression = "[{ \"key\" : [1,2,3] },{ \"key\" : [4,5,6] }]  :: { \"newkey\" : @.key[1] }";
		var expected = "[{ \"newkey\" : 2 }, { \"newkey\" : 5 }]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicSubtemplateEmptyArray() {
		var expression = "[]  :: { \"newkey\" : @.key[1] }";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicFunctionNoArgs() {
		var expression = "mock.nil()";
		var expected = "null";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicFunctionOneArg() {
		var expression = "mock.parameters(null)";
		var expected = "[null]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicFunctionOneFluxOfArgs() {
		var expression = "mock.parameters(\"\".<test.numbers>)";
		var expected = new String[] { "[0]", "[1]", "[2]", "[3]", "[4]", "[5]" };
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicFunctionMoreArgs() {
		var expression = "mock.parameters(null, \"Herbert\", 123)";
		var expected = "[null, \"Herbert\", 123]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicFunctionError() {
		expressionErrors(CTX, "error()");
	}

	@Test
	void evaluateBasicFunctionException() {
		expressionErrors(CTX, "exception(1,2,3)");
	}

}
