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

import java.util.ArrayList;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.grammar.sapl.impl.Val;

public class ResultNodeIsTest {

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void isResultArrayOnAbstractAnnotatedJsonNode() {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
		assertFalse("isResultArray on AbstractAnnotatedJsonNode should return false", resultNode.isResultArray());
	}

	@Test
	public void isResultArrayOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertTrue("isResultArray on ArrayResultNode should return true", resultNode.isResultArray());
	}

	@Test
	public void isNodeWithoutParentOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
		assertTrue("isNodeWithoutParent on JsonNodeWithoutParent should return true", resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnArrayResultNode() {
		ResultNode resultNode = new ArrayResultNode(new ArrayList<>());
		assertFalse("isNodeWithoutParent on ArrayResultNode should return false", resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnWithParentObject() {
		ResultNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(JSON.objectNode()), "key");
		assertFalse("isNodeWithoutParent on JsonNodeWithParentObject should return false",
				resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithoutParentOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(JSON.arrayNode()), 0);
		assertFalse("isNodeWithoutParent on JsonNodeWithParentArray should return false",
				resultNode.isNodeWithoutParent());
	}

	@Test
	public void isNodeWithParentObjectOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
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
		ResultNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(JSON.objectNode()), "key");
		assertTrue("isNodeWithParentObject on JsonNodeWithParentObject should return true",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentObjectOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(JSON.arrayNode()), 0);
		assertFalse("isNodeWithParentObject on JsonNodeWithParentArray should return false",
				resultNode.isNodeWithParentObject());
	}

	@Test
	public void isNodeWithParentArrayOnWithoutParent() {
		ResultNode resultNode = new JsonNodeWithoutParent(Val.ofNull());
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
		ResultNode resultNode = new JsonNodeWithParentObject(Val.ofNull(), Val.of(JSON.objectNode()), "key");
		assertFalse("isNodeWithParentArray on JsonNodeWithParentObject should return false",
				resultNode.isNodeWithParentArray());
	}

	@Test
	public void isNodeWithParentArrayOnWithParentArray() {
		ResultNode resultNode = new JsonNodeWithParentArray(Val.ofNull(), Val.of(JSON.arrayNode()), 0);
		assertTrue("isNodeWithParentArray on JsonNodeWithParentArray should return true",
				resultNode.isNodeWithParentArray());
	}

}
