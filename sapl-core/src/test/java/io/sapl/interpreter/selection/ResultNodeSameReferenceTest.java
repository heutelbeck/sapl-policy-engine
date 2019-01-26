package io.sapl.interpreter.selection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import org.junit.Test;

public class ResultNodeSameReferenceTest {
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test(expected = PolicyEvaluationException.class)
	public void sameReferenceWithoutParent() throws PolicyEvaluationException {
		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		AbstractAnnotatedJsonNode other = new JsonNodeWithoutParent(JSON.nullNode());
		resultNode.sameReference(other);
	}

	@Test
	public void sameReferenceWithParentArrayTrue() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), array, 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(JSON.nullNode(), array, 0);

		assertTrue("sameReference on JsonNodeWithParentArray should return true if reference is the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse1() throws PolicyEvaluationException {
		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.nullNode());

		ArrayNode array2 = JSON.arrayNode();
		array2.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), array1, 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(JSON.nullNode(), array2, 0);

		assertFalse("sameReference on JsonNodeWithParentArray should return false if parent reference is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse2() throws PolicyEvaluationException {
		ArrayNode array1 = JSON.arrayNode();
		array1.add(JSON.nullNode());
		array1.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), array1, 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(JSON.nullNode(), array1, 1);

		assertFalse("sameReference on JsonNodeWithParentArray should return false if index is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentArrayFalse3() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), array, 0);
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(JSON.nullNode(), object, "key");

		assertFalse("sameReference on JsonNodeWithParentArray should return false if other has different type",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectTrue() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), object, "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(JSON.nullNode(), object, "key");

		assertTrue("sameReference on JsonNodeWithParentObject should return true if reference is the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse1() throws PolicyEvaluationException {
		ObjectNode object1 = JSON.objectNode();
		object1.set("key", JSON.nullNode());

		ObjectNode object2 = JSON.objectNode();
		object2.set("key", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), object1, "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(JSON.nullNode(), object2, "key");

		assertFalse("sameReference on JsonNodeWithParentObject should return false if parent reference is not the same",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse2() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), object, "key1");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentObject(JSON.nullNode(), object, "key2");

		assertFalse("sameReference on JsonNodeWithParentObject should return false if keys are not equal",
				resultNode.sameReference(other));
	}

	@Test
	public void sameReferenceWithParentObjectFalse3() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.nullNode());

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		AbstractAnnotatedJsonNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), object, "key");
		AbstractAnnotatedJsonNode other = new JsonNodeWithParentArray(JSON.nullNode(), array, 0);

		assertFalse("sameReference on JsonNodeWithParentObject should return false if other has different type",
				resultNode.sameReference(other));
	}
}
