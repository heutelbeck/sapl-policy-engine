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
package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class EvaluateBasicExpressionsTest {

	private static final String EXCEPTION = "EXCEPTION";

	private static final String PARAMETERS = "PARAMETERS";

	private static final String LONG = "long";

	private static final String SHORT = "short";

	private static final String KEY_ANOTHER = "another key";

	private static final String KEY = "key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx;

	@Before
	public void prepare() throws PolicyEvaluationException {
		Map<String, String> imports = new HashMap<>();
		imports.put(SHORT, LONG);
		ctx = new EvaluationContext(functionCtx, variableCtx, imports);
		variableCtx.put(KEY, JSON.booleanNode(true));
	}

	@Test
	public void evaluateBasicValue() {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("BasicValueExpression with NullLiteral should evaluate to NullNode",
						Val.ofNull(), result));
	}

	@Test
	public void evaluateBasicIdentifierExisting() {
		BasicIdentifier expression = factory.createBasicIdentifier();
		expression.setIdentifier(KEY);

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicIdentifierExpression should return the corresponding variable value", Val.ofTrue(),
						result));
	}

	@Test
	public void evaluateBasicIdentifierNonExisting() {
		BasicIdentifier expression = factory.createBasicIdentifier();
		expression.setIdentifier(KEY_ANOTHER);
		expression.evaluate(ctx, true, Val.undefined())
				.subscribe(result -> assertEquals("should return undefined value", Val.undefined(), result));
	}

	@Test
	public void evaluateBasicRelative() {
		BasicRelative expression = factory.createBasicRelative();

		expression.evaluate(ctx, true, Val.ofNull()).take(1)
				.subscribe(result -> assertEquals(
						"BasicRelativeExpression without selection steps should evaluate to relative node",
						Val.ofNull(), result));
	}

	@Test
	public void evaluateBasicRelativeNotAllowed() {
		BasicRelative expression = factory.createBasicRelative();
		StepVerifier.create(expression.evaluate(ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateBasicGroup() {
		BasicGroup expression = factory.createBasicGroup();
		BasicValue value = factory.createBasicValue();
		value.setValue(factory.createNullLiteral());
		expression.setExpression(value);

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicGroupExpression should evaluate to the result of evaluating its expression", Val.ofNull(),
						result));
	}

	@Test
	public void evaluateBasicFunctionNoArgs1() {
		BasicFunction expression = factory.createBasicFunction();
		expression.getFsteps().add(KEY);

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicFunctionExpression should evaluate to the result of evaluating the function", Val.of(KEY),
						result));
	}

	@Test
	public void evaluateBasicFunctionNoArgs2() {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(KEY);

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicFunctionExpression should evaluate to the result of evaluating the function", Val.of(KEY),
						result));
	}

	@Test
	public void evaluateBasicFunctionOneArg() {
	    // create expression: PARAMETERS(null)
		BasicFunction expression = factory.createBasicFunction();
		Arguments arguments = factory.createArguments();
		BasicValue value = factory.createBasicValue();
		value.setValue(factory.createNullLiteral());
		arguments.getArgs().add(value);
		expression.setArguments(arguments);
		expression.getFsteps().add(PARAMETERS);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicFunctionExpression should evaluate to the result of evaluating the function",
						Val.of(expectedResult), result));
	}

	@Test
	public void evaluateBasicFunctionTwoArgs() {
		BasicFunction expression = factory.createBasicFunction();

		Arguments arguments = factory.createArguments();

		BasicValue value1 = factory.createBasicValue();
		value1.setValue(factory.createNullLiteral());
		BasicValue value2 = factory.createBasicValue();
		value2.setValue(factory.createNullLiteral());

		arguments.getArgs().add(value1);
		arguments.getArgs().add(value2);
		expression.setArguments(arguments);
		expression.getFsteps().add(PARAMETERS);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.nullNode());

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicFunctionExpression should evaluate to the result of evaluating the function",
						Val.of(expectedResult), result));
	}

	@Test
	public void evaluateBasicFunctionImport() {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(SHORT);

		expression.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"BasicFunctionExpression should evaluate to the result of evaluating the function",
						Val.of(LONG), result));
	}

	@Test
	public void evaluateBasicFunctionException() {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(EXCEPTION);

		StepVerifier.create(expression.evaluate(ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

}
