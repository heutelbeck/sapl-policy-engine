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

@TestInstance(Lifecycle.PER_CLASS)
class DataBaseStreamQueryTest {

    private String       authenticationTemplate;
    private ObjectMapper mapper;

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
    void getDatabaseNameErrorTest() throws JsonProcessingException {
        final var authenticationTemplateError = """
                 {
                    "user":"test",
                    "password":"test",
                	"server":"test",
                	"port": 123
                 }
                """;
        final var error                       = Val.ofJson(authenticationTemplateError).get();
        final var databaseStreamQuery         = new DatabaseStreamQuery(error, mapper, DataBaseTypes.POSTGIS);
        final var exception                   = assertThrows(PolicyEvaluationException.class,
                () -> databaseStreamQuery.sendQuery(null));
        assertEquals("No database-name found", exception.getMessage());
    }

    @Test
    void getTableErrorTest() throws JsonProcessingException {
        final var templateWithoutTable = "{\"geoColumn\":\"test\"}";
        final var requestWithoutTable  = Val.ofJson(templateWithoutTable).get();
        final var auth                 = Val.ofJson(authenticationTemplate).get();
        final var databaseStreamQuery  = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
        final var exception            = assertThrows(PolicyEvaluationException.class, () -> {
                                           databaseStreamQuery.sendQuery(requestWithoutTable);
                                       });
        assertEquals("No table-name found", exception.getMessage());
    }

    @Test
    void getGeoColumnErrorTest() throws JsonProcessingException {
        final var templateWithoutTable = "{\"dataBase\":\"test\"}";
        final var requestWithoutTable  = Val.ofJson(templateWithoutTable).get();
        final var auth                 = Val.ofJson(authenticationTemplate).get();
        final var databaseStreamQuery  = new DatabaseStreamQuery(auth, mapper, DataBaseTypes.POSTGIS);
        final var exception            = assertThrows(PolicyEvaluationException.class, () -> {
                                           databaseStreamQuery.sendQuery(requestWithoutTable);
                                       });
        assertEquals("No geoColumn-name found", exception.getMessage());
    }

}
