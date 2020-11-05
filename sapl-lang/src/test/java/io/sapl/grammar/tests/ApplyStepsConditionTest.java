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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.More;
import io.sapl.grammar.sapl.NullLiteral;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
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

public class ApplyStepsConditionTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyToNullNode() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));

		ConditionStep step = factory.createConditionStep();
		step.setExpression(basicValueFrom(factory.createTrueLiteral()));

		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToObjectConditionNotBoolean() {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined())).consumeNextWith(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Condition step with condition always evaluation to null should return empty array",
					expectedResultSet, resultSet);
		}).thenCancel().verify();
	}

	@Test
	public void applyToArrayConditionNotBoolean() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined())).consumeNextWith(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Condition step with condition always evaluation to null should return empty array",
					expectedResultSet, resultSet);
		}).thenCancel().verify();
	}

	@Test
	public void applyToResultArrayConditionNotBoolean() {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		AbstractAnnotatedJsonNode node1 = new JsonNodeWithParentArray(Val.of(JSON.numberNode(20)),
				Val.of(JSON.arrayNode()), 0);
		AbstractAnnotatedJsonNode node2 = new JsonNodeWithParentArray(Val.of(JSON.numberNode(5)),
				Val.of(JSON.arrayNode()), 0);
		listIn.add(node1);
		listIn.add(node2);
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Condition step with condition always evaluation to null should return empty array",
					expectedResultSet, resultSet);
		});

	}

	@Test
	public void applyToResultArray() {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		AbstractAnnotatedJsonNode node1 = new JsonNodeWithParentArray(Val.of(JSON.numberNode(20)),
				Val.of(JSON.arrayNode()), 0);
		AbstractAnnotatedJsonNode node2 = new JsonNodeWithParentArray(Val.of(JSON.numberNode(5)),
				Val.of(JSON.arrayNode()), 0);
		listIn.add(node1);
		listIn.add(node2);
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(node1);

		ConditionStep step = factory.createConditionStep();
		More expression = factory.createMore();
		expression.setLeft(factory.createBasicRelative());
		NumberLiteral number = factory.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueFrom(number));
		step.setExpression(expression);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Condition step applied to result array should return the nodes for which the condition is true",
					expectedResultSet, resultSet);
		});

	}

	@Test
	public void applyToArrayNode() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.numberNode(20));
		array.add(JSON.numberNode(5));

		Val oArray = Val.of(array);
		ResultNode previousResult = new JsonNodeWithoutParent(oArray); // o([20, 5])

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		AbstractAnnotatedJsonNode node = new JsonNodeWithParentArray(Val.of(JSON.numberNode(20)), oArray, 0); // o(20)
																												// with
																												// patent
																												// o([20,
																												// 5])
		expectedResultSet.add(node); // expected = [ o(20) ]

		ConditionStep step = factory.createConditionStep();
		More expression = factory.createMore();
		expression.setLeft(factory.createBasicRelative());
		NumberLiteral number = factory.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueFrom(number));
		step.setExpression(expression); // conditional step: [@>10]

		// [20, 5][@>10] should be [20]

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Condition step applied to array node should return the nodes for which the condition is true",
					expectedResultSet, resultSet);
		});

	}

	@Test
	public void applyToObjectNode() {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.numberNode(20));
		object.set("key2", JSON.numberNode(5));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		AbstractAnnotatedJsonNode node = new JsonNodeWithParentObject(Val.of(JSON.numberNode(20)), Val.of(object),
				"key1");
		expectedResultSet.add(node);

		ConditionStep step = factory.createConditionStep();
		More expression = factory.createMore();
		expression.setLeft(factory.createBasicRelative());
		NumberLiteral number = factory.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueFrom(number));
		step.setExpression(expression);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Condition step applied to object node should return the attribute values for which the condition is true",
					expectedResultSet, resultSet);
		});

	}

}
