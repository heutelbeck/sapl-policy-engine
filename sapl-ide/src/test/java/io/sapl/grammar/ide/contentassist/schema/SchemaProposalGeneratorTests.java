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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.ide.contentassist.SchemaProposalGenerator;
import lombok.SneakyThrows;

class SchemaProposalGeneratorTests {

    private static final String LOCATION_SCHEMA              = """
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
            """;
    private static final String DETAILED_PERSON_SCHEMA       = """
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
            """;
    private static final String SIMPLE_PERSON_SCHEMA         = """
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
            """;
    private static final String COORDINATES_SCHEMA_WITH_DEFS = """
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
            """;
    private static final String COORDINATES_SCHEMA           = """
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
            """;

    private static Stream<Arguments> provideTestArguments() {
        // @formatter:off
        return Stream.of(
                Arguments.of("empty schema", List.of(), "{}", new String[0] ),
                Arguments.of("non object schema", List.of(), "123", new String[0] ),
                Arguments.of("simple schema", List.of(), DETAILED_PERSON_SCHEMA, new String[] {".firstName", ".lastName", ".age"}),
                Arguments.of("simple array", List.of(),
                        """
                        {
                             "type": "array",
                             "items": [
                               { "type": "string" },
                               { "enum": ["Street", "Avenue"] }
                             ]
                        }
                        """, new String[] {"[]"} ),
                Arguments.of("resolves internal defs", List.of(), """
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
                    """,
                    new String[] { ".A", ".A.x", ".A.y", ".A.z",
                                   ".B", ".B.x", ".B.y", ".B.z",
                                   ".C", ".C.x", ".C.y", ".C.z"}),
                Arguments.of(
                        "absoluteToExternalSchemaAnchorButTheAncorIsNotMathing",
                        List.of("""
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
                            """),
                        """
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
                       """,
                       new String[] {".A", ".B", ".C"}),
                Arguments.of("absoluteToExternalSchemaAnchor",
                        List.of("""
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
                            """),
                        """
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
                       """,
                       new String[] { ".A", ".A.x", ".A.y", ".A.z",
                                      ".B", ".B.x", ".B.y", ".B.z",
                                      ".C", ".C.x", ".C.y", ".C.z"}),
                Arguments.of("absoluteToExternalSchemaAnchorInArray",
                        List.of("""
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
                            """),
                        """
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
                       """,
                       new String[] { ".A", ".A.x", ".A.y", ".A.z",
                                      ".B", ".B.x", ".B.y", ".B.z",
                                      ".C", ".C.x", ".C.y", ".C.z"}),
                Arguments.of("absoluteToExternalSchemaAnchorInArrayWhichDoesNotExist",
                        List.of("""
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
                            """),
                        """
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
                       """,
                       new String[] {".A", ".B", ".C"}),
                Arguments.of("absoluteToExternalSchemaAnchorDeeperInHierarcy",
                        List.of("""
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
                            """),
                        """
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
                       """,
                       new String[] {".A", ".B", ".C"}),
                Arguments.of("absoluteToExternalSchema",
                        List.of(COORDINATES_SCHEMA),
                        """
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
                       """,
                       new String[] { ".A", ".A.x", ".A.y", ".A.z",
                                      ".B", ".B.x", ".B.y", ".B.z",
                                      ".C", ".C.x", ".C.y", ".C.z"}),
                Arguments.of("overloadingTypeWithArray",
                        List.of(COORDINATES_SCHEMA, SIMPLE_PERSON_SCHEMA),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": [
                             { "$ref": "https://example.com/coordinates" },
                             { "$ref": "https://example.com/person" }
                           ]
                       }
                       """,
                       new String[] {".x", ".y", ".z", ".name", ".age"}),
                Arguments.of("overloadingTypeWithAnyOf",
                        List.of(COORDINATES_SCHEMA, SIMPLE_PERSON_SCHEMA),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "anyOf": [
                             { "$ref": "https://example.com/coordinates" },
                             { "$ref": "https://example.com/person" }
                           ]
                       }
                       """,
                       new String[] {".x", ".y", ".z", ".name", ".age"}),
                Arguments.of("overloadingTypeWithAllOf",
                        List.of(COORDINATES_SCHEMA, SIMPLE_PERSON_SCHEMA),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "allOf": [
                             { "$ref": "https://example.com/coordinates" },
                             { "$ref": "https://example.com/person" }
                           ]
                       }
                       """,
                        new String[] {".x", ".y", ".z", ".name", ".age"}),
                Arguments.of("overloadingTypeWithOneOf",
                        List.of(COORDINATES_SCHEMA, SIMPLE_PERSON_SCHEMA),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "oneOf": [
                             { "$ref": "https://example.com/coordinates" },
                             { "$ref": "https://example.com/person" }
                           ]
                       }
                       """,
                        new String[] {".x", ".y", ".z", ".name", ".age"}),
                Arguments.of("absolutePointerToExternalSchema",
                        List.of(COORDINATES_SCHEMA_WITH_DEFS),
                        """
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
                       """,
                       new String[] { ".A", ".A.x", ".A.y", ".A.z",
                                      ".B", ".B.x", ".B.y", ".B.z",
                                      ".C", ".C.x", ".C.y", ".C.z"}),
                Arguments.of("absolutePointerToExternalSchemaIncorrectPath",
                        List.of(COORDINATES_SCHEMA_WITH_DEFS),
                        """
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
                       """,
                       new String[] {".A", ".B", ".C"}),
                Arguments.of("nonObjectProperties",
                        List.of(),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": "object",
                           "properties": 123
                       }
                       """,
                       new String[] {}),
                Arguments.of("nonTextType",
                        List.of(),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": 123
                       }
                       """,
                       new String[] {}),
                Arguments.of("objectNoProperties",
                        List.of(),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": "object"
                       }
                       """,
                       new String[] {}),
                Arguments.of("arrayNoItems_then_proposalsHasBrackets",
                        List.of(),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": "array"
                       }
                       """,
                       new String[] {"[]"}),
                Arguments.of("array",
                        List.of(COORDINATES_SCHEMA_WITH_DEFS,DETAILED_PERSON_SCHEMA,LOCATION_SCHEMA),
                        """
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
                       """,
                       new String[] {
                               "[]", "[].firstName", "[].lastName", "[].age",
                               "[].long", "[].lat", "[].x", "[].y", "[].z"}),
                Arguments.of("arrayNonObjectItems_then_proposalsOnlyBracket",
                        List.of(),
                        """
                        {
                           "$id": "https://example.com/triangle.schema.json",
                           "$schema": "https://json-schema.org/draft/2020-12/schema",
                           "title": "Triangle",
                           "type": "array",
                           "items": 123
                       }
                       """,
                       new String[] {"[]"}),
                Arguments.of("when_schemaContainsWhitespaces_then_proposalsAreEscaped",
                        List.of(),
                        """
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
                       """,
                       new String[] {
                               ".A", ".A.'x coordinate'", ".A.'y coordinate'", ".A.'z coordinate'",
                               ".B", ".B.'x coordinate'", ".B.'y coordinate'", ".B.'z coordinate'",
                               ".C", ".C.'x coordinate'", ".C.'y coordinate'", ".C.'z coordinate'"}),
                Arguments.of("simpleRecusiveSchema",
                        List.of(),
                        """
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
                       """, new String[] {
                           ".firstName",
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
                           ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent"}),
                Arguments.of("simpleRecusiveSchemaHashtag",
                        List.of(),
                        """
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
                       """, new String[] {
                           ".firstName",
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
                           ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent"}),
                Arguments.of("simpleRecusiveSchemaAbsoluteReference",
                        List.of(),
                        """
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
                       """,
                        new String[] {
                                ".firstName",
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
                                ".parent.parent.parent.parent.parent.parent.parent.parent.parent.parent"}),
                Arguments.of("badRef",
                        List.of(),
                        """
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
                       """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("schemaIdBadType",
                        List.of(),
                        """
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
                       """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("schemaNoId",
                        List.of(),
                        """
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
                       """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("schemaBlankId",
                        List.of(),
                        """
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
                    """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("unknownAbsoluteExternalRef",
                        List.of(),
                        """
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
                       """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("malformedAbsoluteExternalRef",
                        List.of(),
                        """
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
                       """,
                       new String[] {".firstName", ".age", ".parent"}),
                Arguments.of("empty schema", List.of(), "{}", new String[0] )
                // @formatter:on
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void when_givenSchemaAndVariables_then_GeneratorReturnsExpectedProposals(String test, List<String> variables,
            String schema, String[] expectedProposals) {
        assertThat(test).isNotEmpty();
        var schemaJson   = Val.ofJson(schema).get();
        var variablesMap = new HashMap<String, Val>();
        var schemasArray = JsonNodeFactory.instance.arrayNode();
        for (var variable : variables) {
            schemasArray.add(Val.ofJson(variable).get());
        }
        variablesMap.put("SCHEMAS", Val.of(schemasArray));
        var actualProposals = SchemaProposalGenerator.getCodeTemplates("", schemaJson, variablesMap);
        assertThat(actualProposals).containsExactlyInAnyOrder(expectedProposals);
    }

    @Test
    void when_nullSchema_then_noProposals() {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", (JsonNode) null, Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_undefinedSchema_then_proposalsEmpty() {
        var proposals = SchemaProposalGenerator.getCodeTemplates("", Val.error(""), Map.of());
        assertThat(proposals).isEmpty();
    }

}
