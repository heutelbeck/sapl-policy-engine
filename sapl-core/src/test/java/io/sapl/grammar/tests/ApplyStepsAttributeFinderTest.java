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
import reactor.test.StepVerifier;

public class ApplyStepsAttributeFinderTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static Map<String, String> imports = new HashMap<>();
	private static EvaluationContext ctx = new EvaluationContext(new MockAttributeContext(), functionCtx, variableCtx, imports);

	@Test
	public void applyToTarget() {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());
		AttributeFinderStep step = factory.createAttributeFinderStep();
		StepVerifier.create(previousResult.applyStep(step, ctx, false, null))
				.expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void exceptionDuringEvaluation() {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());
		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("EXCEPTION");
		StepVerifier.create(previousResult.applyStep(step, ctx, true, null))
				.expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void applyWithImport() {
		ctx.getImports().put("short", "ATTRIBUTE");
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		ResultNode expectedResult = new JsonNodeWithoutParent(JSON.textNode("ATTRIBUTE"));

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("short");
		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> assertEquals("Attribute finder step should take import mapping into account",
						expectedResult, result)
				);
	}

	@Test
	public void applyWithoutImport() {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.booleanNode(true));

		ResultNode expectedResult = new JsonNodeWithoutParent(JSON.booleanNode(true));

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("one");
		step.getIdSteps().add("two");
		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> assertEquals("Attribute finder step should take import mapping into account",
						expectedResult, result)
				);
	}

	@Test
	public void applyToResultArray() {
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
		previousResult.applyStep(step, ctx, true, null)
				.take(1)
				.subscribe(result -> assertEquals("Attribute finder step applied to result array should take import mapping into account",
						expectedResult, result)
				);
	}

}
