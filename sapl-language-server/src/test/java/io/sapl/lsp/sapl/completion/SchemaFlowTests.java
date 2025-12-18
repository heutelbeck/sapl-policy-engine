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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.sapl.TestParsing;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Tests for schema flow through variable assignments.
 * When a variable is assigned from an expression with a known schema,
 * the schema should flow to the new variable.
 */
class SchemaFlowTests {

    @Test
    void whenVarAssignedFromSubject_thenSchemaFlows() {
        var document     = """
                subject schema { "type": "object", "properties": { "name": {}, "role": {} } }
                policy "test"
                permit
                where
                  var user = subject;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("user", "user.name", "user.role");
    }

    static Stream<Arguments> subscriptionElementFlowTestCases() {
        return Stream.of(arguments("subject", "userId"), arguments("action", "verb"), arguments("resource", "path"),
                arguments("environment", "time"));
    }

    @ParameterizedTest(name = "whenVarAssignedFrom{0}_thenSchemaFlows")
    @MethodSource("subscriptionElementFlowTestCases")
    void whenVarAssignedFromSubscriptionElement_thenSchemaFlows(String element, String property) {
        var document     = element + " schema { \"type\": \"object\", \"properties\": { \"" + property + "\": {} } }\n"
                + "policy \"test\" permit where var v = " + element + ";";
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("v", "v." + property);
    }

    @Test
    void whenMultiLevelVarChain_thenSchemaFlowsThroughChain() {
        var document     = """
                subject schema { "type": "object", "properties": { "id": {}, "name": {} } }
                policy "test"
                permit
                where
                  var a = subject;
                  var b = a;
                  var c = b;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // All variables in the chain should have schema expansions
        assertThat(proposals).contains("a", "a.id", "a.name", "b", "b.id", "b.name", "c", "c.id", "c.name");
    }

    @Test
    void whenVarAssignedFromVarWithExplicitSchema_thenSchemaFlows() {
        var document     = """
                policy "test"
                permit
                where
                  var config = {} schema { "type": "object", "properties": { "timeout": {} } };
                  var copy = config;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("config", "config.timeout", "copy", "copy.timeout");
    }

    @Test
    void whenVarAssignedFromGroupExpression_thenSchemaFlows() {
        var document     = """
                subject schema { "type": "object", "properties": { "role": {} } }
                policy "test"
                permit
                where
                  var user = (subject);
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("user", "user.role");
    }

    @Test
    void whenPolicySetHeaderVarAssignedFromSubject_thenSchemaFlows() {
        var document     = """
                subject schema { "type": "object", "properties": { "dept": {} } }
                set "access-control"
                deny-overrides
                var currentUser = subject
                policy "check"
                permit
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("currentUser", "currentUser.dept");
    }

    @Test
    void whenVarDefinedButNotInScope_thenNoSchemaExpansions() {
        var document = """
                subject schema { "type": "object", "properties": { "name": {} } }
                policy "test"
                permit
                where
                  var before = 1;
                  var user = subject;
                """;
        var sapl     = parse(document);
        // Position cursor before 'var user' definition
        var cursorOffset = document.indexOf("var user");
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // 'before' should be in scope, 'user' should not
        assertThat(proposals).contains("before").doesNotContain("user", "user.name");
    }

    @Test
    void whenMultipleVarsWithDifferentSchemas_thenEachHasOwnExpansions() {
        var document     = """
                subject schema { "type": "object", "properties": { "userId": {} } }
                action schema { "type": "object", "properties": { "verb": {} } }
                policy "test"
                permit
                where
                  var user = subject;
                  var act = action;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("user", "user.userId", "act", "act.verb")
                // Verify no cross-contamination
                .doesNotContain("user.verb", "act.userId");
    }

    @Test
    void whenVarAssignedFromLiteralValue_thenNoSchemaExpansions() {
        var document     = """
                policy "test"
                permit
                where
                  var count = 42;
                  var name = "test";
                  var flag = true;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("count", "name", "flag");
        var expansions = proposals.stream()
                .filter(p -> p.startsWith("count.") || p.startsWith("name.") || p.startsWith("flag.")).toList();
        assertThat(expansions).isEmpty();
    }

    @Test
    void whenVarAssignedFromObjectLiteral_thenNoSchemaExpansions() {
        var document     = """
                policy "test"
                permit
                where
                  var obj = { "key": "value" };
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Object literals don't have schemas (unless explicit schema is added)
        assertThat(proposals).contains("obj");
        var objExpansions = proposals.stream().filter(p -> p.startsWith("obj.")).toList();
        assertThat(objExpansions).isEmpty();
    }

    @Test
    void whenVarAssignedFromComplexExpression_thenNoSchemaExpansions() {
        var document     = """
                subject schema { "type": "object", "properties": { "count": {} } }
                policy "test"
                permit
                where
                  var result = subject.count + 1;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Complex expressions (arithmetic) don't preserve schema
        assertThat(proposals).contains("result");
        var resultExpansions = proposals.stream().filter(p -> p.startsWith("result.")).toList();
        assertThat(resultExpansions).isEmpty();
    }

    @Test
    void whenVarAssignedFromNestedProperty_thenNestedSchemaFlows() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "name": {},
                        "email": {}
                      }
                    }
                  }
                }
                policy "test"
                permit
                where
                  var userProfile = subject.profile;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // userProfile should have the nested schema with name and email properties
        assertThat(proposals).contains("userProfile", "userProfile.name", "userProfile.email");
    }

    @Test
    void whenVarAssignedFromArrayElement_thenArrayItemSchemaFlows() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "roles": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "roleName": {},
                          "permissions": {}
                        }
                      }
                    }
                  }
                }
                policy "test"
                permit
                where
                  var firstRole = subject.roles[0];
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // firstRole should have the array item schema
        assertThat(proposals).contains("firstRole", "firstRole.roleName", "firstRole.permissions");
    }

    @Test
    void whenVarAssignedFromDeeplyNestedProperty_thenSchemaFlows() {
        var document     = """
                subject schema {
                  "type": "object",
                  "properties": {
                    "organization": {
                      "type": "object",
                      "properties": {
                        "department": {
                          "type": "object",
                          "properties": {
                            "manager": {
                              "type": "object",
                              "properties": {
                                "name": {},
                                "employeeId": {}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                policy "test"
                permit
                where
                  var manager = subject.organization.department.manager;
                """;
        var sapl         = parse(document);
        var cursorOffset = document.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // manager should have the deeply nested schema
        assertThat(proposals).contains("manager", "manager.name", "manager.employeeId");
    }

    private static SaplContext parse(String content) {
        return TestParsing.parseSilently(content);
    }

}
