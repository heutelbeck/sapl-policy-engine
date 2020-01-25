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

		ResultNode expectedResult = new JsonNodeWithoutParent(Optional.of(JSON.nullNode()));

		resultNode.applyStep(step, ctx, false, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("applyStep on ArrayResultNode should return correct ResultNode",
						expectedResult, result));
	}

	@Test
	public void applyStepAnnotatedJsonNode() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(array));

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.ZERO);

		ResultNode expectedResult = new JsonNodeWithParentArray(Optional.of(JSON.nullNode()), Optional.of(array), 0);

		resultNode.applyStep(step, ctx, false, Optional.empty()).take(1)
				.subscribe(result -> assertEquals(
						"applyStep on AbstractAnnotatedJsonNode should return correct ResultNode", expectedResult,
						result));
	}

}
