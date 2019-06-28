package io.sapl.interpreter.selection;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.tests.MockFunctionContext;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class ResultNodeApplyStepTest {

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static SaplFactory factory = SaplFactory.eINSTANCE;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyStepArrayResult() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithoutParent(Optional.of(JSON.nullNode())));
		ResultNode resultNode = new ArrayResultNode(list);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.ZERO);

		ResultNode expectedResult = new JsonNodeWithoutParent(
				Optional.of(JSON.nullNode()));

		resultNode.applyStep(step, ctx, false, null).take(1)
				.subscribe(result -> assertEquals(
						"applyStep on ArrayResultNode should return correct ResultNode",
						expectedResult, result));
	}

	@Test
	public void applyStepAnnotatedJsonNode() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(array));

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.ZERO);

		ResultNode expectedResult = new JsonNodeWithParentArray(
				Optional.of(JSON.nullNode()), Optional.of(array), 0);

		resultNode.applyStep(step, ctx, false, null).take(1)
				.subscribe(result -> assertEquals(
						"applyStep on AbstractAnnotatedJsonNode should return correct ResultNode",
						expectedResult, result));
	}

}
