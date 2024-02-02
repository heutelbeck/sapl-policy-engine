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
package io.sapl.grammar.ide.contentassist.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.ide.contentassist.SchemaProposalGenerator;

class SchemaProposalGeneratorTests {

    @Test
    void when_emptySchema_then_proposalsEmpty() throws JsonProcessingException {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", Val.ofJson("{}"), Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_nonObjectSchema_then_proposalsEmpty() throws JsonProcessingException {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", Val.ofJson("123"), Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_undefinedSchema_then_proposalsEmpty() throws JsonProcessingException {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", Val.error(""), Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_simpleSchema_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "lastName": {
                            "type": "string",
                            "description": "The person's last name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".lastName", ".age");
    }

    @Test
    void when_simpleArray_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                {
                     "type": "array",
                     "items": [
                       { "type": "string" },
                       { "enum": ["Street", "Avenue"] }
                     ]
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("foo", schema.get(), Map.of());
        assertThat(proposals).containsExactlyInAnyOrder("foo[]");
    }

    @Test
    void when_defs_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "#/$defs/coordinates" },
                        "B": { "$ref": "#/$defs/coordinates" },
                        "C": { "$ref": "#/$defs/coordinates" }
                    },
                    "$defs" : {
                        "coordinates" : {
                            "type": "object",
                            "properties" : {
                                "x": { "type": "integer" },
                                "y": { "type": "integer" },
                                "z": { "type": "integer" }
                            }
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z",
                ".C", ".C.x", ".C.y", ".C.z");
    }

    @Test
    void when_absoluteToExternalSchemaAnchorButTheAncorIsNotMathing_then_proposalsDoNotDescend()
            throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "$anchor": "coordis-other",
                    "title": "Coordinates",
                    "type": "object",
                    "properties" : {
                        "x": { "type": "integer" },
                        "y": { "type": "integer" },
                        "z": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#coordis" },
                        "B": { "$ref": "https://example.com/coordinates#coordis" },
                        "C": { "$ref": "https://example.com/coordinates#coordis" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".B", ".C");
    }

    @Test
    void when_absoluteToExternalSchemaAnchor_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "$anchor": "coordis",
                    "title": "Coordinates",
                    "type": "object",
                    "properties" : {
                        "x": { "type": "integer" },
                        "y": { "type": "integer" },
                        "z": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#coordis" },
                        "B": { "$ref": "https://example.com/coordinates#coordis" },
                        "C": { "$ref": "https://example.com/coordinates#coordis" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z",
                ".C", ".C.x", ".C.y", ".C.z");
    }

    @Test
    void when_absoluteToExternalSchemaAnchorInArray_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "type": [
                                {"type":"string"},
                                {
                                    "type":"object",
                                    "$anchor": "coordis",
                                    "properties" : {
                                        "x": { "type": "integer" },
                                        "y": { "type": "integer" },
                                        "z": { "type": "integer" }
                                    }
                                  }
                            ]
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#coordis" },
                        "B": { "$ref": "https://example.com/coordinates#coordis" },
                        "C": { "$ref": "https://example.com/coordinates#coordis" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z",
                ".C", ".C.x", ".C.y", ".C.z");
    }

    @Test
    void when_absoluteToExternalSchemaAnchorInArrayWhichDoesNotExist_then_proposalsDoesNotDescends()
            throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "type": [
                                {"type":"string"},
                                {
                                    "type":"object",
                                    "properties" : {
                                        "x": { "type": "integer" },
                                        "y": { "type": "integer" },
                                        "z": { "type": "integer" }
                                    }
                                  }
                            ]
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#coordis" },
                        "B": { "$ref": "https://example.com/coordinates#coordis" },
                        "C": { "$ref": "https://example.com/coordinates#coordis" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".B", ".C");
    }

    @Test
    void when_absoluteToExternalSchemaAnchorDeeperInHierarcy_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
                 {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "type": "object",
                    "properties" : {
                        "x": { "$anchor": "coordis", "type": "integer" },
                        "y": { "type": "integer" },
                        "z": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#coordis" },
                        "B": { "$ref": "https://example.com/coordinates#coordis" },
                        "C": { "$ref": "https://example.com/coordinates#coordis" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".B", ".C");
    }

    @Test
    void when_absoluteToExternalSchema_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val schema      = Val.ofJson("""
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
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z",
                ".C", ".C.x", ".C.y", ".C.z");
    }

    @Test
    void when_overloadingTypeWithArray_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val person      = Val.ofJson("""
                 {
                    "$id": "https://example.com/person",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "age": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema,
                Map.of("x", coordinates.get(), "y", person.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".x", ".y", ".z", ".name", ".age");
    }

    @Test
    void when_overloadingTypeWithAnyOf_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val person      = Val.ofJson("""
                 {
                    "$id": "https://example.com/person",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "age": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "anyOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema,
                Map.of("x", coordinates.get(), "y", person.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".x", ".y", ".z", ".name", ".age");
    }

    @Test
    void when_overloadingTypeWithAllOf_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val person      = Val.ofJson("""
                 {
                    "$id": "https://example.com/person",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "age": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "allOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema,
                Map.of("x", coordinates.get(), "y", person.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".x", ".y", ".z", ".name", ".age");
    }

    @Test
    void when_overloadingTypeWithOneOf_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val person      = Val.ofJson("""
                 {
                    "$id": "https://example.com/person",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties" : {
                        "name": { "type": "string" },
                        "age": { "type": "integer" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "oneOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema,
                Map.of("x", coordinates.get(), "y", person.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".x", ".y", ".z", ".name", ".age");
    }

    @Test
    void when_absolutePointerToExternalSchema_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val schema      = Val.ofJson("""
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
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z",
                ".C", ".C.x", ".C.y", ".C.z");
    }

    @Test
    void when_absolutePointerToExternalSchemaIncorrectPath_then_proposalsNoDescend() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates#/$defs/xoord" },
                        "B": { "$ref": "https://example.com/coordinates#/$defs/xoord" },
                        "C": { "$ref": "https://example.com/coordinates#/$defs/xoord" }
                    }
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of("x", coordinates.get()));
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".B", ".C");
    }

    @Test
    void when_nonObjectProperties_then_proposalsEmpty() throws JsonProcessingException {

        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": 123
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_nonTextType_then_proposalsEmpty() throws JsonProcessingException {

        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": 123
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_objectNoProperties_then_proposalsEmpty() throws JsonProcessingException {

        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object"
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_arrayNoItems_then_proposalsHasBrackets() throws JsonProcessingException {

        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "array"
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder("[]");
    }

    @Test
    void when_array_then_proposalsComplete() throws JsonProcessingException {
        Val coordinates = Val.ofJson("""
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
                """);
        Val person      = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "lastName": {
                            "type": "string",
                            "description": "The person's last name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        }
                    }
                }
                """);
        Val location    = Val.ofJson("""
                 {
                    "$id": "https://example.com/location.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "location",
                    "type": "object",
                    "properties": {
                        "long": { "type": "number" },
                        "lat": { "type": "number" }
                    }
                }
                """);
        Val schema      = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "array",
                    "items": { "$ref": "https://example.com/coordinates#/$defs/coord" },
                    "prefixItems" : [
                        { "$ref": "https://example.com/person.schema.json" },
                        { "$ref": "https://example.com/location.schema.json" }
                    ]
                }
                """);
        var proposals   = SchemaProposalGenerator.getCodeTemplates("", schema,
                Map.of("x", coordinates.get(), "y", person.get(), "z", location.get()));
        assertThat(proposals).containsExactlyInAnyOrder("[]", "[].firstName", "[].lastName", "[].age", "[].long",
                "[].lat", "[].x", "[].y", "[].z");
    }

    @Test
    void when_arrayNonObjectItems_then_proposalsOnlyBracket() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "array",
                    "items": 123
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder("[]");
    }

    @Test
    void when_schemaContainsWhitespaces_then_proposalsAreEscaped() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "#/$defs/coordinates" },
                        "B": { "$ref": "#/$defs/coordinates" },
                        "C": { "$ref": "#/$defs/coordinates" }
                    },
                    "$defs" : {
                        "coordinates" : {
                            "type": "object",
                            "properties" : {
                                "x coordinate": { "type": "integer" },
                                "y coordinate": { "type": "integer" },
                                "z coordinate": { "type": "integer" }
                            }
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".A", ".A.'x coordinate'", ".A.'y coordinate'",
                ".A.'z coordinate'", ".B", ".B.'x coordinate'", ".B.'y coordinate'", ".B.'z coordinate'", ".C",
                ".C.'x coordinate'", ".C.'y coordinate'", ".C.'z coordinate'");
    }

    @Test
    void when_simpleRecusiveSchema_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : "/"
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        // @formatter:off
        assertThat(proposals).containsExactlyInAnyOrder(  ".firstName",
                                         ".age",
                                         ".parent",
                                         ".parent.firstName",
                                         ".parent.age",
                                         ".parent.parent",
                                         ".parent.parent.firstName",
                                         ".parent.parent.age",
                                         ".parent.parent.parent",
                                         ".parent.parent.parent.firstName",
                                         ".parent.parent.parent.age",
                                         ".parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent");
        // @formatter:on
    }

    @Test
    void when_badRef_then_proposalsStop() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : 123
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
    }

    @Test
    void when_badId_then_noChange() throws JsonProcessingException {
        Val schemaIdBadType = Val.ofJson("""
                 {
                    "$id": 123,
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "type" : "string"
                        }
                    }
                }
                """);
        Val schemaNoId      = Val.ofJson("""
                 {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "type" : "string"
                        }
                    }
                }
                """);
        Val schemaBlankId   = Val.ofJson("""
                    {
                    "$id" : "",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "type" : "string"
                        }
                    }
                }
                """);
        var proposals       = SchemaProposalGenerator.getCodeTemplates("", schemaIdBadType, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
        proposals = SchemaProposalGenerator.getCodeTemplates("", schemaNoId, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
        proposals = SchemaProposalGenerator.getCodeTemplates("", schemaBlankId, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
    }

    @Test
    void when_unknownAbsoluteExternalRef_then_proposalsStop() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : "https://unknown.org"
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
    }

    @Test
    void when_nullSchema_then_noProposals() throws JsonProcessingException {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", (JsonNode) null, Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_malformedAbsoluteExternalRef_then_proposalsStop() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : ":::-3https://unknown.org"
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        assertThat(proposals).containsExactlyInAnyOrder(".firstName", ".age", ".parent");
    }

    @Test
    void when_badRefsimpleRecusiveSchemaHashtag_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : "#"
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        // @formatter:off
        assertThat(proposals).containsExactlyInAnyOrder(  ".firstName",
                                         ".age",
                                         ".parent",
                                         ".parent.firstName",
                                         ".parent.age",
                                         ".parent.parent",
                                         ".parent.parent.firstName",
                                         ".parent.parent.age",
                                         ".parent.parent.parent",
                                         ".parent.parent.parent.firstName",
                                         ".parent.parent.parent.age",
                                         ".parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent");
        // @formatter:on
    }

    @Test
    void when_simpleRecusiveSchemaAbsoluteReference_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Person",
                    "type": "object",
                    "properties": {
                        "firstName": {
                            "type": "string",
                            "description": "The person's first name."
                        },
                        "age": {
                            "description": "Age in years which must be equal to or greater than zero.",
                            "type": "integer",
                            "minimum": 0
                        },
                        "parent": {
                            "$ref" : "https://example.com/person.schema.json"
                        }
                    }
                }
                """);
        var proposals = SchemaProposalGenerator.getCodeTemplates("", schema, Map.of());
        // @formatter:off
        assertThat(proposals).containsExactlyInAnyOrder(  ".firstName",
                                         ".age",
                                         ".parent",
                                         ".parent.firstName",
                                         ".parent.age",
                                         ".parent.parent",
                                         ".parent.parent.firstName",
                                         ".parent.parent.age",
                                         ".parent.parent.parent",
                                         ".parent.parent.parent.firstName",
                                         ".parent.parent.parent.age",
                                         ".parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.firstName",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.age",
                                         ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent");
        // @formatter:on
    }
}
