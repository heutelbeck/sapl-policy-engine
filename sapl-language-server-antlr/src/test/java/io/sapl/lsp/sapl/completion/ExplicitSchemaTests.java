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
package io.sapl.lsp.sapl.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Tests for explicit schema declarations on value definitions.
 * Grammar syntax: var name = expression schema { ... }
 * The schema declaration comes AFTER the assignment.
 */
class ExplicitSchemaTests {

    @Test
    void whenExplicitSchemaOnVar_thenSchemaExpansionsOffered() {
        var document     = """
                policy "test"
                permit
                where
                  var config = {} schema { "type": "object", "properties": { "timeout": {}, "retries": {} } };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("config", "config.timeout", "config.retries");
    }

    @Test
    void whenExplicitSchemaWithNestedProperties_thenNestedExpansionsOffered() {
        var document     = """
                policy "test"
                permit
                where
                  var user = {} schema {
                    "type": "object",
                    "properties": {
                      "name": {
                        "type": "object",
                        "properties": {
                          "first": {},
                          "last": {}
                        }
                      },
                      "age": {}
                    }
                  };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("user", "user.name", "user.name.first", "user.name.last", "user.age");
    }

    @Test
    void whenEmptyExplicitSchema_thenOnlyBaseVariableOffered() {
        var document     = """
                policy "test"
                permit
                where
                  var foo = 1 schema {};
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("foo");
        var fooExpansions = proposals.stream().filter(p -> p.startsWith("foo.")).toList();
        assertThat(fooExpansions).isEmpty();
    }

    @Test
    void whenExplicitSchemaWithArrayType_thenArrayNotationOffered() {
        var document     = """
                policy "test"
                permit
                where
                  var items = [] schema {
                    "type": "array",
                    "items": { "type": "object", "properties": { "id": {}, "name": {} } }
                  };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("items", "items[]", "items[].id", "items[].name");
    }

    @Test
    void whenExplicitSchemaWithInternalRef_thenRefResolved() {
        var document     = """
                policy "test"
                permit
                where
                  var data = {} schema {
                    "type": "object",
                    "properties": {
                      "person": { "$ref": "#/$defs/person" }
                    },
                    "$defs": {
                      "person": {
                        "type": "object",
                        "properties": { "name": {}, "email": {} }
                      }
                    }
                  };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("data", "data.person", "data.person.name", "data.person.email");
    }

    @Test
    void whenExplicitSchemaReferencesEnvVariable_thenSchemaResolvedFromEnv() throws Exception {
        var schemaJson   = """
                {
                  "type": "object",
                  "properties": {
                    "host": {},
                    "port": {}
                  }
                }
                """;
        var document     = """
                policy "test"
                permit
                where
                  var config = {} schema serverConfigSchema;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var mapper       = new ObjectMapper();
        var schemaNode   = mapper.readTree(schemaJson);
        var variables    = new HashMap<String, Value>();
        variables.put("serverConfigSchema", ValueJsonMarshaller.fromJsonNode(schemaNode));
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("config", "config.host", "config.port");
    }

    @Test
    void whenSchemaNotInEnvironment_thenOnlyBaseVariableOffered() {
        var document     = """
                policy "test"
                permit
                where
                  var config = {} schema nonExistentSchema;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("config");
    }

    @Test
    void whenVarAssignedFromSubjectWithExplicitSchema_thenBothSchemasApply() {
        var document     = """
                subject schema { "type": "object", "properties": { "id": {} } }
                policy "test"
                permit
                where
                  var user = subject schema { "type": "object", "properties": { "role": {} } };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Should have expansions from both subject schema (via assignment) and explicit
        // schema
        assertThat(proposals).contains("user", "user.id", "user.role");
    }

    static Stream<Arguments> schemaPropertyTypeTestCases() {
        return Stream.of(
                arguments("string", "{ \"type\": \"object\", \"properties\": { \"name\": { \"type\": \"string\" } } }",
                        "config.name"),
                arguments("number", "{ \"type\": \"object\", \"properties\": { \"count\": { \"type\": \"number\" } } }",
                        "config.count"),
                arguments("boolean",
                        "{ \"type\": \"object\", \"properties\": { \"active\": { \"type\": \"boolean\" } } }",
                        "config.active"),
                arguments("object",
                        "{ \"type\": \"object\", \"properties\": { \"nested\": { \"type\": \"object\", \"properties\": { \"inner\": {} } } } }",
                        "config.nested.inner"),
                arguments("array",
                        "{ \"type\": \"object\", \"properties\": { \"items\": { \"type\": \"array\", \"items\": { \"type\": \"object\", \"properties\": { \"id\": {} } } } } }",
                        "config.items[].id"));
    }

    @ParameterizedTest(name = "whenSchemaHas{0}Property_thenExpansionOffered")
    @MethodSource("schemaPropertyTypeTestCases")
    void whenSchemaHasPropertyOfType_thenExpansionOffered(String typeName, String schema, String expectedExpansion) {
        var document     = "policy \"test\" permit where var config = {} schema " + schema + ";";
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains(expectedExpansion);
    }

    @Test
    void whenRecursiveSchema_thenLimitedDepthExpansions() {
        var document     = """
                policy "test"
                permit
                where
                  var node = {} schema {
                    "type": "object",
                    "properties": {
                      "value": {},
                      "children": {
                        "type": "array",
                        "items": { "$ref": "#" }
                      }
                    }
                  };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Should have recursive expansions up to a reasonable depth
        assertThat(proposals).contains("node", "node.value", "node.children", "node.children[]",
                "node.children[].value", "node.children[].children");
    }

    private static SaplContext parse(String content) {
        var charStream  = CharStreams.fromString(content);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        return parser.sapl();
    }

}
