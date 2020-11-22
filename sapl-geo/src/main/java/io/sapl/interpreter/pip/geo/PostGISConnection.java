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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import org.locationtech.jts.geom.Geometry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.functions.GeometryBuilder;
import lombok.Getter;

public class PostGISConnection {

	private static final String NAME_REGEX = "[^a-zA-Z0-9]+";

	private static final String EMPTY_STRING = "";

	private static final int NAME_INDEX = 1;

	private static final int GEOM_INDEX = 2;

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static Pattern jsonNamePattern = Pattern.compile(NAME_REGEX);

	protected static final String AF_TEST = "AF_TEST";

	protected static final String TEST_OKAY = "ok";

	@Getter
	private PostGISConfig config;

	public PostGISConnection(PostGISConfig conf) {
		config = conf;
	}

	public PostGISConnection(JsonNode conf) {
		if (!AF_TEST.equals(conf.asText())) {
			config = MAPPER.convertValue(conf, PostGISConfig.class);
		}
	}

	public JsonNode toGeoPIPResponse() throws FunctionException, AttributeException {
		if (config == null) {
			return JSON.textNode(TEST_OKAY);
		} else {
			return GeoPIPResponse.builder().identifier(config.getTable()).geofences(retrieveGeometries()).build()
					.toJsonNode();
		}
	}

	public ObjectNode retrieveGeometries() throws FunctionException, AttributeException {
		try (Connection conn = config.getConnection()) {

			try (Statement s = conn.createStatement()) {

				try (ResultSet rs = s.executeQuery(config.buildQuery())) {
					return formatResultSet(rs);
				}
			}
		} catch (SQLException e) {
			throw new AttributeException(e);
		}
	}

	private static ObjectNode formatResultSet(ResultSet rs) throws SQLException, FunctionException {
		ObjectNode geometries = JSON.objectNode();
		while (rs.next()) {
			String name = jsonNamePattern.matcher(rs.getString(NAME_INDEX)).replaceAll(EMPTY_STRING);
			Geometry geom = GeometryBuilder.fromWkt(rs.getString(GEOM_INDEX));
			geometries.set(name, GeometryBuilder.toJsonNode(geom));
		}
		return geometries;
	}

}
