package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

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
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.WildcardStep;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyStepsWildcardTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void applyToNullNode() {
		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(JSON.nullNode()));

		WildcardStep step = factory.createWildcardStep();

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToArray() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		array.add(JSON.booleanNode(false));

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(array));

		ResultNode expectedResult = new JsonNodeWithoutParent(Optional.of(array));

		WildcardStep step = factory.createWildcardStep();
		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Wildcard step applied to an array node should return the array",
						expectedResult, result));
	}

	@Test
	public void applyToResultArray() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithoutParent(Optional.of(JSON.arrayNode())));
		list.add(new JsonNodeWithoutParent(Optional.of(JSON.nullNode())));

		ResultNode previousResult = new ArrayResultNode(list);

		ResultNode expectedResult = previousResult;

		WildcardStep step = factory.createWildcardStep();
		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"Wildcard step applied to a result array node should return the result array", expectedResult,
						result));
	}

	@Test
	public void applyToObject() {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.booleanNode(true));
		object.set("key3", JSON.booleanNode(false));

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(Optional.of(JSON.nullNode()), Optional.of(object), "key1"));
		expectedResultSet.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(true)), Optional.of(object), "key2"));
		expectedResultSet.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(false)), Optional.of(object), "key3"));

		WildcardStep step = factory.createWildcardStep();

		previousResult.applyStep(step, ctx, true, null).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Wildcard step applied to an object should return all attribute values", expectedResultSet,
					resultSet);
		});
	}
}
