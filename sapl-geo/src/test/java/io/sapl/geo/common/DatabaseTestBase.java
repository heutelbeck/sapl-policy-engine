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
package io.sapl.geo.common;

import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

public abstract class DatabaseTestBase extends TestBase {

	protected String templateAll;
	protected String templatePoint;
	protected String authTemplate;
	protected String authenticationTemplate = """
			 {
			    "user":"%s",
			    "password":"%s",
				"server":"%s",
				"port": %s,
				"dataBase":"%s"
			 }
			""";

	protected String template = """
			 {
			    "responseFormat":"GEOJSON",
			    "pollingIntervalMs":1000,
			    "repetitions":2,
			    "responseFormat":"%s"
			""";

	protected String templateAll1 = ("""
			    ,
			    "defaultCRS": 4326,
			    "table":"%s",
			    "geoColumn":"%s",
				"columns": "name"
				}
			""");

	protected String templatePoint1 = ("""
			    ,
			    "table":"%s",
			    "geoColumn":"%s",
				"singleResult": true,
				"columns": ["name","text"],
				"where": "name = 'point'"
				}
			""");

	protected void createGeometryTable(ConnectionFactory connectionFactory) {
		Mono.from(connectionFactory.create()).flatMap(connection -> {
			String createTableQuery = """
					        CREATE TABLE geometries (
					                id SERIAL PRIMARY KEY,
					                geom GEOMETRY,
					                name CHARACTER VARYING(25),
					                text CHARACTER VARYING(25)
					        );
					""";
			return Mono.from(connection.createStatement(createTableQuery).execute());
		}).block();
	}

	protected void createGeographyTable(ConnectionFactory connectionFactory) {

		Mono.from(connectionFactory.create()).flatMap(connection -> {
			String createTableQuery = """
					        CREATE TABLE geographies (
					                id SERIAL PRIMARY KEY,
					                geog GEOGRAPHY,
					                name CHARACTER VARYING(25),
					                text CHARACTER VARYING(25)
					         );
					""";
			return Mono.from(connection.createStatement(createTableQuery).execute());
		}).block();
	}

	protected void insertGeometries(ConnectionFactory connectionFactory) {

		Mono.from(connectionFactory.create()).flatMap(connection -> {
			String insertQuery = """
					      INSERT INTO geometries VALUES
					              (1, ST_GeomFromText('POINT(1 0)'), 'point', 'text point'),
					              (2, ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');
					"""; // insert is (lon lat)
			return Mono.from(connection.createStatement(insertQuery).execute());
		}).block();
	}

	protected void insertGeographies(ConnectionFactory connectionFactory) {

		Mono.from(connectionFactory.create()).flatMap(connection -> {
			String insertQuery = """
					INSERT INTO geographies VALUES
					    (1, ST_GeogFromText('SRID=4326; POINT(1 0)'), 'point', 'text point'),
					    (2, ST_GeogFromText('SRID=4326; POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');
					""";// insert is (lon lat)
			return Mono.from(connection.createStatement(insertQuery).execute());
		}).block();

	}
}
