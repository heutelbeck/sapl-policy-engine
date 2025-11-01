/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.functions.SchemaValidationLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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

    private static final String NECRONOMICON_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "ritualName": { "type": "string" },
                    "requiredSacrifices": { "type": "integer", "minimum": 0 },
                    "forbiddenKnowledge": { "type": "boolean" }
                },
                "required": ["ritualName", "requiredSacrifices"]
            }
            """;

    private static final String ELDER_SIGN_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "geometry": { "enum": ["pentagram", "hexagram", "elder_sign"] },
                    "powerLevel": { "type": "number", "minimum": 0, "maximum": 100 }
                },
                "required": ["geometry"]
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SneakyThrows
    void when_validatingWithExternalSchemas_then_referencesAreResolved() {
        val externals = MAPPER.createArrayNode();
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
        val externalsAsVal = Val.of(externals);
        val specificSchema = Val.ofJson("""
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

        val valid = Val.ofJson("""
                    {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        val validResult = isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal);
        assertThat(validResult.isBoolean()).isTrue();
        assertThat(validResult.get().asBoolean()).isTrue();

        val invalid = Val.ofJson("""
                    {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        val invalidResult = isCompliantWithExternalSchemas(invalid, specificSchema, externalsAsVal);
        assertThat(invalidResult.isBoolean()).isTrue();
        assertThat(invalidResult.get().asBoolean()).isFalse();
    }

    @Test
    @SneakyThrows
    void when_validatingWithExternalSchemaDefinitions_then_definitionsAreResolved() {
        val externals = MAPPER.createArrayNode();
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
        val externalsAsVal = Val.of(externals);
        val specificSchema = Val.ofJson("""
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

        val valid = Val.ofJson("""
                    {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        val validResult = isCompliantWithExternalSchemas(valid, specificSchema, externalsAsVal);
        assertThat(validResult.isBoolean()).isTrue();
        assertThat(validResult.get().asBoolean()).isTrue();

        val invalid = Val.ofJson("""
                    {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    }
                """);

        val invalidResult = isCompliantWithExternalSchemas(invalid, specificSchema, externalsAsVal);
        assertThat(invalidResult.isBoolean()).isTrue();
        assertThat(invalidResult.get().asBoolean()).isFalse();
    }

    static Stream<Arguments> provideIsCompliantScenarios() throws JsonProcessingException {
        return Stream.of(
                Arguments.of("compliant subject", Val.ofJson(COMPLIANT_JSON), Val.ofJson(VALID_SCHEMA), true, false),
                Arguments.of("non-compliant subject", Val.ofJson(NONCOMPLIANT_VALID_JSON), Val.ofJson(VALID_SCHEMA),
                        false, false),
                Arguments.of("undefined subject", Val.UNDEFINED, Val.ofJson(VALID_SCHEMA), false, false),
                Arguments.of("error subject", ErrorFactory.error("test"), Val.ofJson(VALID_SCHEMA), false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideIsCompliantScenarios")
    void when_usingIsCompliant_then_returnsExpectedResult(String scenario, Val subject, Val schema,
                                                          boolean expectedCompliant, boolean expectError) {
        val result = isCompliant(subject, schema);

        if (expectError) {
            assertThat(result.isError()).isTrue();
        } else {
            assertThat(result.isBoolean()).isTrue();
            assertThat(result.get().asBoolean()).isEqualTo(expectedCompliant);
        }
    }

    @Test
    void when_schemaExceptionOccurs_then_isCompliantReturnsFalse() throws JsonProcessingException {
        val validationSubject = spy(Val.NULL);
        when(validationSubject.get()).thenThrow(new JsonSchemaException("test"));

        val result = isCompliant(validationSubject, Val.ofJson(VALID_SCHEMA));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.get().asBoolean()).isFalse();
    }

    static Stream<Arguments> provideValidateScenarios() throws JsonProcessingException {
        return Stream.of(
                Arguments.of("compliant subject", Val.ofJson(COMPLIANT_JSON), Val.ofJson(VALID_SCHEMA), true, false,
                        false),
                Arguments.of("non-compliant subject", Val.ofJson(NONCOMPLIANT_VALID_JSON), Val.ofJson(VALID_SCHEMA),
                        false, true, false),
                Arguments.of("undefined subject", Val.UNDEFINED, Val.ofJson(VALID_SCHEMA), false, false, false),
                Arguments.of("error subject", ErrorFactory.error("eldritch_horror"), Val.ofJson(VALID_SCHEMA), false,
                        false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidateScenarios")
    void when_usingValidate_then_returnsExpectedStructuredResult(String scenario, Val subject, Val schema,
                                                                 boolean expectedValid, boolean expectErrors, boolean expectError) {
        val result = validate(subject, schema);

        if (expectError) {
            assertThat(result.isError()).isTrue();
        } else {
            assertThat(result.isDefined()).isTrue();
            assertThat(result.get().get("valid").asBoolean()).isEqualTo(expectedValid);

            if (expectErrors) {
                assertThat(result.get().get("errors")).isNotEmpty();
            } else {
                assertThat(result.get().get("errors")).isEmpty();
            }
        }
    }

    @Test
    void when_validateWithNonCompliant_then_errorStructureIsComplete() throws JsonProcessingException {
        val result = validate(Val.ofJson(NONCOMPLIANT_VALID_JSON), Val.ofJson(VALID_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();

        val firstError = result.get().get("errors").get(0);
        assertThat(firstError.has("path")).isTrue();
        assertThat(firstError.has("message")).isTrue();
        assertThat(firstError.has("type")).isTrue();
        assertThat(firstError.has("schemaPath")).isTrue();
        assertThat(firstError.get("path").isTextual()).isTrue();
        assertThat(firstError.get("message").isTextual()).isTrue();
        assertThat(firstError.get("type").isTextual()).isTrue();
        assertThat(firstError.get("schemaPath").isTextual()).isTrue();
    }

    @Test
    void when_schemaExceptionOccurs_then_validateReturnsValidFalse() throws JsonProcessingException {
        val validationSubject = spy(Val.NULL);
        when(validationSubject.get()).thenThrow(new JsonSchemaException("test"));

        val result = validate(validationSubject, Val.ofJson(VALID_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isEmpty();
    }

    @Test
    @SneakyThrows
    void when_validatingNecronomiconRitualWithValidData_then_returnsValid() {
        val validRitual = Val.ofJson("""
                {
                    "ritualName": "Summoning of Azathoth",
                    "requiredSacrifices": 13,
                    "forbiddenKnowledge": true
                }
                """);

        val result = validate(validRitual, Val.ofJson(NECRONOMICON_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("errors")).isEmpty();
    }

    @Test
    @SneakyThrows
    void when_validatingNecronomiconRitualWithMissingRequired_then_returnsInvalidWithError() {
        val invalidRitual = Val.ofJson("""
                {
                    "ritualName": "Incomplete Ritual",
                    "forbiddenKnowledge": false
                }
                """);

        val result = validate(invalidRitual, Val.ofJson(NECRONOMICON_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();

        val firstError = result.get().get("errors").get(0);
        assertThat(firstError.get("type").asText()).isEqualTo("required");
        assertThat(firstError.get("message").asText()).contains("requiredSacrifices");
    }

    @Test
    @SneakyThrows
    void when_validatingElderSignWithInvalidEnum_then_returnsInvalidWithError() {
        val invalidSign = Val.ofJson("""
                {
                    "geometry": "yellow_sign",
                    "powerLevel": 50
                }
                """);

        val result = validate(invalidSign, Val.ofJson(ELDER_SIGN_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();

        val firstError = result.get().get("errors").get(0);
        assertThat(firstError.get("type").asText()).isEqualTo("enum");
        assertThat(firstError.get("path").asText()).contains("geometry");
    }

    @Test
    @SneakyThrows
    void when_validatingWithExternalSchemas_then_validateReturnsStructuredResult() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                 {
                    "$id": "https://cthulhu.mythos/dreamer",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "sanity": { "type": "integer", "minimum": 0, "maximum": 100 }
                    },
                    "required": ["name", "sanity"]
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val cultSchema     = Val.ofJson("""
                    {
                    "$id": "https://cthulhu.mythos/cult",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "leader": { "$ref": "https://cthulhu.mythos/dreamer" },
                        "cultName": { "type": "string" }
                    },
                    "required": ["leader", "cultName"]
                }
                """);

        val validCult = Val.ofJson("""
                    {
                       "leader": { "name": "Abdul Alhazred", "sanity": 0 },
                       "cultName": "Order of the Silver Twilight"
                    }
                """);

        val result = validateWithExternalSchemas(validCult, cultSchema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("errors")).isEmpty();
    }

    @Test
    @SneakyThrows
    void when_validatingWithExternalSchemasAndInvalidData_then_validateReturnsStructuredErrors() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                 {
                    "$id": "https://cthulhu.mythos/dreamer",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "sanity": { "type": "integer", "minimum": 0, "maximum": 100 }
                    },
                    "required": ["name", "sanity"]
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val cultSchema     = Val.ofJson("""
                    {
                    "$id": "https://cthulhu.mythos/cult",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "leader": { "$ref": "https://cthulhu.mythos/dreamer" },
                        "cultName": { "type": "string" }
                    },
                    "required": ["leader", "cultName"]
                }
                """);

        val invalidCult = Val.ofJson("""
                    {
                       "leader": { "name": "Nyarlathotep", "sanity": 9999 },
                       "cultName": "The Black Pharaoh's Chosen"
                    }
                """);

        val result = validateWithExternalSchemas(invalidCult, cultSchema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();

        val firstError = result.get().get("errors").get(0);
        assertThat(firstError.get("path").asText()).contains("sanity");
        assertThat(firstError.get("message").asText()).contains("100");
    }

    static Stream<Arguments> provideValidationScenarios() {
        return Stream.of(Arguments.of("compliant document", COMPLIANT_JSON, VALID_SCHEMA, true),
                Arguments.of("non-compliant document", NONCOMPLIANT_VALID_JSON, VALID_SCHEMA, false),
                Arguments.of("valid elder sign", """
                        {
                            "geometry": "elder_sign",
                            "powerLevel": 75
                        }
                        """, ELDER_SIGN_SCHEMA, true), Arguments.of("invalid power level", """
                        {
                            "geometry": "pentagram",
                            "powerLevel": 150
                        }
                        """, ELDER_SIGN_SCHEMA, false), Arguments.of("complete necronomicon ritual", """
                        {
                            "ritualName": "The Ritual of the Key and the Gate",
                            "requiredSacrifices": 7,
                            "forbiddenKnowledge": true
                        }
                        """, NECRONOMICON_SCHEMA, true), Arguments.of("negative sacrifices", """
                        {
                            "ritualName": "Foolish Ritual",
                            "requiredSacrifices": -5,
                            "forbiddenKnowledge": false
                        }
                        """, NECRONOMICON_SCHEMA, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidationScenarios")
    @SneakyThrows
    void when_validatingVariousScenarios_then_resultsMatchExpectations(String scenario, String json, String schema,
                                                                       boolean expectedValid) {
        val result = validate(Val.ofJson(json), Val.ofJson(schema));

        assertThat(result.get().get("valid").asBoolean()).isEqualTo(expectedValid);

        if (expectedValid) {
            assertThat(result.get().get("errors")).isEmpty();
        } else {
            assertThat(result.get().get("errors")).isNotEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidationScenarios")
    @SneakyThrows
    void when_usingIsCompliantWithVariousScenarios_then_resultsMatchExpectations(String scenario, String json,
                                                                                 String schema, boolean expectedValid) {
        val result = isCompliant(Val.ofJson(json), Val.ofJson(schema));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.get().asBoolean()).isEqualTo(expectedValid);
    }

    @Test
    @SneakyThrows
    void when_externalSchemasIsNotArray_then_validationProceedsWithoutExternalSchemas() {
        val notAnArray     = Val.ofJson("\"not an array\"");
        val specificSchema = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" }
                    }
                }
                """);
        val valid          = Val.ofJson("""
                {
                    "name": "Cthulhu"
                }
                """);

        val result = validateWithExternalSchemas(valid, specificSchema, notAnArray);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    @Test
    @SneakyThrows
    void when_externalSchemaLacksDollarId_then_schemaIsIgnored() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                 {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties" : {
                        "x": { "type": "integer" }
                    }
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val specificSchema = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" }
                    }
                }
                """);
        val valid          = Val.ofJson("""
                {
                    "name": "Yog-Sothoth"
                }
                """);

        val result = validateWithExternalSchemas(valid, specificSchema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    @Test
    @SneakyThrows
    void when_multipleValidationErrors_then_allErrorsAreReturned() {
        val schema  = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" },
                        "age": { "type": "integer", "minimum": 0 },
                        "email": { "type": "string", "format": "email" }
                    },
                    "required": ["name", "age", "email"]
                }
                """);
        val invalid = Val.ofJson("""
                {
                    "name": 123,
                    "age": -5
                }
                """);

        val result = validate(invalid, schema);

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors").size()).isGreaterThan(1);
    }

    static Stream<Arguments> provideErrorPropagationScenarios() throws JsonProcessingException {
        return Stream.of(Arguments.of("validate with error", ErrorFactory.error("cosmic_horror"), false),
                Arguments.of("validate with undefined", Val.UNDEFINED, false),
                Arguments.of("isCompliant with external schemas and error", ErrorFactory.error("forbidden_knowledge"),
                        true),
                Arguments.of("validate with external schemas and error", ErrorFactory.error("ancient_evil"), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideErrorPropagationScenarios")
    @SneakyThrows
    void when_errorOrUndefinedSubject_then_handledAppropriately(String scenario, Val subject,
                                                                boolean useExternalSchemas) {
        val externals = Val.ofEmptyArray();
        val schema    = Val.ofJson(VALID_SCHEMA);

        val result = useExternalSchemas ? validateWithExternalSchemas(subject, schema, externals)
                : validate(subject, schema);

        if (subject.isError()) {
            assertThat(result.isError()).isTrue();
        } else {
            assertThat(result.isDefined()).isTrue();
            assertThat(result.get().get("valid").asBoolean()).isFalse();
            assertThat(result.get().get("errors")).isEmpty();
        }
    }

    @Test
    @SneakyThrows
    void when_isCompliantWithExternalSchemasReceivesError_then_errorPropagates() {
        val externals      = Val.ofEmptyArray();
        val specificSchema = Val.ofJson(VALID_SCHEMA);
        val errorSubject   = ErrorFactory.error("forbidden_knowledge");

        val result = isCompliantWithExternalSchemas(errorSubject, specificSchema, externals);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).isEqualTo("forbidden_knowledge");
    }

    @Test
    @SneakyThrows
    void when_validateReturnsValid_then_errorArrayIsEmpty() {
        val result = validate(Val.ofJson(COMPLIANT_JSON), Val.ofJson(VALID_SCHEMA));

        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("errors").isArray()).isTrue();
        assertThat(result.get().get("errors")).isEmpty();
    }

    @Test
    @SneakyThrows
    void when_validateWithValidExternalSchema_then_structuredResultReturned() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                {
                    "$id": "https://shoggoth.deep/entity",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" },
                        "tentacles": { "type": "integer", "minimum": 0 }
                    },
                    "required": ["name"]
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val schema         = Val.ofJson("""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "creature": { "$ref": "https://shoggoth.deep/entity" }
                    }
                }
                """);
        val validData      = Val.ofJson("""
                {
                    "creature": { "name": "Shoggoth", "tentacles": 42 }
                }
                """);

        val result = validateWithExternalSchemas(validData, schema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("errors")).isEmpty();
    }

    @Test
    @SneakyThrows
    void when_validateWithInvalidExternalSchemaReference_then_structuredErrorsReturned() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                {
                    "$id": "https://shoggoth.deep/entity",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" },
                        "tentacles": { "type": "integer", "minimum": 0 }
                    },
                    "required": ["name"]
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val schema         = Val.ofJson("""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "properties": {
                        "creature": { "$ref": "https://shoggoth.deep/entity" }
                    }
                }
                """);
        val invalidData    = Val.ofJson("""
                {
                    "creature": { "tentacles": -10 }
                }
                """);

        val result = validateWithExternalSchemas(invalidData, schema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();
    }

    @Test
    @SneakyThrows
    void when_externalSchemasArrayContainsMixedValidAndInvalid_then_validationProceeds() {
        val externals = MAPPER.createArrayNode();
        externals.add(MAPPER.readValue("""
                {
                    "$id": "https://valid.schema/test",
                    "type": "object"
                }
                """, JsonNode.class));
        externals.add(MAPPER.createObjectNode());
        externals.add(MAPPER.readValue("""
                {
                    "type": "object"
                }
                """, JsonNode.class));
        val externalsAsVal = Val.of(externals);
        val schema         = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "field": { "type": "string" }
                    }
                }
                """);
        val validData      = Val.ofJson("""
                {
                    "field": "Hastur"
                }
                """);

        val result = validateWithExternalSchemas(validData, schema, externalsAsVal);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    @Test
    @SneakyThrows
    void when_validatingComplexNestedSchema_then_pathPointsToExactLocation() {
        val schema  = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "outer": {
                            "type": "object",
                            "properties": {
                                "inner": {
                                    "type": "object",
                                    "properties": {
                                        "deep": { "type": "integer" }
                                    }
                                }
                            }
                        }
                    }
                }
                """);
        val invalid = Val.ofJson("""
                {
                    "outer": {
                        "inner": {
                            "deep": "not_an_integer"
                        }
                    }
                }
                """);

        val result = validate(invalid, schema);

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();

        val firstError = result.get().get("errors").get(0);
        assertThat(firstError.get("path").asText()).contains("deep");
    }

    @Test
    @SneakyThrows
    void when_schemaHasMultipleConstraintViolations_then_allReportedInErrors() {
        val schema  = Val.ofJson("""
                {
                    "type": "object",
                    "properties": {
                        "count": { "type": "integer", "minimum": 10, "maximum": 100 }
                    }
                }
                """);
        val invalid = Val.ofJson("""
                {
                    "count": 5
                }
                """);

        val result = validate(invalid, schema);

        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors")).isNotEmpty();
    }

    @Test
    @SneakyThrows
    void when_emptyExternalSchemasProvided_then_cachedFactoryIsUsed() {
        val emptyExternals = Val.ofEmptyArray();
        val schema         = Val.ofJson(VALID_SCHEMA);
        val validData      = Val.ofJson(COMPLIANT_JSON);

        val result = validateWithExternalSchemas(validData, schema, emptyExternals);

        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("errors")).isEmpty();
    }

}