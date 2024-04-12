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

import common.DatabaseTestBase;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class PostGisConnectionTests extends DatabaseTestBase{

	private String tmpAll;
	private String tmpPoint;
	private String template;
	
    @Container
    private static final PostgreSQLContainer<?> postgisContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))
            .withUsername("test").withPassword("test").withDatabaseName("test");

    @BeforeAll
    public void setUp() throws Exception {

        template = String.format(template1, postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());

        tmpAll = template.concat(tmpAll1);
		
		tmpPoint = template.concat(tmpPoint1);	
        
        var connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder().host(postgisContainer.getHost())
                        .port(postgisContainer.getMappedPort(5432)).database(postgisContainer.getDatabaseName())
                        .username(postgisContainer.getUsername()).password(postgisContainer.getPassword()).build());

        createTable(connectionFactory);
        insert(connectionFactory);
    }

    @Test
    public void Test01PostGisConnectionGeometry() throws JsonProcessingException {

        var str = String.format(tmpAll, "geometries", "geom");

        var exp = Val.ofJson(expAll);

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test02PostGisConnectionGeometrySingleResult() throws JsonProcessingException {

        
        var str = String.format(tmpPoint, "geometries", "geom");

        var exp = Val.ofJson(expPt);
        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test03PostGisConnectionGeography() throws JsonProcessingException {

        
        var str = String.format(tmpAll, "geographies", "geog");

        var exp = Val.ofJson(expAll);
        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test04PostGisConnectionGeographySingleResult() throws JsonProcessingException {

        var str = String.format(tmpPoint, "geographies", "geog");

        var exp = Val.ofJson(expPt);
        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test05Error() throws JsonProcessingException {

        var str = String.format(tmpPoint, "nonExistant", "geog");

        var postgis = PostGisConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(postgis).expectError();
    }

    

}
