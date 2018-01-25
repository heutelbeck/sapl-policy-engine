package io.sapl.interpreter.pip.geo;
/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class PostGISTest {
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

	@Before
	public void init() throws IOException {
		jsonConf = MAPPER.readValue(String.format(JSON_CONF, PROJECTION_CONFIG), JsonNode.class);
		pgProjectionConfFromJson = MAPPER.convertValue(jsonConf, PostGISConfig.class);

		jsonConf = MAPPER.readValue(String.format(JSON_CONF, ""), JsonNode.class);
		pgConfFromJson = MAPPER.convertValue(jsonConf, PostGISConfig.class);
	}

	@Test
	public void builderConstructor() {
		PostGISConfig conf = new PostGISConfig();
		assertNotNull("PostGISConnection object fails to be constructed with empty PostGISConfig object.",
				new PostGISConnection(conf));
	}

	@Test
	public void jsonConstructor() {
		PostGISConnection conn = new PostGISConnection(jsonConf);
		assertEquals("PostGISConnection object fails to be constructed with proper JSON configuration.",
				conn.getConfig(), pgConfFromJson);
	}

	@Test
	public void confConstructor() {
		PostGISConnection conn = new PostGISConnection(pgConfFromJson);
		assertEquals("PostGISConnection object fails to be constructed with proper PostGISConfig object.",
				conn.getConfig(), pgConfFromJson);
	}

	@Test
	public void equalsTest() {
		EqualsVerifier.forClass(PostGISConfig.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.verify();
	}

	@Test
	public void responseTest() throws IOException, FunctionException, AttributeException {
		PostGISConfig configMock = mock(PostGISConfig.class);
		when(configMock.getTable()).thenReturn("testTable");

		PostGISConnection conn = new PostGISConnection(configMock);
		PostGISConnection connSpy = spy(conn);

		ObjectNode response = JSON.objectNode();
		JsonNode jsonPoint = MAPPER.readValue("{\"type\": \"Point\",\"coordinates\":[10.0, 15.0]}", JsonNode.class);
		response.set("testPoint", jsonPoint);

		doReturn(response).when(connSpy).retrieveGeometries();

		assertEquals("", connSpy.toGeoPIPResponse().toString(),
				"{\"identifier\":\"testTable\",\"altitude\":0.0,\"accuracy\":0.0,\"trust\":0.0,"
						+ "\"geofences\":{\"testPoint\":{\"type\":\"Point\",\"coordinates\":[10.0,15.0]}}}");
	}

	@Test
	public void dbConnection() throws SQLException, FunctionException, AttributeException {
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
		assertEquals("Database connection is not correctly established or result not correctly formatted.", JSON_ANSWER,
				c.retrieveGeometries().toString());
	}

	@Test
	public void buildQuery() throws AttributeException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		doReturn(true).when(pgConfSpy).verifySqlArguments();

		assertEquals("PostGIS SQL Query is not correctly build.",
				"SELECT fences, ST_AsText(ST_FlipCoordinates(geom)) FROM geofences WHERE gid>=0;",
				pgConfSpy.buildQuery());
	}

	@Test(expected = AttributeException.class)
	public void buildQueryException() throws AttributeException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		doReturn(false).when(pgConfSpy).verifySqlArguments();

		assertNotEquals("PostGIS SQL Query is build even though parameters are not validated.", pgConfSpy.buildQuery(),
				"SELECT fences, ST_AsText(ST_FlipCoordinates(geom)) FROM geofences WHERE gid>=0;");
	}

	@Test
	public void buildProjectionQuery() throws AttributeException {
		PostGISConfig pgConfSpy = spy(pgProjectionConfFromJson);
		doReturn(true).when(pgConfSpy).verifySqlArguments();
		assertEquals("PostGIS SQL Query is not correctly build.",
				"SELECT fences, ST_AsText(ST_FlipCoordinates(ST_Transform(geom,12345))) FROM geofences WHERE gid>=0 AND gid<=1;",
				pgConfSpy.buildQuery());
	}

	@Test(expected = AttributeException.class)
	public void dbException() throws SQLException, FunctionException, AttributeException {
		PostGISConfig configMock = mock(PostGISConfig.class);
		when(configMock.getConnection()).thenThrow(new SQLException());

		PostGISConnection c = new PostGISConnection(configMock);
		assertTrue("Handling of SQLException in PostGISConnection works not as expected.",
				c.retrieveGeometries().isNull());
	}

	@Test
	public void colsExistTrue() throws SQLException {
		ResultSet rsMock = mock(ResultSet.class);
		when(rsMock.next()).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("test");

		assertTrue("colsExist() returns false even though column title exists in ResultSet.",
				PostGISConfig.colsExist(rsMock, "test"));
	}

	@Test
	public void colsExistFalse() throws SQLException {
		ResultSet rsMock = mock(ResultSet.class);
		when(rsMock.next()).thenReturn(false);

		assertFalse("colsExist() returns true even though column title does not exist in ResultSet.",
				PostGISConfig.colsExist(rsMock, "test"));
	}

	@Test(expected = AttributeException.class)
	public void sqlException() throws AttributeException, SQLException {
		PostGISConfig pgConfSpy = spy(pgProjectionConfFromJson);
		doThrow(new SQLException()).when(pgConfSpy).getConnection();

		assertTrue("SQLException is not correctly handled.", pgConfSpy.verifySqlArguments());
	}

	@Test
	public void verifySqlTrue() throws AttributeException, SQLException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		Connection connMock = mock(Connection.class);
		ResultSet rsMock = mock(ResultSet.class);
		DatabaseMetaData dbmMock = mock(DatabaseMetaData.class);

		doReturn(connMock).when(pgConfSpy).getConnection();
		when(connMock.getMetaData()).thenReturn(dbmMock);
		when(dbmMock.getColumns(eq(null), eq(null), anyString(), eq(null))).thenReturn(rsMock);
		when(rsMock.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("fences").thenReturn("gid").thenReturn("geom");

		assertTrue("Arguments for SQL query are not correctly validated.", pgConfSpy.verifySqlArguments());
	}

	@Test
	public void verifySqlFalse() throws AttributeException, SQLException {
		PostGISConfig pgConfSpy = spy(pgConfFromJson);
		Connection connMock = mock(Connection.class);
		ResultSet rsMock = mock(ResultSet.class);
		DatabaseMetaData dbmMock = mock(DatabaseMetaData.class);

		doReturn(connMock).when(pgConfSpy).getConnection();
		when(connMock.getMetaData()).thenReturn(dbmMock);
		when(dbmMock.getColumns(eq(null), eq(null), anyString(), eq(null))).thenReturn(rsMock);
		when(rsMock.next()).thenReturn(true).thenReturn(false);
		when(rsMock.getString(anyString())).thenReturn("fences");

		assertFalse("Arguments for SQL query are not correctly validated.", pgConfSpy.verifySqlArguments());
	}

	@Test
	public void buildUrl() {
		assertEquals("PostGIS-URL is not correctly built.", "jdbc:postgresql://localhost:5432/db_sample?",
				pgConfFromJson.buildUrl());
	}

	@Test
	public void buildUrlWithParams() throws IOException {
		String config = "{\"serverAdress\": \"localhost\", \"port\": \"5432\", \"db\": \"db_sample\","
				+ "\"table\": \"geofences\", \"username\": \"uname\", \"password\": \"pw\","
				+ "\"geometryColName\": \"geom\", \"idColName\": \"fences\", \"pkColName\": \"gid\","
				+ "\"from\": 0, \"ssl\": true, \"urlParams\": \"test=test\"}";
		PostGISConfig pgConfWithParams = MAPPER.readValue(config, PostGISConfig.class);
		assertEquals("PostGIS-URL is not correctly built.",
				"jdbc:postgresql://localhost:5432/db_sample?ssl=true&test=test", pgConfWithParams.buildUrl());
	}
}
