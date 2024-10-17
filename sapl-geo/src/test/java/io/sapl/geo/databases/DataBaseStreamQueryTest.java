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
package io.sapl.geo.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
class DataBaseStreamQueryTest {

    private DatabaseStreamQuery databaseStreamQuery;
    private String              authenticationTemplate;
    private ObjectMapper        mapper;

    @BeforeAll
    void setup() {

        authenticationTemplate = """
                 {
                    "user":"test",
                    "password":"test",
                	"server":"test",
                	"dataBase": "dataBase",
                	"port": 123
                 }
                """;

        mapper = new ObjectMapper();
    }

    @Test
    void getDatabasenNameErrorTest() throws JsonProcessingException {
        var authenticationTemplateError = """
                 {
                    "user":"test",
                    "password":"test",
                	"server":"test",
                	"port": 123
                 }
                """;

        var error     = Val.ofJson(authenticationTemplateError).get();
        var exception = assertThrows(PolicyEvaluationException.class,
                () -> databaseStreamQuery = new DatabaseStreamQuery(error, mapper, DataBaseTypes.POSTGIS));
        assertEquals("No database-name found", exception.getMessage());
    }

    @Test
    void getTableErrorTest() throws JsonProcessingException {

        var templateWithoutTable = "{\"geoColumn\":\"test\"}";
        var requestWithoutTable  = Val.ofJson(templateWithoutTable).get();
        var auth                 = Val.ofJson(authenticationTemplate).get();
        databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
        var errorVal = Val.error("No table-name found");
        var response = databaseStreamQuery.sendQuery(requestWithoutTable);
        StepVerifier.create(response).expectNext(errorVal).thenCancel().verify();

    }

    @Test
    void getGeoColumnErrorTest() throws JsonProcessingException {

        var templateWithoutTable = "{\"dataBase\":\"test\"}";
        var requestWithoutTable  = Val.ofJson(templateWithoutTable).get();
        var auth                 = Val.ofJson(authenticationTemplate).get();
        databaseStreamQuery = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
        var errorVal = Val.error("No geoColumn-name found");
        var response = databaseStreamQuery.sendQuery(requestWithoutTable);
        StepVerifier.create(response).expectNext(errorVal).thenCancel().verify();
    }

}