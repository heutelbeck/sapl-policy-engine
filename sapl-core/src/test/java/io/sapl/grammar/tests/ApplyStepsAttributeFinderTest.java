package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyStepsAttributeFinderTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static Map<String, String> imports = new HashMap<>();
	private static EvaluationContext ctx = new EvaluationContext(new MockAttributeContext(), functionCtx, variableCtx, imports);

	@Test(expected = PolicyEvaluationException.class)
	public void applyToTarget() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());
		AttributeFinderStep step = factory.createAttributeFinderStep();
		previousResult.applyStep(step, ctx, false, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void exceptionDuringEvaluation() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());
		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("EXCEPTION");
		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyWithImport() throws PolicyEvaluationException {
		ctx.getImports().put("short", "ATTRIBUTE");
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		ResultNode expectedResult = new JsonNodeWithoutParent(JSON.textNode("ATTRIBUTE"));

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("short");
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Attribute finder step should take import mapping into account", expectedResult, result);
	}

	@Test
	public void applyWithoutImport() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.booleanNode(true));

		ResultNode expectedResult = new JsonNodeWithoutParent(JSON.booleanNode(true));

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("one");
		step.getIdSteps().add("two");
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Attribute finder step should take import mapping into account", expectedResult, result);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		AbstractAnnotatedJsonNode previousNode = new JsonNodeWithoutParent(JSON.booleanNode(true));
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(previousNode);
		ArrayResultNode previousResult = new ArrayResultNode(list);

		ArrayNode expectedArray = JSON.arrayNode();
		expectedArray.add(JSON.booleanNode(true));
		ResultNode expectedResult = new JsonNodeWithoutParent(expectedArray);

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("one");
		step.getIdSteps().add("two");
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Attribute finder step applied to result array should take import mapping into account",
				expectedResult, result);
	}

}
