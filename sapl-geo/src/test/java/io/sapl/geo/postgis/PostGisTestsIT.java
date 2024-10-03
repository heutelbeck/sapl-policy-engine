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
package io.sapl.geo.postgis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.PostgisTestBase;
import io.sapl.geo.databases.DataBaseTypes;
import io.sapl.geo.databases.DatabaseStreamQuery;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class PostGisTestsIT extends PostgisTestBase {

    @BeforeAll
    void setUp() {

        commonSetUp();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedAllWKT", "GEOJSON,ExpectedAllGeoJson", "GML,ExpectedAllGML", "KML,ExpectedAllKML" })
    void Test01PostGisConnectionGeometry(String responseFormat, String expectedJsonKey) throws JsonProcessingException {

        var queryString = String.format(templateAll, responseFormat, "geometries", "geom");
        var expected    = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var postgis     = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.POSTGIS).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedPointWKT", "GEOJSON,ExpectedPointGeoJson", "GML,ExpectedPointGML",
            "KML,ExpectedPointKML" })
    void Test02PostGisConnectionGeometrySingleResult(String responseFormat, String expectedJsonKey)
            throws JsonProcessingException {

        var queryString = String.format(templatePoint, responseFormat, "geometries", "geom");
        var expected    = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var postgis     = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.POSTGIS).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedAllWKT", "GEOJSON,ExpectedAllGeoJson", "GML,ExpectedAllGML", "KML,ExpectedAllKML" })
    void Test03PostGisConnectionGeography(String responseFormat, String expectedJsonKey)
            throws JsonProcessingException {

        var queryString = String.format(templateAll, responseFormat, "geographies", "geog");
        var expected    = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var postgis     = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.POSTGIS).sendQuery(Val.ofJson(queryString).get()).map(Val::get)
                .map(JsonNode::toPrettyString);
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ExpectedPointWKT", "GEOJSON,ExpectedPointGeoJson", "GML,ExpectedPointGML",
            "KML,ExpectedPointKML" })
    void Test04PostGisConnectionGeographySingleResult(String responseFormat, String expectedJsonKey)
            throws JsonProcessingException {

        var str      = String.format(templatePoint, responseFormat, "geographies", "geog");
        var expected = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var postgis  = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.POSTGIS).sendQuery(Val.ofJson(str).get()).map(Val::get).map(JsonNode::toPrettyString);
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @Test
    void Test05ErrorNonExistantTable() throws JsonProcessingException {

        var str     = String.format(templatePoint, "WKT", "nonExistantTable", "geog");
        var postgis = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(), DataBaseTypes.POSTGIS)
                .sendQuery(Val.ofJson(str).get()).map(Val::getMessage);
        StepVerifier.create(postgis).expectNext("relation \"nonexistanttable\" does not exist").verifyComplete();
    }

    @Test
    void Test06ErrorInvalidTemplate() throws JsonProcessingException {

        var str     = "{\"invalid\":\"Template\"}";
        var postgis = new DatabaseStreamQuery(Val.ofJson(authTemplate).get(), new ObjectMapper(), DataBaseTypes.POSTGIS)
                .sendQuery(Val.ofJson(str).get()).map(Val::getMessage);
        StepVerifier.create(postgis).expectNext("No geoColumn-name found").verifyComplete();
    }
}
