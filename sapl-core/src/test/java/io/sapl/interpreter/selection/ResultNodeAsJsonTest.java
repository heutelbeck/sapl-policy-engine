/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
