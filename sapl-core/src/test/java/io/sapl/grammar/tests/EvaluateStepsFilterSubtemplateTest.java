package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class EvaluateStepsFilterSubtemplateTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFilteringContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void basicExpressionWithStep() throws PolicyEvaluationException {
		BasicValue nullExpression = factory.createBasicValue();
		nullExpression.setValue(factory.createNullLiteral());

		Array array = factory.createArray();
		array.getItems().add(nullExpression);

		BasicValue expression = factory.createBasicValue();
		expression.setValue(array);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.ZERO);
		expression.getSteps().add(step);

		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("Index step applied to BasicValue should return correct result", JSON.nullNode(), result);
	}

	@Test
	public void basicExpressionWithFilter() throws PolicyEvaluationException {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add("EMPTY_STRING");
		expression.setFilter(filter);

		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("Filter applied to BasicValue should return correct result", JSON.textNode(""), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void subtemplateNoArray() throws PolicyEvaluationException {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		BasicValue subtemplate = factory.createBasicValue();
		subtemplate.setValue(factory.createNullLiteral());
		expression.setSubtemplate(subtemplate);

		expression.evaluate(ctx, true, null);
	}

	@Test
	public void subtemplateArray() throws PolicyEvaluationException {
		BasicValue expression = factory.createBasicValue();

		BasicValue item1 = factory.createBasicValue();
		item1.setValue(factory.createTrueLiteral());
		BasicValue item2 = factory.createBasicValue();
		item2.setValue(factory.createFalseLiteral());

		Array array = factory.createArray();
		array.getItems().add(item1);
		array.getItems().add(item2);

		expression.setValue(array);

		BasicValue subtemplate = factory.createBasicValue();
		subtemplate.setValue(factory.createNullLiteral());
		expression.setSubtemplate(subtemplate);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.nullNode());

		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("Subtemplate applied to array should return correct result", expectedResult, result);
	}
}
