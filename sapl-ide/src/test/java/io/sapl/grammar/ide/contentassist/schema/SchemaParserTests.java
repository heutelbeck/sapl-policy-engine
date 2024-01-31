package io.sapl.grammar.ide.contentassist.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchemaParserTests {

    @Test
    void when_emptySchema_then_proposalsEmpty() throws JsonProcessingException {
        var proposals = SchemaParser.generateProposals(Val.ofJson("{}"), Map.of());
        assertThat(proposals).isEmpty();
    }

    @Test
    void when_undefinedSchema_then_proposalsEmpty() throws JsonProcessingException {
        var proposals = SchemaParser.generateProposals(Val.error(""), Map.of());
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".firstName", ".lastName", ".age");
    }

    @Test
    void when_defs_then_proposalsComplete() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z", ".C", ".C.x", ".C.y",
                ".C.z");
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
                    "$id": "https://example.com/person.schema.json",
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
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z", ".C", ".C.x", ".C.y",
                ".C.z");
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
                    "$id": "https://example.com/person.schema.json",
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
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z", ".C", ".C.x", ".C.y",
                ".C.z");
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
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get(), "y", person.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".x", ".y", ".z", ".name", ".age");
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
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "anyOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get(), "y", person.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".x", ".y", ".z", ".name", ".age");
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
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "allOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get(), "y", person.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".x", ".y", ".z", ".name", ".age");
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
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "oneOf": [
                      { "$ref": "https://example.com/coordinates" },
                      { "$ref": "https://example.com/person" }
                    ]
                }
                """);
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get(), "y", person.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".x", ".y", ".z", ".name", ".age");
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
                    "$id": "https://example.com/person.schema.json",
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
        var proposals   = SchemaParser.generateProposals(schema, Map.of("x", coordinates.get()));
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".A", ".A.x", ".A.y", ".A.z", ".B", ".B.x", ".B.y", ".B.z", ".C", ".C.x", ".C.y",
                ".C.z");
    }

    @Test
    void when_undefinedDefsReference_then_proposalsDoNotResolveDeeper() throws JsonProcessingException {
        Val schema    = Val.ofJson("""
                 {
                    "$id": "https://example.com/person.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "#/$defs/coordinatesBAD" },
                        "B": { "$ref": "#/$defs/coordinatesBAD" },
                        "C": { "$ref": "#/$defs/coordinatesBAD" }
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        assertThat(proposals).contains(".A", ".B", ".C");
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        // @formatter:off
        assertThat(proposals).contains(  ".firstName",
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        assertThat(proposals).contains(".firstName", ".age", ".parent");
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        // @formatter:off
        assertThat(proposals).contains(  ".firstName",
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
        var proposals = SchemaParser.generateProposals(schema, Map.of());
        log.error("Proposals: {}", proposals);
        // @formatter:off
        assertThat(proposals).contains(  ".firstName",
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
