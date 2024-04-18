/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.springdatar2dbc.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatar2dbc.database.Person;

class QuerySelectionUtilsTests {

	private static ObjectMapper MAPPER = new ObjectMapper();
	private static ArrayNode SELECTIONS_BLACKLIST;
	private static ArrayNode SELECTIONS_WHITELIST;
	private static ArrayNode SELECTIONS_ALIAS_BLACKLIST;
	private static ArrayNode SELECTIONS_ALIAS_WHITELIST;
	private static ArrayNode SELECTIONS_ALIAS_IS_EMPTY_WHITELIST;

	@BeforeAll
	static void initJsonNodes() throws JsonMappingException, JsonProcessingException {

		SELECTIONS_BLACKLIST = MAPPER.readValue("""
				[
					{
						"type": "blacklist",
						"columns": ["firstname"]
					},
					{
						"type": "whitelist",
						"columns": ["age"]
					}
				]
				""", ArrayNode.class);

		SELECTIONS_WHITELIST = MAPPER.readValue("""
				[
					{
						"type": "whitelist",
						"columns": ["age, active"]
					}
				]
				""", ArrayNode.class);

		SELECTIONS_ALIAS_BLACKLIST = MAPPER.readValue("""
				[
					{
						"type": "blacklist",
						"columns": ["firstname, age"],
						"alias" : "p"
					}
				]
				""", ArrayNode.class);

		SELECTIONS_ALIAS_WHITELIST = MAPPER.readValue("""
				[
					{
						"type": "whitelist",
						"columns": ["firstname, age"],
						"alias" : "p"
					}
				]
				""", ArrayNode.class);

		SELECTIONS_ALIAS_IS_EMPTY_WHITELIST = MAPPER.readValue("""
				[
					{
						"type": "whitelist",
						"columns": ["firstname, age"],
						"alias" : ""
					}
				]
				""", ArrayNode.class);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_createSelectionPartBlacklist() {
		// GIVEN
		var expected = "SELECT  id, age, active  FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(SELECTIONS_BLACKLIST, Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_createSelectionPartWhitelist() {
		// GIVEN
		var expected = "SELECT age, active FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(SELECTIONS_WHITELIST, Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias1() {
		// GIVEN
		var expected = "SELECT  p.id, p.firstname, p.age, p.active  FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(SELECTIONS_ALIAS_BLACKLIST,
				Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias2() {
		// GIVEN
		var expected = "SELECT p.firstname, p.age FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(SELECTIONS_ALIAS_WHITELIST,
				Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias3() {
		// GIVEN
		var expected = "SELECT firstname, age FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(SELECTIONS_ALIAS_IS_EMPTY_WHITELIST,
				Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForMethodNameQuery_then_returnEmptyStringBecauseNoCondition() {
		// GIVEN
		var expected = "SELECT * FROM XXXXX WHERE ";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForMethodNameQuery(MAPPER.createArrayNode(), Person.class);

		// THEN
		assertEquals(result, expected);
	}

	@Test
	void when_createSelectionPartForAnnotation_then_returnSelectionPart() {
		// GIVEN
		var expected = "SELECT  id, age, active  FROM Person WHERE firstname = 'Juni'";
		var baseQuery = "SELECT * FROM Person WHERE firstname = 'Juni'";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, SELECTIONS_BLACKLIST,
				Person.class);

		// THEN
		assertEquals(expected, result);
	}

	@Test
	void when_createSelectionPartForAnnotation_then_returnQueryBecauseSelectionIsEmpty() {
		// GIVEN
		var baseQuery = "SELECT * FROM Person WHERE firstname = 'Juni'";

		// WHEN
		var result = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, MAPPER.createArrayNode(),
				Person.class);

		// THEN
		assertEquals(baseQuery, result);
	}

}
