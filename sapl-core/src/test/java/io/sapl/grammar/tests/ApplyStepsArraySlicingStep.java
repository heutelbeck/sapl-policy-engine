package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.ArraySlicingStep;
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

public class ApplyStepsArraySlicingStep {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	private static ArrayNode numberArray;
	private static ArrayResultNode resultArray;

	@Before
	public void fillNumberArray() {
		numberArray = JSON.arrayNode();
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			numberArray.add(JSON.numberNode(BigDecimal.valueOf(i)));
			list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(i)), numberArray, i));
		}
		resultArray = new ArrayResultNode(list);
	}

	private ResultNode applySlicingToArrayNode(Integer from, Integer to, Integer step)
			throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);

		ArraySlicingStep slicingStep = factory.createArraySlicingStep();

		if (from != null)
			slicingStep.setIndex(BigDecimal.valueOf(from));

		if (to != null)
			slicingStep.setTo(BigDecimal.valueOf(to));

		if (step != null) {
			slicingStep.setStep(BigDecimal.valueOf(step));
		}

		return previousResult.applyStep(slicingStep, ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applySlicingToNoArray() throws PolicyEvaluationException {
		ArraySlicingStep slicingStep = factory.createArraySlicingStep();

		JsonNodeWithoutParent node = new JsonNodeWithoutParent(JSON.objectNode());
		node.applyStep(slicingStep, ctx, false, null);
	}

	@Test
	public void applySlicingToArrayNode() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(1)), numberArray, 1));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(2)), numberArray, 2));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(3)), numberArray, 3));
		ResultNode expectedResult = new ArrayResultNode(list);


		ResultNode result = applySlicingToArrayNode(1, 4, null);

		assertEquals("Slicing applied to array node should return corresponding items", expectedResult,
				result);
	}

	@Test
	public void applySlicingToArrayNodeNegativeTo() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(7, -1, null);

		assertEquals("Slicing applied to array node with negative to should return corresponding items", expectedResult,
				result);
	}

	@Test
	public void applySlicingToArrayNodeNegativeFrom() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(-3, 9, null);

		assertEquals("Slicing applied to array node with negative from should return corresponding items",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayWithFromGreaterThanTo() throws PolicyEvaluationException {
		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		ResultNode result = applySlicingToArrayNode(4, 1, null);

		assertEquals("Slicing applied to array node with from greater than to should return empty result array",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayNodeWithoutTo() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(9)), numberArray, 9));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(7, null, null);

		assertEquals("Slicing applied to array node without to to should return corresponding items", expectedResult,
				result);
	}

	@Test
	public void applySlicingToArrayNodeWithoutFrom() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(0)), numberArray, 0));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(1)), numberArray, 1));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(2)), numberArray, 2));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(null, 3, null);

		assertEquals("Slicing applied to array node without from should return corresponding items", expectedResult,
				result);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeFrom() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(9)), numberArray, 9));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(-3, null, null);

		assertEquals("Slicing applied to array node with negative from from should return corresponding items",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStep() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(9)), numberArray, 9));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(6)), numberArray, 6));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(5)), numberArray, 5));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(4)), numberArray, 4));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(3)), numberArray, 3));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(2)), numberArray, 2));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(1)), numberArray, 1));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(0)), numberArray, 0));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(null, null, -1);

		assertEquals("Slicing applied to array node with negative step from should return corresponding items",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(-2, 6, -1);

		assertEquals("Slicing applied to array node with negative step from should return corresponding items",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(7)), numberArray, 7));
		list.add(new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(6)), numberArray, 6));
		ResultNode expectedResult = new ArrayResultNode(list);

		ResultNode result = applySlicingToArrayNode(-2, -5, -1);

		assertEquals("Slicing applied to array node with negative step from should return corresponding items",
				expectedResult, result);
	}

	@Test
	public void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() throws PolicyEvaluationException {
		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		ResultNode result = applySlicingToArrayNode(1, 5, -1);

		assertEquals(
				"Slicing applied to array node with negative step and to greater than from should return empty result array",
				expectedResult, result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applySlicingStepZero() throws PolicyEvaluationException {
		applySlicingToArrayNode(1, 5, 0);
	}

	@Test
	public void applySlicingToResultArray() throws PolicyEvaluationException {
		ResultNode previousResult = resultArray;

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(resultArray.getNodes().get(3));
		list.add(resultArray.getNodes().get(4));
		list.add(resultArray.getNodes().get(5));
		ResultNode expectedResult = new ArrayResultNode(list);

		ArraySlicingStep step = factory.createArraySlicingStep();
		step.setIndex(BigDecimal.valueOf(3));
		step.setTo(BigDecimal.valueOf(6));

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals(
				"Slicing applied to result array should return the correct items",
				expectedResult, result);
	}
}
