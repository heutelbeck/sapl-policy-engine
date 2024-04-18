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
package io.sapl.springdatacommon.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.springdatacommon.utils.TestUtils;


class JsonNodeStructureTests {
	
	private static JsonNode TEMPLATE1; 
	private static JsonNode VALID_OBLIGATION1_FOR_TEMPLATE1;
	private static JsonNode VALID_OBLIGATION2_FOR_TEMPLATE1;
	private static JsonNode UNVALID_OBLIGATION1_FOR_TEMPLATE1;
	private static JsonNode UNVALID_OBLIGATION2_FOR_TEMPLATE1;
	private static JsonNode UNVALID_OBLIGATION3_FOR_TEMPLATE1;
	private static JsonNode UNVALID_OBLIGATION4_FOR_TEMPLATE1;
	private static JsonNode UNVALID_OBLIGATION5_FOR_TEMPLATE1;
	
	private static JsonNode TEMPLATE2; 
	private static JsonNode VALID_OBLIGATION1_FOR_TEMPLATE2;
	private static JsonNode VALID_OBLIGATION2_FOR_TEMPLATE2;
	private static JsonNode UNVALID_OBLIGATION1_FOR_TEMPLATE2;
	private static JsonNode UNVALID_OBLIGATION2_FOR_TEMPLATE2;
	private static JsonNode UNVALID_OBLIGATION3_FOR_TEMPLATE2;
	private static JsonNode UNVALID_OBLIGATION4_FOR_TEMPLATE2;
	private static JsonNode UNVALID_OBLIGATION5_FOR_TEMPLATE2;
	
	private static JsonNode TEMPLATE3; 
	private static JsonNode VALID_OBLIGATION1_FOR_TEMPLATE3;
	private static JsonNode UNVALID_OBLIGATION1_FOR_TEMPLATE3;
	private static JsonNode UNVALID_OBLIGATION2_FOR_TEMPLATE3;
	
	private static JsonNode TEMPLATE4; 
	private static JsonNode VALID_OBLIGATION1_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION1_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION2_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION3_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION4_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION5_FOR_TEMPLATE4;
	private static JsonNode UNVALID_OBLIGATION6_FOR_TEMPLATE4;



	@BeforeAll
	private static void initJsonNodes() throws JsonMappingException, JsonProcessingException {
		var mapper = new ObjectMapper();
		initJsonNodesForTemplate1(mapper);
		initJsonNodesForTemplate2(mapper);
		initJsonNodesForTemplate3(mapper);
		initJsonNodesForTemplate4(mapper);
	}

	@Test
	void when_compare_then_returnTrueIfStructureOfJsonNodeIsValid() {
		// Valid JsonNodes
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION1_FOR_TEMPLATE1, TEMPLATE1));
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION2_FOR_TEMPLATE1, TEMPLATE1));
		
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION1_FOR_TEMPLATE2, TEMPLATE2));
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION2_FOR_TEMPLATE2, TEMPLATE2));
		
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION1_FOR_TEMPLATE3, TEMPLATE3));
		
		assertTrue(JsonNodeStructure.compare(VALID_OBLIGATION1_FOR_TEMPLATE4, TEMPLATE4));
	}
	
	@Test
	void when_compare_then_returnFalseIfStructureOfJsonNodeIsValid() throws JsonMappingException, JsonProcessingException {
		// Not valid JsonNodes
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION1_FOR_TEMPLATE1, TEMPLATE1));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION2_FOR_TEMPLATE1, TEMPLATE1));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION3_FOR_TEMPLATE1, TEMPLATE1));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION4_FOR_TEMPLATE1, TEMPLATE1));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION5_FOR_TEMPLATE1, TEMPLATE1));

		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION1_FOR_TEMPLATE2, TEMPLATE2));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION2_FOR_TEMPLATE2, TEMPLATE2));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION3_FOR_TEMPLATE2, TEMPLATE2));
		
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION1_FOR_TEMPLATE3, TEMPLATE3));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION2_FOR_TEMPLATE3, TEMPLATE3));

		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION1_FOR_TEMPLATE4, TEMPLATE4));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION2_FOR_TEMPLATE4, TEMPLATE4));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION3_FOR_TEMPLATE4, TEMPLATE4));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION4_FOR_TEMPLATE4, TEMPLATE4));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION5_FOR_TEMPLATE4, TEMPLATE4));
		assertFalse(JsonNodeStructure.compare(UNVALID_OBLIGATION6_FOR_TEMPLATE4, TEMPLATE4));
	}
	
	@Test
	void when_compare_then_throwAccessDeniedException() {
		// GIVEN 
		var errorMessage1 = """
				Obligation does not satisfy the required structure. 
				
				 Obligation: [ "firstname" ] 
				 Template:   [ "", "" ]
				""";	
		var errorMessage2 = """
				Obligation does not satisfy the required structure. 
				
				 Obligation: [ "firstname", "lastname", "age" ] 
				 Template:   [ "", "" ]
				""";	
		
		// WHEN
		var accessDeniedException1 = assertThrows(AccessDeniedException.class, () -> {
			JsonNodeStructure.compare(UNVALID_OBLIGATION4_FOR_TEMPLATE2, TEMPLATE2);
		});
		
		var accessDeniedException2 = assertThrows(AccessDeniedException.class, () -> {
			JsonNodeStructure.compare(UNVALID_OBLIGATION5_FOR_TEMPLATE2, TEMPLATE2);
		});
		
		// THEN
		assertEquals(TestUtils.removeWhitespace(errorMessage1), TestUtils.removeWhitespace(accessDeniedException1.getMessage()));
		assertEquals(TestUtils.removeWhitespace(errorMessage2), TestUtils.removeWhitespace(accessDeniedException2.getMessage()));
	}
	
	private static void initJsonNodesForTemplate1(ObjectMapper mapper) throws JsonMappingException, JsonProcessingException {

		TEMPLATE1 = mapper.readTree("""
				{
				  "selection": {
				    "type": "",
				    "columns": [
				      ""
				    ]
				  }
				}
			""");
		
		VALID_OBLIGATION1_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist",
						"columns": ["firstname"]
					}	
	             }
				""");
		
		VALID_OBLIGATION2_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist",
						"columns": ["firstname", "lastname", "age"]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION1_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": 123,
						"columns": ["firstname", "lastname", "age"]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION2_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist",
						"columns": ["firstname", 123, "age"]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION3_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist"
					}	
	             }
				""");
		
		UNVALID_OBLIGATION4_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist",
						"columns": [ 321 ]						
					}	
	             }
				""");
		
		UNVALID_OBLIGATION5_FOR_TEMPLATE1 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist",
						"columns": [ true ]						
					}	
	             }
				""");
	}
	
	private static void initJsonNodesForTemplate2(ObjectMapper mapper) throws JsonMappingException, JsonProcessingException {
		TEMPLATE2 = mapper.readTree("""
				{
				  "selection": {
				    "type": "",
				    "columns": [
				      "", ""
				    ]
				  }
				}
			""");
		
		VALID_OBLIGATION1_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": ["firstname", "lastname"]
					}	
	             }
				""");
		
		VALID_OBLIGATION2_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": ["firstname", "lastname"],
						"alias" : "t"
					}	
	             }
				""");
		
		UNVALID_OBLIGATION1_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": 123,
						"columns": ["firstname", "lastname"]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION2_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": ["firstname", 123]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION3_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				       "selection": {
						"type": "blacklist"
					}	
	             }
				""");
		
		UNVALID_OBLIGATION4_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": ["firstname"]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION5_FOR_TEMPLATE2 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": ["firstname", "lastname", "age"]
					}	
	             }
				""");
	}
	
	private static void initJsonNodesForTemplate3(ObjectMapper mapper) throws JsonMappingException, JsonProcessingException {
		TEMPLATE3 = mapper.readTree("""
				{
				  "selection": {
				    "type": "",
				    "columns": [
				      true
				    ]
				  }
				}
			""");
		
		VALID_OBLIGATION1_FOR_TEMPLATE3 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ false, true, false ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION1_FOR_TEMPLATE3 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ 123 ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION2_FOR_TEMPLATE3 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ "firstname" ]
					}	
	             }
				""");
	}
	
	private static void initJsonNodesForTemplate4(ObjectMapper mapper) throws JsonMappingException, JsonProcessingException {
		TEMPLATE4 = mapper.readTree("""
				{
				  "selection": {
				    "type": "",
				    "columns": [
				      123
				    ]
				  }
				}
			""");
		
		VALID_OBLIGATION1_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ 123, 321, 213 ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION1_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ true ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION2_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ "firstname" ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION3_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": [ "", "" ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION4_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": "blacklist",
						"columns": { 
							"id": "123"
						}
					}	
	             }
				""");
		
		UNVALID_OBLIGATION5_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				    "selection": {
						"type": "",
						"columns": [ "firstname" ]
					}	
	             }
				""");
		
		UNVALID_OBLIGATION6_FOR_TEMPLATE4 = mapper.readTree("""
				{
	               "type": "r2dbcQueryManipulation",
	               "conditions": [ "role = 'USER'" ],
				   "selection": {
						"type": null,
						"columns": [ null ]
					}	
	             }
				""");
	}

}
