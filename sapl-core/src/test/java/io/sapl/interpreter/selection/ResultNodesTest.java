package io.sapl.interpreter.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;

public class ResultNodesTest {
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void asJsonWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithoutParent should return contained JSON node", expectedResult,
				resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void asJsonWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), JSON.objectNode(), "key");
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithParentObject should return contained JSON node", expectedResult,
				resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void asJsonWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), JSON.arrayNode(), 0);
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithParentArray should return contained JSON node", expectedResult,
				resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void isResultArrayOnAbstractAnnotatedJsonNode() {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		assertFalse("isResultArray on AbstractAnnotatedJsonNode should return false", resultNode.isResultArray());
	}

	@Test
	public void isResultArrayOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertTrue("isResultArray on ArrayResultNode should return false", resultNode.isResultArray());
	}

	@Test
	public void isNodeWithoutParentOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		assertTrue("isNodeWithoutParent on JsonNodeWithoutParent should return true", resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertFalse("isNodeWithoutParent on ArrayResultNode should return false", resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), JSON.objectNode(), "key");
		assertFalse("isNodeWithoutParent on JsonNodeWithParentObject should return false",
				resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), JSON.arrayNode(), 0);
		assertFalse("isNodeWithoutParent on JsonNodeWithParentArray should return false",
				resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithParentObjectOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		assertFalse("isNodeWithParentObject on JsonNodeWithoutParent should return false",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentObjectOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertFalse("isNodeWithParentObject on ArrayResultNode should return false",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentObjectOnWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), JSON.objectNode(), "key");
		assertTrue("isNodeWithParentObject on JsonNodeWithParentObject should return true",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentObjectOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), JSON.arrayNode(), 0);
		assertFalse("isNodeWithParentObject on JsonNodeWithParentArray should return false",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentArrayOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		assertFalse("isNodeWithParentArray on JsonNodeWithoutParent should return false",
				resultNode.isNodeWithParentArray());
	}

	@Test
	public void isNodeWithParentArrayOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertFalse("isNodeWithParentArray on ArrayResultNode should return false", resultNode.isNodeWithParentArray());
	}

	@Test
	public void isNodeWithParentArrayOnWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), JSON.objectNode(), "key");
		assertFalse("isNodeWithParentArray on JsonNodeWithParentObject should return false",
				resultNode.isNodeWithParentArray());
	}

	@Test
	public void isNodeWithParentArrayOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), JSON.arrayNode(), 0);
		assertTrue("isNodeWithParentArray on JsonNodeWithParentArray should return true",
				resultNode.isNodeWithParentArray());
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithoutParentNoEach() throws PolicyEvaluationException {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		resultNode.removeFromTree(false);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithoutParentEachNoArray() throws PolicyEvaluationException {
		ResultNode resultNode = new JsonNodeWithoutParent(JSON.nullNode());
		resultNode.removeFromTree(true);
	}

	@Test
	public void removeOnWithoutParentEachArray() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithoutParent(target);
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithoutParent and each should remove each element of array",
				JSON.arrayNode(), target);
	}

	@Test
	public void removeOnWithParentObjectNoEach() throws PolicyEvaluationException {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), target, "key");
		resultNode.removeFromTree(false);

		assertEquals("remove applied to JsonNodeWithParentObject without each should remove selected element",
				JSON.objectNode(), target);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithParentObjectEachNoArray() throws PolicyEvaluationException {
		ObjectNode target = JSON.objectNode();
		target.set("key", JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentObject(JSON.nullNode(), target, "key");
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

		ResultNode resultNode = new JsonNodeWithParentObject(array, target, "key");
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithParentObject with each should remove each element from array",
				expectedResult, target);
	}

	@Test
	public void removeOnWithParentArrayNoEach() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), target, 0);
		resultNode.removeFromTree(false);

		assertEquals("remove applied to JsonNodeWithParentArray without each should remove selected element",
				JSON.arrayNode(), target);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeOnWithParentArrayEachNoArray() throws PolicyEvaluationException {
		ArrayNode target = JSON.arrayNode();
		target.add(JSON.nullNode());

		ResultNode resultNode = new JsonNodeWithParentArray(JSON.nullNode(), target, 0);
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

		ResultNode resultNode = new JsonNodeWithParentArray(array, target, 0);
		resultNode.removeFromTree(true);

		assertEquals("remove applied to JsonNodeWithParentArray with each should remove each element from array",
				expectedResult, target);
	}

}
