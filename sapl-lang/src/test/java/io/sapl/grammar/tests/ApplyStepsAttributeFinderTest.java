/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.MockUtil;
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

	private static EvaluationContext ctx = new EvaluationContext(new MockAttributeContext(), functionCtx, variableCtx,
			imports);

	@Test
	public void applyToPolicyTarget() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));
		AttributeFinderStep step = factory.createAttributeFinderStep();
		MockUtil.mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(step);
		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToPolicySetTarget() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));
		AttributeFinderStep step = factory.createAttributeFinderStep();
		MockUtil.mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(step);
		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void exceptionDuringEvaluation() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));
		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("EXCEPTION");
		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyWithImport() {
		ctx.getImports().put("short", "ATTRIBUTE");
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));
		ResultNode expectedResult = new JsonNodeWithoutParent(Val.of(JSON.textNode("ATTRIBUTE")));
		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("short");
		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Attribute finder step should take import mapping into account",
						expectedResult, result));
	}

	@Test
	public void applyWithoutImport() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.booleanNode(true)));
		ResultNode expectedResult = new JsonNodeWithoutParent(Val.of(JSON.booleanNode(true)));
		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("one");
		step.getIdSteps().add("two");
		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Attribute finder step should take import mapping into account",
						expectedResult, result));
	}

	@Test
	public void applyToResultArray() {
		AbstractAnnotatedJsonNode previousNode = new JsonNodeWithoutParent(Val.of(JSON.booleanNode(true)));
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(previousNode);
		ArrayResultNode previousResult = new ArrayResultNode(list);

		ArrayNode expectedArray = JSON.arrayNode();
		expectedArray.add(JSON.booleanNode(true));
		ResultNode expectedResult = new JsonNodeWithoutParent(Val.of(expectedArray));

		AttributeFinderStep step = factory.createAttributeFinderStep();
		step.getIdSteps().add("one");
		step.getIdSteps().add("two");
		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"Attribute finder step applied to result array should take import mapping into account",
						expectedResult, result));
	}

}
