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
package io.sapl.geo.connection.postgis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class PostGisConnectionTests {

    String template = """
                     {
                     "user":"%s",
                     "password":"%s",
                 	"server":"%s",
                 	"port": %s,
                 	"dataBase":"%s",
            "responseFormat":"GEOJSON",
                 	"defaultCRS": 4326,
                 	"pollingIntervalMs":1000,
                 	"repetitions":2
                 """;

    @Container
    private static final PostgreSQLContainer<?> postgisContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))
            .withUsername("test").withPassword("test").withDatabaseName("test");

    @BeforeAll
    public void setUp() throws Exception {

        template = String.format(template, postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());
        var a = template;

        var connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder().host(postgisContainer.getHost())
                        .port(postgisContainer.getMappedPort(5432)).database(postgisContainer.getDatabaseName())
                        .username(postgisContainer.getUsername()).password(postgisContainer.getPassword()).build());

        createTable(connectionFactory);
        insert(connectionFactory);
    }

    @Test
    public void Test01PostGisConnectionGemoetry() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": false,
                	"columns": ["name"]
                }
                """);
        var str = String.format(tmp, "geometries", "geom");

        var exp = Val.ofJson(
                "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1,0.0],[1,1],[0.0,1],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\"}]");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test02PostGisConnectionGeometrySingleResult() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name"],
                	"where": "name = 'point'"
                }
                """);
        var str = String.format(tmp, "geometries", "geom");

        var exp = Val.ofJson(
                "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"}");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test03PostGisConnectionGeography() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": false,
                	"columns": ["name", "text"]
                }
                """);
        var str = String.format(tmp, "geographies", "geog");

        var exp = Val.ofJson(
                "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\",\"text\":\"text point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1,0.0],[1,1],[0.0,1],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\",\"text\":\"text polygon\"}]");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test04PostGisConnectionGeographySingleResult() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """);
        var str = String.format(tmp, "geographies", "geog");

        var exp = Val.ofJson(
                "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\",\"text\":\"text point\"}");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test05Error() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """);
        var str = String.format(tmp, "nonExistant", "geog");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectError();
    }

    private void createTable(ConnectionFactory connectionFactory) {
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geometries (id SERIAL PRIMARY KEY, geom GEOMETRY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geographies (id SERIAL PRIMARY KEY, geog GEOGRAPHY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();
    }

    private void insert(ConnectionFactory connectionFactory) {

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geometries VALUES (1, ST_GeomFromText('POINT(1 1)', 4326), 'point', 'text point'), (2, ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))', 4326), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geographies VALUES (1, ST_GeogFromText('SRID=4326; POINT(1 1)'), 'point', 'text point'), (2, ST_GeogFromText('SRID=4326; POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

    }

}
