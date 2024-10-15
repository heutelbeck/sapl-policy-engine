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
package io.sapl.geo.databases;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;

@TestInstance(Lifecycle.PER_CLASS)
class DataBaseStreamQueryTest {

	private DatabaseStreamQuery databaseStreamQuery;
	private String authenticationTemplate;
	private ObjectMapper mapper;

	@BeforeAll
	void setup() {

		authenticationTemplate = """
				 {
				    "user":"test",
				    "password":"test",
					"server":"test",
					"dataBase": "dataBase",
					"port": 123
				 }
				""";

		mapper = new ObjectMapper();
	}

	@Test
	void getDatabasenNameErrorTest() throws JsonProcessingException {
		var authenticationTemplateError = """
				 {
				    "user":"test",
				    "password":"test",
					"server":"test",
					"port": 123
				 }
				""";

		var error = Val.ofJson(authenticationTemplateError).get();
		var exception = assertThrows(PolicyEvaluationException.class,
				() -> databaseStreamQuery = new DatabaseStreamQuery(error, mapper, DataBaseTypes.POSTGIS));
		assertEquals("No database-name found", exception.getMessage());
	}

	@Test
	void getPortTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var authTemplate = """
				 {
				    "user":"test",
				    "password":"test",
					"server":"test",
					"dataBase": "dataBase"
				 }
				""";
		var auth = Val.ofJson(authTemplate).get();
		var databaseStreamQueryPostGis = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var databaseStreamQueryMySql = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.MYSQL);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getPort", JsonNode.class);
		method.setAccessible(true);
		int portPostGis = (int) method.invoke(databaseStreamQueryPostGis, auth);
		int portMySql = (int) method.invoke(databaseStreamQueryMySql, auth);
		assertEquals(5432, portPostGis);
		assertEquals(3306, portMySql);
	}

	@Test
	void getTableErrorTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var template = "{\"table\":\"test\"}";
		var request = Val.ofJson(template).get();
		var templateEmpty = "{ }";
		var requestEmpty = Val.ofJson(templateEmpty).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getTable", JsonNode.class);
		method.setAccessible(true);
		var result = (String) method.invoke(databaseStreamQuery, request);
		var exception = assertThrows(InvocationTargetException.class,
				() -> method.invoke(databaseStreamQuery, requestEmpty));
		var cause = exception.getCause();
		assertTrue(cause instanceof PolicyEvaluationException);
		assertEquals("No table-name found", cause.getMessage());
		assertEquals("test", result);
	}

	@Test
	void getGeoColumnErrorTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		var templateEmpty = "{}";
		var requestEmpty = Val.ofJson(templateEmpty).get();
		var template = "{\"geoColumn\":\"test\"}";
		var request = Val.ofJson(template).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getGeoColumn", JsonNode.class);
		method.setAccessible(true);
		var result = (String) method.invoke(databaseStreamQuery, request);
		var exception = assertThrows(InvocationTargetException.class,
				() -> method.invoke(databaseStreamQuery, requestEmpty));
		var cause = exception.getCause();
		assertTrue(cause instanceof PolicyEvaluationException);
		assertEquals("No geoColumn-name found", cause.getMessage());
		assertEquals("test", result);
	}

	@Test
	void getColumnsTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var templateNoArray = "{\"columns\": \"column\"}";
		var requestNoArray = Val.ofJson(templateNoArray).get();
		var templateNoColumns = "{}";
		var requestNoColumns = Val.ofJson(templateNoColumns).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getColumns", JsonNode.class, ObjectMapper.class);
		method.setAccessible(true);
		var resultNoArray = (String[]) method.invoke(databaseStreamQuery, requestNoArray, mapper);
		var resultNoColumns = (String[]) method.invoke(databaseStreamQuery, requestNoColumns, mapper);
		assertArrayEquals(new String[] { "column" }, resultNoArray);
		assertArrayEquals(new String[] {}, resultNoColumns);
	}

	@Test
	void getWhereTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var templateEmpty = "{}";
		var requestEmpty = Val.ofJson(templateEmpty).get();
		var template = "{\"where\":\"test\"}";
		var request = Val.ofJson(template).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getWhere", JsonNode.class);
		method.setAccessible(true);
		var result = (String) method.invoke(databaseStreamQuery, request);
		var resultEmpty = (String) method.invoke(databaseStreamQuery, requestEmpty);
		assertEquals("WHERE test", result);
		assertEquals("", resultEmpty);
	}

	@Test
	void getDefaultCrsTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var templateEmpty = "{}";
		var requestEmpty = Val.ofJson(templateEmpty).get();
		var template = "{\"defaultCRS\": 1111}";
		var request = Val.ofJson(template).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getDefaultCRS", JsonNode.class);
		method.setAccessible(true);
		var result = (int) method.invoke(databaseStreamQuery, request);
		var resultEmpty = (int) method.invoke(databaseStreamQuery, requestEmpty);
		assertEquals(1111, result);
		assertEquals(4326, resultEmpty);
	}

	@Test
	void getSingleResultTest()
			throws JsonProcessingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		var templateEmpty = "{}";
		var requestEmpty = Val.ofJson(templateEmpty).get();
		var templateSingleResult = "{\"singleResult\": true}";
		var requestSingleResult = Val.ofJson(templateSingleResult).get();
		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var method = DatabaseStreamQuery.class.getDeclaredMethod("getSingleResult", JsonNode.class);
		method.setAccessible(true);
		var resultEmpty = (boolean) method.invoke(databaseStreamQuery, requestEmpty);
		var resultTrue = (boolean) method.invoke(databaseStreamQuery, requestSingleResult);
		assertEquals(false, resultEmpty);
		assertEquals(true, resultTrue);
	}

	@Test
	void longOrDefaultTest() throws JsonProcessingException, NoSuchMethodException, SecurityException,
			IllegalAccessException, InvocationTargetException {

		var auth = Val.ofJson(authenticationTemplate).get();
		databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
		var templateWithValue = "{\"someField\": 42}";
		var requestWithValue = Val.ofJson(templateWithValue).get();
		var templateInvalidValue = "{ \"someField\": \"notANumber\"}";
		var requestInvalidValue = Val.ofJson(templateInvalidValue).get();
		var templateWithoutField = "{}";
		var requestWithoutField = Val.ofJson(templateWithoutField).get();
		var method = DatabaseStreamQuery.class.getDeclaredMethod("longOrDefault", JsonNode.class, String.class,
				long.class);
		method.setAccessible(true);
		var resultWithValue = (long) method.invoke(databaseStreamQuery, requestWithValue, "someField", 0);
		var resultWithoutField = (long) method.invoke(databaseStreamQuery, requestWithoutField, "someField", 10);
		assertEquals(42L, resultWithValue);
		assertEquals(10L, resultWithoutField);
		var exception = assertThrows(InvocationTargetException.class,
				() -> method.invoke(databaseStreamQuery, requestInvalidValue, "someField", 0));
		var cause = exception.getCause();
		assertTrue(cause instanceof PolicyEvaluationException);
	}
}
