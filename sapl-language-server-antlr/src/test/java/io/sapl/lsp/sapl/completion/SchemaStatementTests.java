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
 * Tests for schema statements on authorization subscription elements.
 * These tests verify that schema statements at document level properly
 * provide schema expansions for subject, action, resource, environment.
 */
class SchemaStatementTests {

    static Stream<Arguments> subscriptionElementTestCases() {
        return Stream.of(arguments("subject", "userId"), arguments("action", "verb"), arguments("resource", "path"),
                arguments("environment", "timestamp"));
    }

    @ParameterizedTest(name = "when{0}SchemaStatement_thenExpansionsOffered")
    @MethodSource("subscriptionElementTestCases")
    void whenSubscriptionElementHasSchema_thenExpansionsOffered(String element, String property) {
        var document     = element + " schema { \"type\": \"object\", \"properties\": { \"" + property + "\": {} } }\n"
                + "policy \"test\" permit where " + element;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains(element, element + "." + property);
    }

    @Test
    void whenAllSubscriptionElementsHaveSchemas_thenAllExpansionsOffered() {
        var document     = """
                subject schema { "type": "object", "properties": { "userId": {} } }
                action schema { "type": "object", "properties": { "verb": {} } }
                resource schema { "type": "object", "properties": { "path": {} } }
                environment schema { "type": "object", "properties": { "time": {} } }
                policy "test"
                permit
                where
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.userId", "action", "action.verb", "resource",
                "resource.path", "environment", "environment.time");
    }

    @Test
    void whenSchemaStatementHasNestedProperties_thenNestedExpansionsOffered() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "firstName": {},
                        "lastName": {},
                        "email": {}
                      }
                    }
                  }
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.profile", "subject.profile.firstName",
                "subject.profile.lastName", "subject.profile.email");
    }

    @Test
    void whenSchemaStatementReferencesEnvVariable_thenSchemaResolved() throws Exception {
        var schemaJson   = """
                {
                  "type": "object",
                  "properties": {
                    "name": {},
                    "role": {}
                  }
                }
                """;
        var document     = """
                subject schema personSchema
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var mapper       = new ObjectMapper();
        var schemaNode   = mapper.readTree(schemaJson);
        var variables    = new HashMap<String, Value>();
        variables.put("personSchema", ValueJsonMarshaller.fromJsonNode(schemaNode));
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.name", "subject.role");
    }

    @Test
    void whenSchemaStatementHasArrayProperty_thenArrayNotationOffered() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "roles": {
                      "type": "array",
                      "items": { "type": "string" }
                    }
                  }
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.roles", "subject.roles[]");
    }

    @Test
    void whenSchemaStatementHasInternalRef_thenRefResolved() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "address": { "$ref": "#/$defs/address" }
                  },
                  "$defs": {
                    "address": {
                      "type": "object",
                      "properties": {
                        "street": {},
                        "city": {},
                        "zip": {}
                      }
                    }
                  }
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.address", "subject.address.street", "subject.address.city",
                "subject.address.zip");
    }

    @Test
    void whenSchemaStatementHasAllOfCombination_thenAllPropertiesOffered() {
        var document     = """
                subject schema {
                  "allOf": [
                    { "type": "object", "properties": { "id": {} } },
                    { "type": "object", "properties": { "name": {} } }
                  ]
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.id", "subject.name");
    }

    @Test
    void whenSchemaStatementHasAnyOfCombination_thenAllPropertiesOffered() {
        var document     = """
                subject schema {
                  "anyOf": [
                    { "type": "object", "properties": { "email": {} } },
                    { "type": "object", "properties": { "phone": {} } }
                  ]
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.email", "subject.phone");
    }

    @Test
    void whenNoSchemaStatement_thenOnlyBaseVariablesOffered() {
        var document     = """
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "action", "resource", "environment");
        var expansions = proposals.stream().filter(p -> p.contains(".")).toList();
        assertThat(expansions).isEmpty();
    }

    @Test
    void whenPropertyNameContainsSpaces_thenQuotedPropertyPathOffered() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "first name": {}
                  }
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("subject", "subject.'first name'");
    }

    @Test
    void whenRecursiveSchemaStatement_thenLimitedDepthExpansions() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "name": {},
                    "children": {
                      "type": "array",
                      "items": { "$ref": "#" }
                    }
                  }
                }
                policy "test"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Should have recursive expansions but limited to prevent infinite recursion
        assertThat(proposals).contains("subject", "subject.name", "subject.children", "subject.children[]",
                "subject.children[].name", "subject.children[].children");
    }

    @Test
    void whenInSchemaContext_thenOnlyEnvVariablesOffered() {
        var document  = """
                subject schema
                """;
        var sapl      = parse(document);
        var variables = new HashMap<String, Value>();
        variables.put("personSchema", Value.of("{}"));
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        // In schema context, cursor is within the schema expression
        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, document.length(), config, true);

        assertThat(proposals).contains("personSchema").doesNotContain("subject", "action", "resource", "environment");
    }

    private static SaplContext parse(String content) {
        var charStream  = CharStreams.fromString(content);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        return parser.sapl();
    }

}
