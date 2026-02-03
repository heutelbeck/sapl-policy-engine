/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.sapl.TestParsing;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.completion.ContextAnalyzer.ContextAnalysisResult;
import io.sapl.lsp.sapl.completion.ContextAnalyzer.ProposalType;

/**
 * Tests for LibraryProposalsGenerator schema extension methods.
 */
class LibraryProposalsGeneratorTests {

    private static final String TIME_SCHEMA = """
            { "type": "object", "properties": { "year": {}, "month": {}, "day": {}, "hour": {}, "minute": {}, "second": {} } }""";

    private static final String USER_SCHEMA = """
            { "type": "object", "properties": { "username": {}, "roles": { "type": "array", "items": {} }, "department": {} } }""";

    private static final String NESTED_SCHEMA = """
            { "type": "object", "properties": { "profile": { "type": "object", "properties": { "name": {}, "email": {} } } } }""";

    static Stream<Arguments> functionSchemaTestCases() {
        return Stream.of(
                arguments("Function with schema - offers all properties", "time.now", "policy \"test\" permit",
                        TIME_SCHEMA, List.of("year", "month", "day", "hour", "minute", "second")),
                arguments("Function without schema - no proposals", "standard.textOf", "policy \"test\" permit", null,
                        List.of()),
                arguments("Nested schema - includes nested paths", "custom.getData", "policy \"test\" permit",
                        NESTED_SCHEMA, List.of("profile", "profile.name", "profile.email")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("functionSchemaTestCases")
    void whenFunctionAnalyzed_thenExpectedProposalsReturned(String description, String functionName, String document,
            String schema, List<String> expectedProposals) {
        var analysis  = new ContextAnalysisResult("", "", functionName, ProposalType.FUNCTION);
        var libName   = functionName.contains(".") ? functionName.substring(0, functionName.indexOf('.'))
                : functionName;
        var entryName = functionName.contains(".") ? functionName.substring(functionName.indexOf('.') + 1)
                : functionName;
        var config    = configWithFunction(libName, entryName, schema);
        var sapl      = parse(document);

        var proposals = LibraryProposalsGenerator.allFunctionSchemaExtensions(analysis, sapl, config);

        if (expectedProposals.isEmpty()) {
            assertThat(proposals).isEmpty();
        } else {
            assertThat(proposals).containsAll(expectedProposals);
        }
    }

    @Test
    void whenFunctionNotInConfig_thenNoProposals() {
        var analysis = new ContextAnalysisResult("", "", "nonexistent.func", ProposalType.FUNCTION);
        var config   = configWithFunction("time", "now", TIME_SCHEMA);
        var sapl     = parse("policy \"test\" permit");

        var proposals = LibraryProposalsGenerator.allFunctionSchemaExtensions(analysis, sapl, config);

        assertThat(proposals).isEmpty();
    }

    @Test
    void whenAttributeHasSchema_thenSchemaPropertiesOffered() {
        var analysis = new ContextAnalysisResult("", "", "auth.user", ProposalType.ATTRIBUTE);
        var config   = configWithAttribute("auth", "user", USER_SCHEMA);
        var sapl     = parse("policy \"test\" permit");

        var proposals = LibraryProposalsGenerator.allAttributeSchemaExtensions(analysis, sapl, config);

        assertThat(proposals).contains("username", "roles", "roles[]", "department");
    }

    @Test
    void whenEnvironmentAttributeHasSchema_thenSchemaPropertiesOffered() {
        var analysis = new ContextAnalysisResult("", "", "auth.currentUser", ProposalType.ENVIRONMENT_ATTRIBUTE);
        var config   = configWithEnvAttribute("auth", "currentUser", USER_SCHEMA);
        var sapl     = parse("policy \"test\" permit");

        var proposals = LibraryProposalsGenerator.allEnvironmentAttributeSchemaExtensions(analysis, sapl, config);

        assertThat(proposals).contains("username", "department");
    }

    static Stream<Arguments> importAliasTestCases() {
        return Stream.of(arguments("Function imported without alias", "now", "import time.now policy \"test\" permit"),
                arguments("Function imported with explicit alias", "currentTime",
                        "import time.now as currentTime policy \"test\" permit"),
                arguments("Library imported - short name works", "now", "import time policy \"test\" permit"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("importAliasTestCases")
    void whenFunctionImportedWithAlias_thenAliasMatchesSchema(String description, String alias, String document) {
        var analysis = new ContextAnalysisResult("", "", alias, ProposalType.FUNCTION);
        var config   = configWithFunction("time", "now", TIME_SCHEMA);
        var sapl     = parse(document);

        var proposals = LibraryProposalsGenerator.allFunctionSchemaExtensions(analysis, sapl, config);

        assertThat(proposals).contains("year", "month", "day");
    }

    @Test
    void whenAliasNamesForFunction_thenReturnsFQNAndAllAliases() {
        var sapl    = parse("import time.now as t import time policy \"test\" permit");
        var aliases = LibraryProposalsGenerator.aliasNamesForFunction("time.now", sapl);

        assertThat(aliases).containsExactlyInAnyOrder("time.now", "t", "now");
    }

    @Test
    void whenNoImports_thenOnlyFQNReturned() {
        var sapl    = parse("policy \"test\" permit");
        var aliases = LibraryProposalsGenerator.aliasNamesForFunction("time.now", sapl);

        assertThat(aliases).containsExactly("time.now");
    }

    private static LSPConfiguration configWithFunction(String library, String name, String schema) {
        var entry   = new EntryDocumentation(EntryType.FUNCTION, name, "Documentation", schema, List.of());
        var lib     = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, library, "Library", "Docs",
                List.of(entry));
        var bundle  = new DocumentationBundle(List.of(lib));
        var minimal = LSPConfiguration.minimal();
        return new LSPConfiguration("", bundle, Map.of(), minimal.functionBroker(), minimal.attributeBroker());
    }

    private static LSPConfiguration configWithAttribute(String pip, String name, String schema) {
        var entry   = new EntryDocumentation(EntryType.ATTRIBUTE, name, "Documentation", schema, List.of());
        var lib     = new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, pip, "PIP", "Docs",
                List.of(entry));
        var bundle  = new DocumentationBundle(List.of(lib));
        var minimal = LSPConfiguration.minimal();
        return new LSPConfiguration("", bundle, Map.of(), minimal.functionBroker(), minimal.attributeBroker());
    }

    private static LSPConfiguration configWithEnvAttribute(String pip, String name, String schema) {
        var entry   = new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, name, "Documentation", schema, List.of());
        var lib     = new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, pip, "PIP", "Docs",
                List.of(entry));
        var bundle  = new DocumentationBundle(List.of(lib));
        var minimal = LSPConfiguration.minimal();
        return new LSPConfiguration("", bundle, Map.of(), minimal.functionBroker(), minimal.attributeBroker());
    }

    private static SaplContext parse(String content) {
        return TestParsing.parseSilently(content);
    }

}
