/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class PostGISTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final String JSON_CONF = "{\"serverAdress\": \"localhost\", \"port\": \"5432\","
			+ "\"db\": \"db_sample\", \"table\": \"geofences\", \"username\": \"uname\", \"password\": \"pw\","
			+ "\"geometryColName\": \"geom\", \"idColName\": \"fences\", \"pkColName\": \"gid\",\"from\": 0,"
			+ "\"flipCoordinates\": true %s}";

	private static final String PROJECTION_CONFIG = ",\"projectionSRID\": 12345," + "\"until\": 1";

	private static final String JSON_ANSWER = "{\"name\":{\"type\":\"Point\",\"coordinates\":[1,1]}}";

	private PostGISConfig pgConfFromJson;

	private PostGISConfig pgProjectionConfFromJson;

	private JsonNode jsonConf;

	@BeforeEach
	void setUp() throws IOException {
		jsonConf = MAPPER.readValue(String.format(JSON_CONF, PROJECTION_CONFIG), JsonNode.class);
		pgProjectionConfFromJson = MAPPER.convertValue(jsonConf, PostGISConfig.class);

		jsonConf = MAPPER.readValue(String.format(JSON_CONF, ""), JsonNode.class);
		pgConfFromJson = MAPPER.convertValue(jsonConf, PostGISConfig.class);
	}

	@Test
	void builderConstructor() {
		PostGISConfig conf = new PostGISConfig();
		assertNotNull(new PostGISConnection(conf),
				"PostGISConnection object fails to be constructed with empty PostGISConfig object.");
	}

	@Test
	void jsonConstructor() {
		PostGISConnection conn = new PostGISConnection(jsonConf);
		assertEquals(pgConfFromJson, conn.getConfig(),
				"PostGISConnection object fails to be constructed with proper JSON configuration.");
	}

	@Test
	void confConstructor() {
		PostGISConnection conn = new PostGISConnection(pgConfFromJson);
		assertEquals(pgConfFromJson, conn.getConfig(),
				"PostGISConnection object fails to be constructed with proper PostGISConfig object.");
	}

	@Test
	void equalsTest() {
		EqualsVerifier.forClass(PostGISConfig.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.verify();
	}

	@Test
	void responseTest() throws IOException {
		PostGISConfig configMock = mock(PostGISConfig.class);
		when(configMock.getTable()).thenReturn("testTable");

		PostGISConnection conn = new PostGISConnection(configMock);
		PostGISConnection connSpy = spy(conn);

		ObjectNode response = JSON.objectNode();
		JsonNode jsonPoint = MAPPER.readValue("{\"type\": \"Point\",\"coordinates\":[10.0, 15.0]}", JsonNode.class);
		response.set("testPoint", jsonPoint);

		doReturn(response).when(connSpy).retrieveGeometries();

		assertEquals(
				"{\"identifier\":\"testTable\",\"altitude\":0.0,\"accuracy\":0.0,\"trust\":0.0,"
						+ "\"geofences\":{\"testPoint\":{\"type\":\"Point\",\"coordinates\":[10.0,15.0]}}}",
				connSpy.toGeoPIPResponse().toString());
	}

	@Test
	void dbConnection() throws SQLException {
		PostGISConfig configMock = mock(PostGISConfig.class);
		Connection connMock = mock(Connection.class);
		Statement sMock = mock(Statement.class);
		ResultSet rsMock = mock(ResultSet.class);

		when(configMock.getConnection()).thenReturn(connMock);
		when(configMock.buildQuery()).thenReturn("");
		when(connMock.createStatement()).thenReturn(sMock);
		when(sMock.executeQuery(anyString())).thenReturn(rsMock);

		when(rsMock.next()).thenReturn(true).thenReturn(false);
		when(rsMock.getString(eq(1))).thenReturn("name");
		when(rsMock.getString(eq(2))).thenReturn("POINT (1 1)");

		PostGISConnection c = new PostGISConnection(configMock);
		assertEquals(JSON_ANSWER, c.retrieveGeometries().toString(),
				"Database connection is not correctly established or result not correctly formatted.");
	}

	@Test
	void buildQuery() {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		doReturn(true).when(pgConfSpy).verifySqlArguments();

		assertEquals("SELECT fences, ST_AsText(ST_FlipCoordinates(geom)) FROM geofences WHERE gid>=0;",
				pgConfSpy.buildQuery(), "PostGIS SQL Query is not correctly build.");
	}

	@Test
	void buildQueryException() {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		doReturn(false).when(pgConfSpy).verifySqlArguments();
		assertThrows(PolicyEvaluationException.class, () -> pgConfSpy.buildQuery());
	}

	@Test
	void buildProjectionQuery() {
		PostGISConfig pgConfSpy = spy(pgProjectionConfFromJson);
		doReturn(true).when(pgConfSpy).verifySqlArguments();
		assertEquals(
				"SELECT fences, ST_AsText(ST_FlipCoordinates(ST_Transform(geom,12345))) FROM geofences WHERE gid>=0 AND gid<=1;",
				pgConfSpy.buildQuery(), "PostGIS SQL Query is not correctly build.");
	}

	@Test
	void dbException() throws SQLException {
		PostGISConfig configMock = mock(PostGISConfig.class);
		when(configMock.getConnection()).thenThrow(new SQLException());
		assertThrows(PolicyEvaluationException.class, () -> new PostGISConnection(configMock).retrieveGeometries());
	}

	@Test
	void colsExistTrue() throws SQLException {
		ResultSet rsMock = mock(ResultSet.class);
		when(rsMock.next()).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("test");

		assertTrue(PostGISConfig.colsExist(rsMock, "test"),
				"colsExist() returns false even though column title exists in ResultSet.");
	}

	@Test
	void colsExistFalse() throws SQLException {
		ResultSet rsMock = mock(ResultSet.class);
		when(rsMock.next()).thenReturn(false);
		assertFalse(PostGISConfig.colsExist(rsMock, "test"),
				"colsExist() returns true even though column title does not exist in ResultSet.");
	}

	@Test
	void sqlException() throws SQLException, PolicyEvaluationException {
		PostGISConfig pgConfSpy = spy(pgProjectionConfFromJson);
		doThrow(new SQLException()).when(pgConfSpy).getConnection();
		assertThrows(PolicyEvaluationException.class, () -> pgConfSpy.verifySqlArguments());
	}

	@Test
	void verifySqlTrue() throws SQLException, PolicyEvaluationException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		Connection connMock = mock(Connection.class);
		ResultSet rsMock = mock(ResultSet.class);
		DatabaseMetaData dbmMock = mock(DatabaseMetaData.class);

		doReturn(connMock).when(pgConfSpy).getConnection();
		when(connMock.getMetaData()).thenReturn(dbmMock);
		when(dbmMock.getColumns(eq(null), eq(null), anyString(), eq(null))).thenReturn(rsMock);
		when(rsMock.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("fences").thenReturn("gid").thenReturn("geom");

		assertTrue(pgConfSpy.verifySqlArguments(), "Arguments for SQL query are not correctly validated.");
	}

	@Test
	void verifySqlFalse() throws SQLException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		Connection connMock = mock(Connection.class);
		ResultSet rsMock = mock(ResultSet.class);
		DatabaseMetaData dbmMock = mock(DatabaseMetaData.class);

		doReturn(connMock).when(pgConfSpy).getConnection();
		when(connMock.getMetaData()).thenReturn(dbmMock);
		when(dbmMock.getColumns(eq(null), eq(null), anyString(), eq(null))).thenReturn(rsMock);
		when(rsMock.next()).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("fences");

		assertFalse(pgConfSpy.verifySqlArguments(), "Arguments for SQL query are not correctly validated.");
	}

	@Test
	void buildUrl() {
		assertEquals("jdbc:postgresql://localhost:5432/db_sample?", pgConfFromJson.buildUrl(),
				"PostGIS-URL is not correctly built.");
	}

	@Test
	void buildUrlWithParams() throws IOException {
		String config = "{\"serverAdress\": \"localhost\", \"port\": \"5432\", \"db\": \"db_sample\","
				+ "\"table\": \"geofences\", \"username\": \"uname\", \"password\": \"pw\","
				+ "\"geometryColName\": \"geom\", \"idColName\": \"fences\", \"pkColName\": \"gid\","
				+ "\"from\": 0, \"ssl\": true, \"urlParams\": \"test=test\"}";
		PostGISConfig pgConfWithParams = MAPPER.readValue(config, PostGISConfig.class);
		assertEquals("jdbc:postgresql://localhost:5432/db_sample?ssl=true&test=test", pgConfWithParams.buildUrl(),
				"PostGIS-URL is not correctly built.");
	}

}
