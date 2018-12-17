package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.FilterExtended;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyFilteringExtendedTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFilteringContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	private static final String REMOVE = "remove";

	@Test(expected = PolicyEvaluationException.class)
	public void removeNoStepsNoEach() throws PolicyEvaluationException {
		JsonNode root = JSON.objectNode();

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		filter.getStatements().add(statement);

		filter.apply(root, ctx, false, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeEachNoArray() throws PolicyEvaluationException {
		JsonNode root = JSON.objectNode();

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		filter.apply(root, ctx, false, null);
	}

	@Test
	public void removeNoStepsEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		JsonNode expectedResult = JSON.arrayNode();

		JsonNode result = filter.apply(root, ctx, false, null);

		assertEquals("Function remove, no steps and each should return empty array", expectedResult, result);
	}

	@Test
	public void emptyStringNoStepsNoEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");
		filter.getStatements().add(statement);

		JsonNode expectedResult = JSON.textNode("");

		JsonNode result = filter.apply(root, ctx, false, null);

		assertEquals("Mock function EMPTY_STRING, no steps, no each should return empty string", expectedResult,
				result);
	}

	@Test
	public void emptyStringNoStepsEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.textNode(""));

		JsonNode result = filter.apply(root, ctx, false, null);

		assertEquals("Mock function EMPTY_STRING, no steps, each should array with empty strings", expectedResult,
				result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void emptyStringEachNoArray() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.objectNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		BasicRelative expression = factory.createBasicRelative();
		expression.getSteps().add(step);
		statement.setTarget(expression);

		statement.setEach(true);

		filter.getStatements().add(statement);

		filter.apply(root, ctx, false, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeResultArrayNoEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add(REMOVE);
		filter.getStatements().add(statement);

		filter.apply(root, ctx, false, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void emptyStringResultArrayNoEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add("EMPTY_STRING");
		filter.getStatements().add(statement);

		filter.apply(root, ctx, false, null);
	}

	@Test
	public void emptyStringResultArrayEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add("EMPTY_STRING");
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.booleanNode(true));

		JsonNode result = filter.apply(root, ctx, false, null);

		assertEquals(
				"Mock function EMPTY_STRING applied to result array and each should replace selected elements by empty string",
				expectedResult, result);
	}

	@Test
	public void removeResultArrayEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.booleanNode(true));

		JsonNode result = filter.apply(root, ctx, false, null);

		assertEquals("Remove applied to result array and each should remove each element", expectedResult, result);
	}

}
