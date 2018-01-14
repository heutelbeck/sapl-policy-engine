package io.sapl.grammar.tests;

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
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.More;
import io.sapl.grammar.sapl.NullLiteral;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
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

public class ApplyStepsConditionTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test(expected = PolicyEvaluationException.class)
	public void applyToNullNode() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		ConditionStep step = factory.createConditionStep();
		step.setExpression(basicValueOf(factory.createTrueLiteral()));

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyToObjectConditionNotBoolean() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());
		ResultNode previousResult = new JsonNodeWithoutParent(object);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Condition step with condition always evaluation to null should return empty array",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToArrayConditionNotBoolean() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Condition step with condition always evaluation to null should return empty array",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToResultArrayConditionNotBoolean() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		AbstractAnnotatedJsonNode node1 = new JsonNodeWithParentArray(JSON.numberNode(20), JSON.arrayNode(), 0);
		AbstractAnnotatedJsonNode node2 = new JsonNodeWithParentArray(JSON.numberNode(5), JSON.arrayNode(), 0);
		listIn.add(node1);
		listIn.add(node2);
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();

		ConditionStep step = factory.createConditionStep();
		NullLiteral nullLiteral = factory.createNullLiteral();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(nullLiteral);
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Condition step with condition always evaluation to null should return empty array",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		AbstractAnnotatedJsonNode node1 = new JsonNodeWithParentArray(JSON.numberNode(20), JSON.arrayNode(), 0);
		AbstractAnnotatedJsonNode node2 = new JsonNodeWithParentArray(JSON.numberNode(5), JSON.arrayNode(), 0);
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
		expression.setRight(basicValueOf(number));
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Condition step applied to result array should return the nodes for which the condition is true",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToArrayNode() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.numberNode(20));
		array.add(JSON.numberNode(5));
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		AbstractAnnotatedJsonNode node = new JsonNodeWithParentArray(JSON.numberNode(20), array, 0);
		expectedResultSet.add(node);

		ConditionStep step = factory.createConditionStep();
		More expression = factory.createMore();
		expression.setLeft(factory.createBasicRelative());
		NumberLiteral number = factory.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueOf(number));
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Condition step applied to array node should return the nodes for which the condition is true",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToObjectNode() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.numberNode(20));
		object.set("key2", JSON.numberNode(5));
		ResultNode previousResult = new JsonNodeWithoutParent(object);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		AbstractAnnotatedJsonNode node = new JsonNodeWithParentObject(JSON.numberNode(20), object, "key1");
		expectedResultSet.add(node);

		ConditionStep step = factory.createConditionStep();
		More expression = factory.createMore();
		expression.setLeft(factory.createBasicRelative());
		NumberLiteral number = factory.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueOf(number));
		step.setExpression(expression);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals(
				"Condition step applied to object node should return the attribute values for which the condition is true",
				expectedResultSet, resultSet);
	}

	private static BasicValue basicValueOf(Value value) {
		BasicValue basicValue = factory.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}
}
