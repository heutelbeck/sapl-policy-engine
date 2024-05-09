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
package common;

import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

public abstract class DatabaseTestBase {

	protected String authenticationTemplate = """
            {
            "user":"%s",
            "password":"%s",
        	"server":"%s",
        	"port": %s,
        	"dataBase":"%s",
        	}       	
        """;
	
    protected String template1 = """
                     {
                 	
            "responseFormat":"GEOJSON",
            "defaultCRS": 4326,
            "pollingIntervalMs":1000,
            "repetitions":2
        """;

    protected String tmpAll1 = ("""
                ,
                "table":"%s",
                "geoColumn":"%s",
            	"singleResult": false,
            	"columns": ["name"]
            }
            """);

    protected String tmpPoint1 = ("""
                ,
                "table":"%s",
                "geoColumn":"%s",
            	"singleResult": true,
            	"columns": ["name","text"],
            	"where": "name = 'point'",
            	"latitudeFirst":false
            }
            """);

    protected String expPt  = "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,0.0],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\",\"text\":\"text point\"}";
    protected String expAll = "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[0.0,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[0.0,1],[1,1],[1,0.0],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\"}]";

    protected void createTable(ConnectionFactory connectionFactory) {
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geometries (id SERIAL PRIMARY KEY, geom GEOMETRY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geographies (id SERIAL PRIMARY KEY, geog GEOGRAPHY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();
    }

    protected void insert(ConnectionFactory connectionFactory) {

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geometries VALUES (1, ST_GeomFromText('POINT(1 0)', 4326), 'point', 'text point'), (2, ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))', 4326), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geographies VALUES (1, ST_GeogFromText('SRID=4326; POINT(1 0)'), 'point', 'text point'), (2, ST_GeogFromText('SRID=4326; POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

    }
}
