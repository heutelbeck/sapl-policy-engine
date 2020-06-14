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
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.impl.Val;

public class ResultNodeRemoveTest {

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithoutParentNoEach() throws PolicyEvaluationException {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
		resultNode.removeFromTree(false);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithoutParentEachNoArray() throws PolicyEvaluationException {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
		resultNode.removeFromTree(true);
	}

	@Test
	public void removeOnWithoutParentEachArray() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithoutParent(Val.of(target));
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithoutParent and each should remove each element of array",
				JSON.arrayNode(), target);
	}

	@Test
	public void removeOnWithParentObjectNoEach() throws PolicyEvaluationException {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(target), "key");
		resultNode.removeFromTree(false);

		assertEquals("remove applied to JsonNodeWithParentObject without each should remove selected element",
				JSON.objectNode(), target);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithParentObjectEachNoArray() throws PolicyEvaluationException {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(target), "key");
		resultNode.removeFromTree(true);
	}

	@Test
	public void removeOnWithParentObjectEachArray() throws PolicyEvaluationException {
		ObjectNode target = JSON.objectNode();
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		target.set("key", array);

		ObjectNode expectedResult = JSON.objectNode();
		expectedResult.set("key", JSON.arrayNode());

		ResultNode resultNode = new JsonNodeWithParentObject(Val.of(array), Val.of(target), "key");
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithParentObject with each should remove each element from array",
				expectedResult, target);
	}

	@Test
	public void removeOnWithParentArrayNoEach() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(target), 0);
		resultNode.removeFromTree(false);

		assertEquals("remove applied to JsonNodeWithParentArray without each should remove selected element",
				JSON.arrayNode(), target);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithParentArrayEachNoArray() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(target), 0);
		resultNode.removeFromTree(true);
	}

	@Test
	public void removeOnWithParentArrayEachArray() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		target.add(array);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.arrayNode());

		ResultNode resultNode = new JsonNodeWithParentArray(Val.of(array), Val.of(target), 0);
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithParentArray with each should remove each element from array",
				expectedResult, target);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnArrayResultNoEach() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(target), 0));
		ArrayResultNode resultNode = new ArrayResultNode(list);

		resultNode.removeFromTree(false);
	}

	@Test
	public void removeOnArrayResultEach() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		target.add(JSON.nullNode());
		target.add(object);
		target.add(array);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.objectNode());
		expectedResult.add(JSON.arrayNode());

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(target), 0));
		list.add(new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key"));
		list.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(array), 0));
		ArrayResultNode resultNode = new ArrayResultNode(list);

		resultNode.removeFromTree(true);

		assertEquals("remove applied to ArrayResultNode with each should remove each node", expectedResult, target);
	}

}
