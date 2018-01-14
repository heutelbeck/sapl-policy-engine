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
import io.sapl.grammar.sapl.IndexStep;
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

public class ApplyStepsIndexTest {
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

	@Test(expected = PolicyEvaluationException.class)
	public void applyToNonArrayNode() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.objectNode());

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(0));

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyPositiveExistingToArrayNode() throws PolicyEvaluationException {
		int index = 5;

		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);
		ResultNode expectedResult = new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(index)), numberArray,
				index);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Index step applied to array node should return corresponding item", expectedResult, result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyPositiveNonExistingToArrayNode() throws PolicyEvaluationException {
		int index = 12;

		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyNegativeExistingToArrayNode() throws PolicyEvaluationException {
		int index = -2;

		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);
		ResultNode expectedResult = new JsonNodeWithParentArray(JSON.numberNode(BigDecimal.valueOf(8)), numberArray, 8);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Negative index step applied to array node should return corresponding item", expectedResult,
				result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyNegativeNonExistingToArrayNode() throws PolicyEvaluationException {
		int index = -12;

		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyPositiveExistingToResultArray() throws PolicyEvaluationException {
		int index = 5;

		ResultNode previousResult = resultArray;
		ResultNode expectedResult = resultArray.getNodes().get(index);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Index step applied to result array should return corresponding item", expectedResult, result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyPositiveNonExistingToResultArray() throws PolicyEvaluationException {
		int index = 12;

		ResultNode previousResult = resultArray;

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyNegativeExistingToResultArray() throws PolicyEvaluationException {
		int index = -2;

		ResultNode previousResult = resultArray;
		ResultNode expectedResult = resultArray.getNodes().get(8);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Negative index step applied to result array should return corresponding item", expectedResult,
				result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyNegativeNonExistingToResultArray() throws PolicyEvaluationException {
		int index = -12;

		ResultNode previousResult = resultArray;

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null);
	}
}
