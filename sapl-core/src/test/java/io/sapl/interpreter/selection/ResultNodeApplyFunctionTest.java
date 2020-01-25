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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.tests.MockFunctionContext;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.Void;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ResultNodeApplyFunctionTest {

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static SaplFactory factory = SaplFactory.eINSTANCE;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	private static Optional<JsonNode> nullNode() {
		return Optional.of(JSON.nullNode());
	}

	@Test
	public void functionOnArrayResultNoEach() {
		ArrayNode target = JSON.arrayNode();

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0));
		ArrayResultNode resultNode = new ArrayResultNode(list);

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), false, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionOnArrayResultEach() {
		final ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());
		target.add(JSON.booleanNode(true));
		target.add(JSON.booleanNode(false));

		final ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode("dummy"));
		expectedResult.add(JSON.booleanNode(true));
		expectedResult.add(JSON.textNode("dummy"));

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0));
		list.add(new JsonNodeWithParentArray(Optional.of(JSON.booleanNode(false)), Optional.of(target), 2));
		ResultNode resultNode = new ArrayResultNode(list);

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to ArrayResultNode with each should replace each selected item", expectedResult,
				target);
	}

	@Test
	public void functionOnWithoutParentNoEach() {
		JsonNode target = JSON.nullNode();
		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(target));

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), false, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionOnWithoutParentEachNoArray() {
		JsonNode target = JSON.nullNode();

		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(target));

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionOnWithoutParentEach() {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode("dummy"));

		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(target));

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to JsonNodeWithoutParent with each should replace each node", expectedResult,
				target);
	}

	@Test
	public void functionOnWithParentObjectNoEach() {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ObjectNode expectedResult = JSON.objectNode();
		expectedResult.set("key", JSON.textNode("dummy"));

		ResultNode resultNode = new JsonNodeWithParentObject(nullNode(), Optional.of(target), "key");

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), false, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to JsonNodeWithParentObject should replace selected node", expectedResult,
				target);
	}

	@Test
	public void functionOnWithParentObjectEach() {
		ObjectNode target = JSON.objectNode();
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		target.set("key", array);

		ObjectNode expectedResult = JSON.objectNode();
		ArrayNode expectedArray = JSON.arrayNode();
		expectedArray.add(JSON.textNode("dummy"));
		expectedResult.set("key", expectedArray);

		ResultNode resultNode = new JsonNodeWithParentObject(Optional.of(array), Optional.of(target), "key");

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to JsonNodeWithParentObject with each should replace each item of selected node",
				expectedResult, target);
	}

	@Test
	public void functionOnWithParentObjectEachNoArray() {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentObject(nullNode(), Optional.of(target), "key");

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionOnWithParentArrayNoEach() {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode("dummy"));

		ResultNode resultNode = new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0);

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), false, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to JsonNodeWithParentArray should replace selected node", expectedResult,
				target);
	}

	@Test
	public void functionOnWithParentArrayEach() {
		ArrayNode target = JSON.arrayNode();
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		target.add(array);

		ArrayNode expectedResult = JSON.arrayNode();
		ArrayNode expectedArray = JSON.arrayNode();
		expectedArray.add(JSON.textNode("dummy"));
		expectedResult.add(expectedArray);

		ResultNode resultNode = new JsonNodeWithParentArray(Optional.of(array), Optional.of(target), 0);

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectNext(Void.INSTANCE).verifyComplete();

		assertEquals("function applied to JsonNodeWithParentArray with each should replace each item of selected node",
				expectedResult, target);
	}

	@Test
	public void functionOnWithParentArrayEachNoArray() {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0);

		StepVerifier.create(resultNode.applyFilter("dummy", factory.createArguments(), true, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionWithImport() {
		Map<String, String> imports = new HashMap<>();
		imports.put("short", "dummy");
		ctx = new EvaluationContext(functionCtx, variableCtx, imports);

		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode("dummy"));

		ResultNode resultNode = new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0);

		StepVerifier.create(resultNode.applyFilter("short", null, false, ctx, false)).expectNext(Void.INSTANCE)
				.verifyComplete();

		assertEquals("function with imports should replace selected node", expectedResult, target);
	}

	@Test
	public void functionWithException() {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0);

		StepVerifier.create(resultNode.applyFilter("EXCEPTION", factory.createArguments(), false, ctx, false))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void functionWithArguments() {
		final ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		final ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode("dummy"));

		ResultNode resultNode = new JsonNodeWithParentArray(nullNode(), Optional.of(target), 0);

		Arguments arguments = factory.createArguments();
		BasicValue argumentExpression = factory.createBasicValue();
		Value value = factory.createTrueLiteral();
		argumentExpression.setValue(value);
		arguments.getArgs().add(argumentExpression);

		StepVerifier.create(resultNode.applyFilter("dummy", arguments, false, ctx, false)).expectNext(Void.INSTANCE)
				.verifyComplete();

		assertEquals("function with arguments should replace selected node", expectedResult, target);
	}

}
