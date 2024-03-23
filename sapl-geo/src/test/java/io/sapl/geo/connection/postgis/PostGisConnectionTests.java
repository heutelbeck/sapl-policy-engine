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

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.Point;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class PostGisConnectionTests {

    static String            address;
    static ConnectionFactory connectionFactory;

    @Container
    private static final PostgreSQLContainer<?> postgisContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))
            .withUsername("test").withPassword("test").withDatabaseName("test");

    @BeforeAll
    public static void setUp() throws Exception {
        address = postgisContainer.getJdbcUrl();

        String username = postgisContainer.getUsername();
        String password = postgisContainer.getPassword();

        var a = postgisContainer.getHost();
        var b = postgisContainer.getMappedPort(5432);

        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(postgisContainer.getHost()).port(postgisContainer.getMappedPort(5432))
                .database(postgisContainer.getDatabaseName()).username(username).password(password).build());

        createTable().block();
        insertPoint().block();

    }

//    public void insert() {
//
//		 createTableAndInsertPoint().doOnNext(x->System.out.println("test")).subscribe();
//		 StepVerifier.create(createTableAndInsertPoint())
//                .expectNextCount(1)
//                .verifyComplete();
//    }

    private static Mono<? extends Result> createTable() {
        return Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS geometries (id SERIAL PRIMARY KEY, geom GEOMETRY);";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        });
    }
    
    private static Mono<? extends Result> insertPoint() {

        return Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geometries (geom) VALUES (ST_GeomFromText('POINT(1 1)', 4326)), (ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'));";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        });

    }

    

    @Test
    @Order(1)
    public void Select() {
        StepVerifier.create(selectPoints())
        .expectNext("{\"type\":\"Point\",\"coordinates\":[1,1]}")
        .expectNext("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}")
        .verifyComplete();
    }

    private static Flux<String> selectPoints() {
        return Mono.from(connectionFactory.create())
                .flatMapMany(connection -> Mono
                        .from(connection.createStatement("SELECT ST_AsGeoJSON(geom) as geom FROM geometries;").execute())
                        .flatMapMany(result -> result.map((row, rowMetadata) -> row.get("geom", String.class))))
                ;
    }

}
