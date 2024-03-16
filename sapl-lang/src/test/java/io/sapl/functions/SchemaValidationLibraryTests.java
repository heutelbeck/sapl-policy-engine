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
package io.sapl.functions;

import static io.sapl.functions.SchemaValidationLibrary.isCompliant;
import static io.sapl.functions.SchemaValidationLibrary.isCompliantWithExternalSchemas;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaException;

import io.sapl.api.interpreter.Val;
import lombok.SneakyThrows;

class SchemaValidationLibraryTests {

    private static final String COMPLIANT_JSON = """
            {
                "name": "Alice",
                "age" : 25
            }
            """;

    private static final String NONCOMPLIANT_VALID_JSON = """
            {
                "name": "Alice",
                "age" : "25"
            }
            """;

    private static final String VALID_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age" : { "type": "integer" }
                }
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SneakyThrows
    void testDereference() {
        var externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "type": "object",
                    "properties" : {
                        "x": { "type": "integer" },
                        "y": { "type": "integer" },
                        "z": { "type": "integer" }
                    }
                }
                """, JsonNode.class));
        var externalsAsVal = Val.of(externals);
        var specificSchema = Val.ofJson("""
                    {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates" },
                        "B": { "$ref": "https://example.com/coordinates" },
                        "C": { "$ref": "https://example.com/coordinates" }
                    }
                }
                """);

        var valid = Val.ofJson("""
                    {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        assertThat(isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal), is(val(true)));

        var invalid = Val.ofJson("""
                    {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        assertThat(isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal), is(val(true)));
        assertThat(isCompliantWithExternalSchemas(invalid, specificSchema, externalsAsVal), is(val(false)));
    }

    @Test
    @SneakyThrows
    void testDereferenceDefs() {
        var externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "$defs" : {
                        "coord" : {
                                    "type": "object",
                                        "properties" : {
                                            "x": { "type": "integer" },
                                            "y": { "type": "integer" },
                                            "z": { "type": "integer" }
                                        }
                                   }
                    }
                }
                """, JsonNode.class));
        externals.add(MAPPER.createObjectNode());
        var externalsAsVal = Val.of(externals);
        var specificSchema = Val.ofJson("""
                    {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#/$defs/coord" },
                        "B": { "$ref": "https://example.com/coordinates#/$defs/coord" },
                        "C": { "$ref": "https://example.com/coordinates#/$defs/coord" }
                    }
                }
                """);

        var valid = Val.ofJson("""
                    {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        assertThat(isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal), is(val(true)));

        var invalid = Val.ofJson("""
                    {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        assertThat(isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal), is(val(true)));
        assertThat(isCompliantWithExternalSchemas(invalid, specificSchema, externalsAsVal), is(val(false)));
    }

    @Test
    void when_subjectIsCompliant_then_returnTrue() throws JsonProcessingException {
        var result = isCompliant(Val.ofJson(COMPLIANT_JSON), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(true)));
    }

    @Test
    void when_subjectIsNonCompliant_then_returnFalse() throws JsonProcessingException {
        var result = isCompliant(Val.ofJson(NONCOMPLIANT_VALID_JSON), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void when_subjectIsUndefined_then_returnFalse() throws JsonProcessingException {
        var result = isCompliant(Val.UNDEFINED, Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void when_subjectIsError_then_errorPropagates() throws JsonProcessingException {
        var result = isCompliant(Val.error(null, "test"), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(valError("test")));
    }

    @Test
    void when_schemaException_then_returnFalse() throws JsonProcessingException {
        var validationSubject = spy(Val.NULL);
        when(validationSubject.get()).thenThrow(new JsonSchemaException("test"));

        var result = isCompliant(validationSubject, Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }
}
