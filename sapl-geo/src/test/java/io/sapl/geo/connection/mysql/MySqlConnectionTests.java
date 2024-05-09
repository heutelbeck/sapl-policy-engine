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
package io.sapl.geo.connection.mysql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.MySqlTestBase;
import io.sapl.api.interpreter.Val;

import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
class MySqlConnectionTests extends MySqlTestBase {

    @BeforeAll
    void setUp() throws Exception {

        commonSetUp();
    }

    @Test
    void Test01MySqlConnection() throws JsonProcessingException {

        var str = String.format(tmpAll, "geometries", "geom");

        var exp   = Val.ofJson(expAll);
        var mysql = new MySqlConnection(Val.ofJson(authTemp).get(), Val.ofJson(str).get(), new ObjectMapper())
                .connect(Val.ofJson(str).get());
        StepVerifier.create(mysql).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    void Test02MySqlConnectionSingleResult() throws JsonProcessingException {

        var str = String.format(tmpPoint, "geometries", "geom");

        var exp = Val.ofJson(expPt);

        var mysql = new MySqlConnection(Val.ofJson(authTemp).get(), Val.ofJson(str).get(), new ObjectMapper())
                .connect(Val.ofJson(str).get());
        StepVerifier.create(mysql).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    void Test03Error() throws JsonProcessingException {

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

        var mysql = new MySqlConnection(Val.ofJson(authTemp).get(), Val.ofJson(str).get(), new ObjectMapper())
                .connect(Val.ofJson(str).get());
        StepVerifier.create(mysql).expectError();
    }

}
