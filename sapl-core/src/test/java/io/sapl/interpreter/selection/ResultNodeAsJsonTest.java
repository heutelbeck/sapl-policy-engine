package io.sapl.interpreter.selection;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.Test;

public class ResultNodeAsJsonTest {
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void asJsonWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(Optional.of(JSON.nullNode()));
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithoutParent should return contained JSON node",
				Optional.of(expectedResult), resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void asJsonWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(Optional.of(JSON.nullNode()),
				Optional.of(JSON.objectNode()), "key");
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithParentObject should return contained JSON node",
				Optional.of(expectedResult), resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void asJsonWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(Optional.of(JSON.nullNode()), Optional.of(JSON.arrayNode()),
				0);
		JsonNode expectedResult = JSON.nullNode();

		assertEquals("asJson method JsonNodeWithParentArray should return contained JSON node",
				Optional.of(expectedResult), resultNode.asJsonWithoutAnnotations());
	}

	@Test
	public void asJsonArrayResultNode() {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithoutParent(Optional.of(JSON.nullNode())));
		list.add(new JsonNodeWithoutParent(Optional.of(JSON.booleanNode(true))));
		ArrayResultNode resultNode = new ArrayResultNode(list);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.booleanNode(true));

		assertEquals("asJson method ArrayResultNode should return array node containing the JSON nodes",
				Optional.of(expectedResult), resultNode.asJsonWithoutAnnotations());
	}
}
