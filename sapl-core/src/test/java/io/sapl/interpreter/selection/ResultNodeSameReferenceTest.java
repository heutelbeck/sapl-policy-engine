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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.impl.Val;

public class ResultNodeSameReferenceTest {

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test(expected = PolicyEvaluationException.class)
	public void sameReferenceWithoutParent() throws PolicyEvaluationException {
		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
		AbstractAnnotatedJsonNode other = new JsonNodeWithoutParent(Val.ofNull());
		resultNode.sameReference(other);
	}

	@Test
	public void sameReferenceWithParentArrayTrue() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		Val arr = Val.of(array);
		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), arr, 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(Val.ofNull(), arr, 0);

		assertTrue("sameReference on JsonNodeWithParentArray should return true if reference is the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse1() throws PolicyEvaluationException {
		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.nullNode());

		ArrayNode array2 = JSON.arrayNode();
		array2.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array1), 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array2), 0);

		assertFalse("sameReference on JsonNodeWithParentArray should return false if parent reference is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse2() throws PolicyEvaluationException {
		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.nullNode());
		array1.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array1), 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array1), 1);

		assertFalse("sameReference on JsonNodeWithParentArray should return false if index is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse3() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array), 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key");

		assertFalse("sameReference on JsonNodeWithParentArray should return false if other has different type",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectTrue() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		Val obj = Val.of(object);

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), obj, "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(Val.ofNull(), obj, "key");

		assertTrue("sameReference on JsonNodeWithParentObject should return true if reference is the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse1() throws PolicyEvaluationException {
		ObjectNode object1 = JSON.objectNode();
		object1.set("key", JSON.nullNode());

		ObjectNode object2 = JSON.objectNode();
		object2.set("key", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object1), "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object2), "key");

		assertFalse("sameReference on JsonNodeWithParentObject should return false if parent reference is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse2() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key1");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key2");

		assertFalse("sameReference on JsonNodeWithParentObject should return false if keys are not equal",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse3() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(Val.ofNull(), Val.of(array), 0);

		assertFalse("sameReference on JsonNodeWithParentObject should return false if other has different type",
				resultNode.sameReference(other));
	}

}
