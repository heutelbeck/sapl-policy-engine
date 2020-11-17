/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class EvaluateBasicExpressionsTest {

	private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	private EvaluationContext ctx;

	@Before
	public void before() {
		ctx = MockUtil.mockEvaluationContext();
	}

	@Test
	public void evaluateBasicValue() throws IOException {
		var expression = ParserUtil.expression("null");
		var expected = Val.ofNull();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicIdentifierExisting() throws IOException {
		var expression = ParserUtil.expression("nullVariable");
		var expected = Val.ofNull();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicIdentifierNonExisting() throws IOException {
		var expression = ParserUtil.expression("unknownVariable");
		var expected = Val.UNDEFINED;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicRelative() {
		var expression = FACTORY.createBasicRelative();
		StepVerifier.create(expression.evaluate(ctx, Val.TRUE)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateBasicRelativeNotAllowed() {
		BasicRelative expression = FACTORY.createBasicRelative();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateBasicGroup() throws IOException {
		var expression = ParserUtil.expression("(null)");
		var expected = Val.ofNull();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFilter() throws IOException {
		var expression = ParserUtil.expression("[1,2,3] |- { @.* : nil }");
		var expected = Val.ofJson("[null,null,null]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicSubtemplateObject() throws IOException {
		var expression = ParserUtil.expression("{ \"key\" : [1,2,3] } :: { \"newkey\" : @.key[1] }");
		var expected = Val.ofJson("{ \"newkey\" : 2 }");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicSubtemplateArray() throws IOException {
		var expression = ParserUtil
				.expression("[{ \"key\" : [1,2,3] },{ \"key\" : [4,5,6] }]  :: { \"newkey\" : @.key[1] }");
		var expected = Val.ofJson("[{ \"newkey\" : 2 }, { \"newkey\" : 5 }]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicSubtemplateEmptyArray() throws IOException {
		var expression = ParserUtil.expression("[]  :: { \"newkey\" : @.key[1] }");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionNoArgs() throws IOException {
		var expression = ParserUtil.expression("nil()");
		var expected = Val.ofNull();
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionOneArg() throws IOException {
		var expression = ParserUtil.expression("parameters(null)");
		var expected = Val.ofJson("[null]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionOneFluxOfArgs() throws IOException {
		var expression = ParserUtil.expression("parameters(\"\".<numbers>)");
		var expected = new Val[] { Val.ofJson("[0]"), Val.ofJson("[1]"), Val.ofJson("[2]"), Val.ofJson("[3]"),
				Val.ofJson("[4]"), Val.ofJson("[5]") };
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionMoreArgs() throws IOException {
		var expression = ParserUtil.expression("parameters(null, \"Herbert\", 123)");
		var expected = Val.ofJson("[null, \"Herbert\", 123]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionError() throws IOException {
		var expression = ParserUtil.expression("error()");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateBasicFunctionException() throws IOException {
		var expression = ParserUtil.expression("exception(1,2,3)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNextMatches(Val::isError)
				.verifyComplete();
	}

}
