package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.IndexUnionStep;
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

public class ApplyStepsIndexUnionTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void applyToNonArray() {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		IndexUnionStep step = factory.createIndexUnionStep();
		step.getIndices().add(BigDecimal.valueOf(0));
		step.getIndices().add(BigDecimal.valueOf(1));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void applyToResultArray() {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		AbstractAnnotatedJsonNode node1 = new JsonNodeWithParentObject(JSON.nullNode(), JSON.objectNode(), "key1");
		AbstractAnnotatedJsonNode node2 = new JsonNodeWithParentObject(JSON.booleanNode(true), JSON.objectNode(),
				"key1");
		listIn.add(node1);
		listIn.add(node2);
		listIn.add(new JsonNodeWithParentObject(JSON.booleanNode(false), JSON.objectNode(), "key2"));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(node1);
		expectedResultSet.add(node2);

		IndexUnionStep step = factory.createIndexUnionStep();
		step.getIndices().add(BigDecimal.valueOf(0));
		step.getIndices().add(BigDecimal.valueOf(1));
		step.getIndices().add(BigDecimal.valueOf(-2));
		step.getIndices().add(BigDecimal.valueOf(10));
		step.getIndices().add(BigDecimal.valueOf(-10));

		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> {
					Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
					assertEquals("Index union applied to result array should return items with corresponding attribute values",
							expectedResultSet, resultSet);
				});
	}

	@Test
	public void applyToArrayNodePositive() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		array.add(JSON.booleanNode(false));
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.nullNode(), array, 0));
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.booleanNode(true), array, 1));

		IndexUnionStep step = factory.createIndexUnionStep();
		step.getIndices().add(BigDecimal.valueOf(1));
		step.getIndices().add(BigDecimal.valueOf(0));
		step.getIndices().add(BigDecimal.valueOf(1));
		step.getIndices().add(BigDecimal.valueOf(10));

		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> {
					Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
					assertEquals("Index union applied to array node should return items with corresponding attribute values",
							expectedResultSet, resultSet);
				});
	}

	@Test
	public void applyToArrayNodeNegative() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		array.add(JSON.booleanNode(false));
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.nullNode(), array, 0));
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.booleanNode(true), array, 1));

		IndexUnionStep step = factory.createIndexUnionStep();
		step.getIndices().add(BigDecimal.valueOf(-2));
		step.getIndices().add(BigDecimal.valueOf(0));

		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> {
					Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
					assertEquals("Index union applied to array node should return items with corresponding attribute values",
							expectedResultSet, resultSet);
				});
	}
}
