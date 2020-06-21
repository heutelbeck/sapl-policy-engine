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

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.ExpressionStep;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyStepsExpressionTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void expressionEvaluatesToBoolean() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.objectNode()));

		ExpressionStep step = factory.createExpressionStep();
		step.setExpression(basicValueFrom(factory.createTrueLiteral()));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToResultArrayWithTextualResult() {
		ResultNode previousResult = new ArrayResultNode(new ArrayList<>());

		ExpressionStep step = factory.createExpressionStep();
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("key");
		step.setExpression(basicValueFrom(stringLiteral));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToArrayNodeWithTextualResult() {
		JsonNodeWithoutParent previousResult = new JsonNodeWithoutParent(Val.of(JSON.arrayNode()));
		ExpressionStep step = factory.createExpressionStep();
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("key");
		step.setExpression(basicValueFrom(stringLiteral));

		ResultNode expectedResult = new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(),
				stringLiteral.getString());

		previousResult.applyStep(step, ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Accessing key on text should yield undefined.", expectedResult, result));
	}

	@Test
	public void applyToObjectWithTextualResult() {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.booleanNode(true));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));
		ResultNode expectedResult = new JsonNodeWithParentObject(Val.of(JSON.booleanNode(true)), Val.of(object), "key");

		ExpressionStep step = factory.createExpressionStep();
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("key");
		step.setExpression(basicValueFrom(stringLiteral));

		previousResult.applyStep(step, ctx, true, Val.undefined()).take(1).subscribe(result -> assertEquals(
				"Expression step with expression evaluating to key should return the value of the corresponding attribute",
				expectedResult, result));
	}

	@Test
	public void applyToObjectWithNumericResult() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.objectNode()));

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(0));
		step.setExpression(basicValueFrom(numberLiteral));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToArrayNodeWithNumericResultWhichExists() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.booleanNode(true));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));
		ResultNode expectedResult = new JsonNodeWithParentArray(Val.of(JSON.booleanNode(true)), Val.of(array), 0);

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(0));
		step.setExpression(basicValueFrom(numberLiteral));

		previousResult.applyStep(step, ctx, true, Val.undefined()).take(1).subscribe(result -> assertEquals(
				"Expression step with expression evaluating to number should return the corresponding array item",
				expectedResult, result));
	}

	@Test
	public void applyToArrayNodeWithNumericResultWhichNotExists() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.booleanNode(true));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(1));
		step.setExpression(basicValueFrom(numberLiteral));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToResultArraWithNumericResultWhichNotExists() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(JSON.nullNode()), Val.of(JSON.arrayNode()), 0));
		ResultNode previousResult = new ArrayResultNode(list);

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(1));
		step.setExpression(basicValueFrom(numberLiteral));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToResultArraWithNumericResultWhichNotExistsNegative() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(JSON.arrayNode()), 0));
		ResultNode previousResult = new ArrayResultNode(list);

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(-1));
		step.setExpression(basicValueFrom(numberLiteral));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToResultArrayWithNumericResultWhichExists() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		AbstractAnnotatedJsonNode annotatedNode = new JsonNodeWithParentArray(Val.of(JSON.nullNode()),
				Val.of(JSON.arrayNode()), 0);
		list.add(annotatedNode);
		ResultNode previousResult = new ArrayResultNode(list);

		ResultNode expectedResult = annotatedNode;

		ExpressionStep step = factory.createExpressionStep();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(BigDecimal.valueOf(0));
		step.setExpression(basicValueFrom(numberLiteral));

		previousResult.applyStep(step, ctx, true, Val.undefined()).take(1).subscribe(result -> assertEquals(
				"Expression step with expression evaluating to number applied to result array should return the corresponding array item",
				expectedResult, result));
	}

}
