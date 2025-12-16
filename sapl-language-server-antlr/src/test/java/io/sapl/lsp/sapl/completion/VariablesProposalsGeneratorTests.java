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
import java.util.Map;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.configuration.LSPConfiguration;

class VariablesProposalsGeneratorTests {

    private static final String SIMPLE_POLICY = """
            policy "test"
            permit
            """;

    private static final String POLICY_WITH_BODY = """
            policy "test"
            permit
            where
              var user = subject.name;
              var role = subject.role;
            """;

    private static final String POLICY_SET_WITH_VARS = """
            set "test-set"
            deny-overrides
            var adminRole = "ADMIN"
            policy "admin-only"
            permit where
              subject.role == adminRole;
            """;

    private static final String POLICY_WITH_SCHEMA = """
            subject schema { "type": "object", "properties": { "name": {}, "role": {} } }
            policy "test"
            permit
            """;

    @Test
    void whenSimplePolicy_thenReturnsAuthorizationSubscriptionVariables() {
        var sapl      = parse(SIMPLE_POLICY);
        var config    = LSPConfiguration.minimal();
        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, false);

        assertThat(proposals).contains("subject", "action", "resource", "environment");
    }

    @Test
    void whenInSchemaContext_thenReturnsOnlyEnvironmentVariables() {
        var sapl = parse(SIMPLE_POLICY);
        // Create config with environment variables
        var variables = new HashMap<String, Value>();
        variables.put("appConfig", Value.of("test"));
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, true);

        // In schema context, only environment variables should be offered
        assertThat(proposals).contains("appConfig").doesNotContain("subject", "action", "resource", "environment");
    }

    @Test
    void whenEnvironmentVariablesConfigured_thenIncludedInProposals() {
        var sapl      = parse(SIMPLE_POLICY);
        var variables = new HashMap<String, Value>();
        variables.put("serverUrl", Value.of("https://api.example.com"));
        variables.put("apiKey", Value.of("secret"));
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, false);

        assertThat(proposals).contains("serverUrl", "apiKey");
    }

    @Test
    void whenEnvironmentVariableIsObject_thenExpandsToPropertyPaths() {
        var sapl      = parse(SIMPLE_POLICY);
        var config    = ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(8080)).build();
        var variables = new HashMap<String, Value>();
        variables.put("serverConfig", config);
        var lspConfig = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null,
                null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, lspConfig, false);

        assertThat(proposals).contains("serverConfig", "serverConfig.host", "serverConfig.port");
    }

    @Test
    void whenPolicyBodyHasValueDefinitions_thenInScopeVariablesIncluded() {
        var sapl = parse(POLICY_WITH_BODY);
        // Position after both variable definitions
        var cursorOffset = POLICY_WITH_BODY.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("user", "role");
    }

    @Test
    void whenPolicyBodyHasValueDefinitions_andCursorBeforeFirst_thenNoValueDefinitionsInScope() {
        var sapl = parse(POLICY_WITH_BODY);
        // Position before first variable definition
        var cursorOffset = POLICY_WITH_BODY.indexOf("var user");
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Base subscription variables should be included, but not user/role
        assertThat(proposals).contains("subject", "action", "resource", "environment").doesNotContain("user", "role");
    }

    @Test
    void whenPolicySetHasValueDefinitions_thenIncluded() {
        var sapl         = parse(POLICY_SET_WITH_VARS);
        var cursorOffset = POLICY_SET_WITH_VARS.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        assertThat(proposals).contains("adminRole");
    }

    @Test
    void whenPolicyHasSchemaStatement_thenSchemaExpansionsIncluded() {
        var sapl         = parse(POLICY_WITH_SCHEMA);
        var cursorOffset = POLICY_WITH_SCHEMA.length();
        var config       = LSPConfiguration.minimal();

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset, config, false);

        // Should include base variable and schema expansions
        assertThat(proposals).contains("subject", "subject.name", "subject.role");
    }

    @Test
    void whenNullSaplContext_thenEmptyList() {
        var config    = LSPConfiguration.minimal();
        var proposals = VariablesProposalsGenerator.variableProposalsForContext(null, 0, config, false);

        assertThat(proposals).isEmpty();
    }

    static Stream<Arguments> authorizationSubscriptionVariablesTestCases() {
        return Stream.of(arguments("subject"), arguments("action"), arguments("resource"), arguments("environment"));
    }

    @ParameterizedTest(name = "whenSimplePolicy_thenContains_{0}")
    @MethodSource("authorizationSubscriptionVariablesTestCases")
    void whenSimplePolicy_thenContainsAuthorizationVariable(String variableName) {
        var sapl      = parse(SIMPLE_POLICY);
        var config    = LSPConfiguration.minimal();
        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, false);

        assertThat(proposals).contains(variableName);
    }

    @Test
    void whenNestedObjectVariable_thenExpandsToNestedPaths() {
        var sapl        = parse(SIMPLE_POLICY);
        var innerObject = ObjectValue.builder().put("street", Value.of("Main St")).put("city", Value.of("Boston"))
                .build();
        var outerObject = ObjectValue.builder().put("address", innerObject).put("name", Value.of("Test")).build();
        var variables   = new HashMap<String, Value>();
        variables.put("person", outerObject);
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, false);

        assertThat(proposals).contains("person", "person.address", "person.address.street", "person.address.city",
                "person.name");
    }

    @Test
    void whenArrayVariable_thenExpandsWithArrayNotation() {
        var sapl      = parse(SIMPLE_POLICY);
        var array     = ArrayValue.builder().add(Value.of("item1")).add(Value.of("item2")).build();
        var variables = new HashMap<String, Value>();
        variables.put("items", array);
        var config = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null, null);

        var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, 100, config, false);

        assertThat(proposals).contains("items", "items.[?]");
    }

    private static SaplContext parse(String content) {
        var charStream  = CharStreams.fromString(content);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        return parser.sapl();
    }

}
