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
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class ApplyStepsArraySlicingTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	private static Val numberArray;

	private static ArrayResultNode resultArray;

	@Before
	public void fillNumberArray() {

		ArrayNode aux = JSON.arrayNode();
		numberArray = Val.of(aux);
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			aux.add(JSON.numberNode(BigDecimal.valueOf(i)));
			list.add(new JsonNodeWithParentArray(Val.of(BigDecimal.valueOf(i)), numberArray, i));
		}
		resultArray = new ArrayResultNode(list);
	}

	@Test
	public void applySlicingToNoArray() {
		ArraySlicingStep slicingStep = factory.createArraySlicingStep();
		JsonNodeWithoutParent node = new JsonNodeWithoutParent(Val.of(JSON.objectNode()));
		StepVerifier.create(node.applyStep(slicingStep, ctx, false, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applySlicingToArrayNode() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(1L), numberArray, 1));
		list.add(new JsonNodeWithParentArray(Val.of(2L), numberArray, 2));
		list.add(new JsonNodeWithParentArray(Val.of(3L), numberArray, 3));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(1, 4, null).take(1)
				.subscribe(result -> assertEquals("Slicing applied to array node should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeNegativeTo() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(7, -1, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative to should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeNegativeFrom() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(-3, 9, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative from should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayWithFromGreaterThanTo() {
		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		applySlicingToArrayNode(4, 1, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with from greater than to should return empty result array",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeWithoutTo() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		list.add(new JsonNodeWithParentArray(Val.of(9L), numberArray, 9));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(7, null, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node without to to should return corresponding items", expectedResult,
						result));
	}

	@Test
	public void applySlicingToArrayNodeWithoutFrom() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(0L), numberArray, 0));
		list.add(new JsonNodeWithParentArray(Val.of(1L), numberArray, 1));
		list.add(new JsonNodeWithParentArray(Val.of(2L), numberArray, 2));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(null, 3, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node without from should return corresponding items", expectedResult,
						result));
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeFrom() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		list.add(new JsonNodeWithParentArray(Val.of(9L), numberArray, 9));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(-3, null, null).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative from from should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStep() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(9L), numberArray, 9));
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(6L), numberArray, 6));
		list.add(new JsonNodeWithParentArray(Val.of(5L), numberArray, 5));
		list.add(new JsonNodeWithParentArray(Val.of(4L), numberArray, 4));
		list.add(new JsonNodeWithParentArray(Val.of(3L), numberArray, 3));
		list.add(new JsonNodeWithParentArray(Val.of(2L), numberArray, 2));
		list.add(new JsonNodeWithParentArray(Val.of(1L), numberArray, 1));
		list.add(new JsonNodeWithParentArray(Val.of(0L), numberArray, 0));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(null, null, -1).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative step from should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(-2, 6, -1).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative step from should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.of(8L), numberArray, 8));
		list.add(new JsonNodeWithParentArray(Val.of(7L), numberArray, 7));
		list.add(new JsonNodeWithParentArray(Val.of(6L), numberArray, 6));
		ResultNode expectedResult = new ArrayResultNode(list);

		applySlicingToArrayNode(-2, -5, -1).take(1)
				.subscribe(result -> assertEquals(
						"Slicing applied to array node with negative step from should return corresponding items",
						expectedResult, result));
	}

	@Test
	public void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() {
		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		applySlicingToArrayNode(1, 5, -1).take(1).subscribe(result -> assertEquals(
				"Slicing applied to array node with negative step and to greater than from should return empty result array",
				expectedResult, result));
	}

	@Test
	public void applySlicingStepZero() {
		StepVerifier.create(applySlicingToArrayNode(1, 5, 0)).expectError(PolicyEvaluationException.class).verify();
	}

	private Flux<ResultNode> applySlicingToArrayNode(Integer from, Integer to, Integer step) {
		ResultNode previousResult = new JsonNodeWithoutParent(numberArray);

		ArraySlicingStep slicingStep = factory.createArraySlicingStep();

		if (from != null)
			slicingStep.setIndex(BigDecimal.valueOf(from));

		if (to != null)
			slicingStep.setTo(BigDecimal.valueOf(to));

		if (step != null) {
			slicingStep.setStep(BigDecimal.valueOf(step));
		}

		return previousResult.applyStep(slicingStep, ctx, true, Val.undefined());
	}

	@Test
	public void applySlicingToResultArray() {
		ResultNode previousResult = resultArray;

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(resultArray.getNodes().get(3));
		list.add(resultArray.getNodes().get(4));
		list.add(resultArray.getNodes().get(5));
		ResultNode expectedResult = new ArrayResultNode(list);

		ArraySlicingStep step = factory.createArraySlicingStep();
		step.setIndex(BigDecimal.valueOf(3));
		step.setTo(BigDecimal.valueOf(6));

		previousResult.applyStep(step, ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Slicing applied to result array should return the correct items",
						expectedResult, result));
	}

}
