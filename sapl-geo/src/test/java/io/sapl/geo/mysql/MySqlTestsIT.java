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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.MySqlTestBase;
import io.sapl.geo.databases.DataBaseTypes;
import io.sapl.geo.databases.DatabaseConnection;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class MySqlTestsIT extends MySqlTestBase {

    @BeforeAll
    void setUp() {

        commonSetUp();
    }

    @Test
    void Test01MySqlConnection() throws JsonProcessingException {
        var queryString = String.format(templateAll, "geometries", "geom");

        var expected      = Val.ofJson(expectedAll);
        var mysqlResponse = new DatabaseConnection(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(mysqlResponse).expectNext(expected).expectNext(expected).verifyComplete();
    }

    @Test
    void Test02MySqlConnectionSingleResult() throws JsonProcessingException {
        var queryString = String.format(templatePoint, "geometries", "geom");

        var expected = Val.ofJson(expectedPoint);

        var mysqlResponse = new DatabaseConnection(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(mysqlResponse).expectNext(expected).expectNext(expected).verifyComplete();

    }

    @Test
    void Test03ErrorNonexistantTable() throws JsonProcessingException {
        var errorTemplate = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """);
        var queryString   = String.format(errorTemplate, "nonExistant", "geog");

        var mysqlResponse = new DatabaseConnection(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get());
        StepVerifier.create(mysqlResponse).expectError();
    }

    @Test
    void Test04ErrorInvalidTemplate() throws JsonProcessingException {
        var queryString = "{\"invalid\":\"Template\"}";

        var mysqlResponse = new DatabaseConnection(Val.ofJson(authTemplate).get(), new ObjectMapper(),
                DataBaseTypes.MYSQL).sendQuery(Val.ofJson(queryString).get()).map(Val::getMessage);

        StepVerifier.create(mysqlResponse).expectNext("No geoColumn-name found").verifyComplete();
    }

}
