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
package io.sapl.geo.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.MySqlTestBase;
import io.sapl.geo.databases.DataBaseTypes;
import io.sapl.geo.databases.DatabaseStreamQuery;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class MySqlTestsIT extends MySqlTestBase {

    @BeforeAll
    void setUp() {

        commonSetUp();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedAllWKT", "GEOJSON,ExpectedAllGeoJson", "GML,ExpectedAllGML", "KML,ExpectedAllKML" })
    void Test01MySqlConnection(String responseFormat, String expectedJsonKey) throws JsonProcessingException {
        final var queryString   = String.format(templateAll, responseFormat, "geometries", "geom");
        final var expected      = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var mysqlResponse = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(mysqlResponse).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedPointWKT", "GEOJSON,ExpectedPointGeoJson", "GML,ExpectedPointGML",
            "KML,ExpectedPointKML" })
    void Test02MySqlConnectionSingleResult(String responseFormat, String expectedJsonKey)
            throws JsonProcessingException {
        final var queryString   = String.format(templatePoint, responseFormat, "geometries", "geom");
        final var expected      = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var mysqlResponse = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(mysqlResponse).expectNext(expected).expectNext(expected).verifyComplete();

    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedPointWKT2", "GEOJSON,ExpectedPointGeoJson2", "GML,ExpectedPointGML2",
            "KML,ExpectedPointKML2" })
    void Test03MySqlConnectionSingleResultSrcLatitudeFirst(String responseFormat, String expectedJsonKey)
            throws JsonProcessingException {
        insertMySqlPointInGeometries(connectionFactory);
        final var templatePoint2 = """
                    ,
                    "table":"geometries",
                    "geoColumn":"geom",
                	"singleResult": true,
                	"columns": ["name","text"],
                	"where": "name = 'point2'",
                	"srcLatitudeFirst": true
                	}
                """; // mysql delivers lat/lon if srid == 4326
        final var templatePoint3 = template.concat(templatePoint2);
        final var queryString    = String.format(templatePoint3, responseFormat);
        final var expected       = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var mysqlResponse  = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(mysqlResponse).expectNext(expected).expectNext(expected).verifyComplete();

    }

    @Test
    void Test04ErrorNonexistantTable() throws JsonProcessingException {
        final var errorTemplate = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """);
        final var queryString   = String.format(errorTemplate, "WKT", "nonExistant", "geog");
        final var mysqlResponse = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(mysqlResponse).expectError();
    }

    @Test
    void Test05ErrorInvalidTemplate() throws JsonProcessingException {
        final var queryString = "{\"invalid\":\"Template\"}";
        final var mysql       = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL);
        final var query       = Val.ofJson(queryString).get();
        final var exception   = assertThrows(PolicyEvaluationException.class, () -> {
                                  mysql.sendQuery(query);
                              });
        assertEquals("No geoColumn-name found", exception.getMessage());
    }

    private void insertMySqlPointInGeometries(ConnectionFactory connectionFactory) {
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertQuery = "INSERT INTO geometries VALUES (3, ST_GeomFromText('POINT(1 0)', 4326), 'point2', 'text point2');";
            // insert is (lon lat)
            return Mono.from(connection.createStatement(insertQuery).execute());
        }).block();
    }
}
