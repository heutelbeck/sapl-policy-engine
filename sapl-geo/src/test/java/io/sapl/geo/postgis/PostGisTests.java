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
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.PostgisTestBase;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class PostGisTests extends PostgisTestBase {

    @BeforeAll
    void setUp() {

        commonSetUp();
    }

    @Test
    void Test01PostGisConnectionGeometry() throws JsonProcessingException {

        var queryString = String.format(templateAll, "geometries", "geom");

        var expected = Val.ofJson(expectedAll);

        var postgis = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @Test
    void Test02PostGisConnectionGeometrySingleResult() throws JsonProcessingException {

        var queryString = String.format(templatePoint, "geometries", "geom");

        var expected = Val.ofJson(expectedPoint);
        var postgis  = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @Test
    void Test03PostGisConnectionGeography() throws JsonProcessingException {

        var queryString = String.format(templateAll, "geographies", "geog");

        var expected = Val.ofJson(expectedAll);
        var postgis  = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(postgis).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @Test
    void Test04PostGisConnectionGeographySingleResult() throws JsonProcessingException {

        var str = String.format(templatePoint, "geographies", "geog");

        var exp     = Val.ofJson(expectedPoint);
        var postgis = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper()).sendQuery(Val.ofJson(str).get());
        StepVerifier.create(postgis).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    void Test05ErrorNonExistantTable() throws JsonProcessingException {

        var str = String.format(templatePoint, "nonExistantTable", "geog");

        var postgis = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper()).sendQuery(Val.ofJson(str).get())
                .map(Val::getMessage);
        StepVerifier.create(postgis).expectNext("relation \"nonexistanttable\" does not exist").verifyComplete();
    }

    @Test
    void Test06ErrorInvalidTemplate() throws JsonProcessingException {

        var str = "{\"invalid\":\"Template\"}";

        var postgis = new PostGis(Val.ofJson(authTemplate).get(), new ObjectMapper()).sendQuery(Val.ofJson(str).get())
                .map(Val::getMessage);

        StepVerifier.create(postgis).expectNext("No geoColumn-name found").verifyComplete();
    }

}
