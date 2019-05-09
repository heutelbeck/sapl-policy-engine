package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyStepsRecursiveIndexTest {

	private static BigDecimal INDEX = BigDecimal.valueOf(1L);

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx,
			variableCtx);

	@Test
	public void applyToNull() {
		ResultNode previousResult = new JsonNodeWithoutParent(
				Optional.of(JSON.nullNode()));

		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(INDEX);

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToSimpleArray() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(array));

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Optional.of(JSON.booleanNode(true)),
				Optional.of(array), INDEX.intValue()));
		ResultNode expectedResult = new ArrayResultNode(list);

		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(INDEX);
		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"Recursive index step applied to simple array should return result array with item",
						expectedResult, result));
	}

	@Test
	public void applyToDeeperStructure() {
		ObjectNode object = JSON.objectNode();

		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.arrayNode());
		array1.add(JSON.objectNode());

		object.set("key1", array1);

		ObjectNode object2 = JSON.objectNode();
		ArrayNode array2 = JSON.arrayNode();
		array2.add(JSON.nullNode());
		array2.add(JSON.booleanNode(true));

		object2.set("key1", array2);
		object.set("key2", object2);

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentArray(Optional.of(JSON.objectNode()),
				Optional.of(array1), INDEX.intValue()));
		expectedResultSet
				.add(new JsonNodeWithParentArray(Optional.of(JSON.booleanNode(true)),
						Optional.of(array2), INDEX.intValue()));

		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(INDEX);

		previousResult.applyStep(step, ctx, true, null).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset
					.create(((ArrayResultNode) result).getNodes());
			assertEquals("Recursive index step should return result array with items",
					expectedResultSet, resultSet);
		});
	}

	@Test
	public void applyToResultArray() {
		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.nullNode());
		array1.add(JSON.booleanNode(true));
		ArrayNode array2 = JSON.arrayNode();
		array2.add(JSON.nullNode());
		array2.add(JSON.booleanNode(false));

		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		listIn.add(new JsonNodeWithoutParent(Optional.of(JSON.nullNode())));
		listIn.add(new JsonNodeWithoutParent(Optional.of(array1)));
		listIn.add(new JsonNodeWithoutParent(Optional.of(array2)));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet
				.add(new JsonNodeWithParentArray(Optional.of(JSON.booleanNode(true)),
						Optional.of(array1), INDEX.intValue()));
		expectedResultSet
				.add(new JsonNodeWithParentArray(Optional.of(JSON.booleanNode(false)),
						Optional.of(array2), INDEX.intValue()));

		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(INDEX);

		previousResult.applyStep(step, ctx, true, null).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset
					.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Recursive index step applied to result array should return result array with items",
					expectedResultSet, resultSet);
		});
	}

}
