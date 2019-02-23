package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import reactor.test.StepVerifier;

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
			list.add(new JsonNodeWithParentArray(Optional.of(JSON.numberNode(BigDecimal.valueOf(i))),
					Optional.of(numberArray), i));
		}
		resultArray = new ArrayResultNode(list);
	}

	@Test
	public void applyToNonArrayNode() {
		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(JSON.objectNode()));

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(0));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyPositiveExistingToArrayNode() {
		int index = 5;

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(numberArray));
		ResultNode expectedResult = new JsonNodeWithParentArray(Optional.of(JSON.numberNode(BigDecimal.valueOf(index))),
				Optional.of(numberArray), index);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Index step applied to array node should return corresponding item",
						expectedResult, result));
	}

	@Test
	public void applyPositiveNonExistingToArrayNode() {
		int index = 12;

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(numberArray));

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyNegativeExistingToArrayNode() {
		int index = -2;

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(numberArray));
		ResultNode expectedResult = new JsonNodeWithParentArray(Optional.of(JSON.numberNode(BigDecimal.valueOf(8))),
				Optional.of(numberArray), 8);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"Negative index step applied to array node should return corresponding item", expectedResult,
						result));
	}

	@Test
	public void applyNegativeNonExistingToArrayNode() {
		int index = -12;

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(numberArray));

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyPositiveExistingToResultArray() {
		int index = 5;

		ResultNode previousResult = resultArray;
		ResultNode expectedResult = resultArray.getNodes().get(index);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Index step applied to result array should return corresponding item",
						expectedResult, result));
	}

	@Test
	public void applyPositiveNonExistingToResultArray() {
		int index = 12;

		ResultNode previousResult = resultArray;

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyNegativeExistingToResultArray() {
		int index = -2;

		ResultNode previousResult = resultArray;
		ResultNode expectedResult = resultArray.getNodes().get(8);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		previousResult.applyStep(step, ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"Negative index step applied to result array should return corresponding item", expectedResult,
						result));
	}

	@Test
	public void applyNegativeNonExistingToResultArray() {
		int index = -12;

		ResultNode previousResult = resultArray;

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(index));

		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class).verify();
	}
}
