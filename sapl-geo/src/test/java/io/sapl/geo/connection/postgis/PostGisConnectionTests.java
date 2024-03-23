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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class PostGisConnectionTests {

    static String            address;
    static ConnectionFactory connectionFactory;

    String userName;
    String password;
    String template = """
                {
                "user":"%s",
                "password":"%s",
            	"server":"%s",
            	"port": %s,
            	"dataBase":"%s",
            	"table":"%s",
            	"geoColumn":"%s",
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
        address = postgisContainer.getJdbcUrl();

        userName = postgisContainer.getUsername();
        password = postgisContainer.getPassword();

        var a = postgisContainer.getHost();
        var b = postgisContainer.getMappedPort(5432);

        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(postgisContainer.getHost()).port(postgisContainer.getMappedPort(5432))
                .database(postgisContainer.getDatabaseName()).username(userName).password(password).build());

        createTable();
        insert();
        var z = 1;
    }

    
    @Test
    public void Test01Select() {
        StepVerifier.create(selectPoints()).expectNext("{\"type\":\"Point\",\"coordinates\":[1,1]}")
                .expectNext("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}")
                .verifyComplete();
    }

    @Test
    public void Test02PostGisConnectionGemoetry() throws JsonProcessingException {

        var tmp2 = """
                    ,
                	"singleResult": false,
                	"columns": ["name"]
                }
                """;

        var tmp = template.concat(tmp2);
        var str = String.format(tmp, userName, password, postgisContainer.getHost(),
                postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName(), "geometries", "geom");

        var node = Val.ofJson(str).get();

        var exp = Val.ofJson(
                "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1,0.0],[1,1],[0.0,1],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\"}]");

        var postgis = PostGisConnection.connect(node, new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test03PostGisConnectionGeometrySingleResult() throws JsonProcessingException {

        var tmp2 = """
                    ,
                	"singleResult": true,
                	"columns": ["name"],
                	"where": "name = 'point'"
                }
                """;

        var tmp = template.concat(tmp2);
        var str = String.format(tmp, userName, password, postgisContainer.getHost(),
                postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName(), "geometries", "geom");

        var node = Val.ofJson(str).get();

        var exp = Val.ofJson(
                "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"}");

        var postgis = PostGisConnection.connect(node, new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test04PostGisConnectionGeography() throws JsonProcessingException {

        var tmp2 = """
                    ,
                	"singleResult": false,
                	"columns": ["name", "text"]
                }
                """;

        var tmp = template.concat(tmp2);
        var str = String.format(tmp, userName, password, postgisContainer.getHost(),
                postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName(), "geographies", "geog");

        var node = Val.ofJson(str).get();

        var exp = Val.ofJson(
                "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\",\"text\":\"text point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1,0.0],[1,1],[0.0,1],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\",\"text\":\"text polygon\"}]");

        var postgis = PostGisConnection.connect(node, new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test05PostGisConnectionGeographySingleResult() throws JsonProcessingException {

        var tmp2 = """
                    ,
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """;

        var tmp = template.concat(tmp2);
        var str = String.format(tmp, userName, password, postgisContainer.getHost(),
                postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName(), "geographies", "geog");

        var node = Val.ofJson(str).get();

        var exp = Val.ofJson(
                "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\",\"text\":\"text point\"}");

        var postgis = PostGisConnection.connect(node, new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    
    private void createTable() {
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS geometries (id SERIAL PRIMARY KEY, geom GEOMETRY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();
        
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS geographies (id SERIAL PRIMARY KEY, geog GEOGRAPHY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();
    }

    private void insert() {

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geometries VALUES (1, ST_GeomFromText('POINT(1 1)', 4326), 'point', 'text point'), (2, ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))', 4326), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geographies VALUES (1, ST_GeogFromText('SRID=4326; POINT(1 1)'), 'point', 'text point'), (2, ST_GeogFromText('SRID=4326; POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

        
    }

    private Flux<String> selectPoints() {
        return Mono.from(connectionFactory.create())
                .flatMapMany(connection -> Mono.from(
                        connection.createStatement("SELECT ST_AsGeoJSON(geom) as geom FROM geometries;").execute())
                        .flatMapMany(result -> result.map((row, rowMetadata) -> row.get("geom", String.class))));
    }

}
