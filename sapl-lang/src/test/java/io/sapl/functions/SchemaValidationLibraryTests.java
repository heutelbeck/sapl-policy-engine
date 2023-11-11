/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import static io.sapl.functions.SchemaValidationLibrary.isCompliantWithSchema;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;

class SchemaValidationLibraryTests {

    private static final String COMPLIANT_JSON = "{\"name\": \"Alice\", \"age\": 25}";

    private static final String INCOMPLIANT_VALID_JSON = "{\"name\": \"Alice\", \"age\": \"25\"}";

    private static final String INVALID_JSON = "{\"name\": \"Alice\", \"age\": }";

    private static final String VALID_SCHEMA = "{ " + "  \"type\": \"object\", " + "  \"properties\": { "
            + "    \"name\": { \"type\": \"string\" }, " + "    \"age\": { \"type\": \"integer\" } " + "  }" + "}";

    private static final String INVALID_SCHEMA = "{ " + "  \"type\": \"object\", " + "  \"properties\": { "
            + "    \"name\": { \"type\": \"string\" }, " + "    \"age\": { \"type\":  } " + "  }" + "}";

    @Test
    void isCompliantWithSchema_valid_string_input() {
        var result = isCompliantWithSchema(Val.of(COMPLIANT_JSON), Val.of(VALID_SCHEMA));
        assertThat(result, is(val(true)));
    }

    @Test
    void isCompliantWithSchema_compliant_object_input() throws JsonProcessingException {
        var jsonObject = new ObjectMapper().readTree(COMPLIANT_JSON);
        var result     = isCompliantWithSchema(Val.of(jsonObject), Val.of(VALID_SCHEMA));
        assertThat(result, is(val(true)));
    }

    @Test
    void isCompliantWithSchema_non_compliant_string_input() {
        var result = isCompliantWithSchema(Val.of(INCOMPLIANT_VALID_JSON), Val.of(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void isCompliantWithSchema_non_compliant_object_input() throws JsonProcessingException {
        var jsonObject = new ObjectMapper().readTree(INCOMPLIANT_VALID_JSON);
        var result     = isCompliantWithSchema(Val.of(jsonObject), Val.of(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void isCompliantWithSchema_invalid_json_input() {
        var jsonAsVal   = Val.of(INVALID_JSON);
        var schemaAsVal = Val.of(VALID_SCHEMA);
        var result      = isCompliantWithSchema(jsonAsVal, schemaAsVal);
        assertThat(result, is(val(false)));
    }

    @Test
    void isCompliantWithSchema_invalid_json_schema() {
        var jsonAsVal   = Val.of(COMPLIANT_JSON);
        var schemaAsVal = Val.of(INVALID_SCHEMA);
        var result      = isCompliantWithSchema(jsonAsVal, schemaAsVal);
        assertThat(result, is(valError()));

    }
}
