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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Integration tests for schema extensions after function and attribute calls.
 * These tests validate the complete flow from document parsing through
 * SAPLCompletionProvider to ensure schema-based completions work correctly.
 */
class FunctionSchemaExtensionIntegrationTests {

    private static final String TIME_NOW_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "year": { "type": "integer" },
                "month": { "type": "integer" },
                "day": { "type": "integer" },
                "hour": { "type": "integer" },
                "minute": { "type": "integer" },
                "second": { "type": "integer" }
              }
            }
            """;

    private static final String AUTH_USER_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "username": { "type": "string" },
                "roles": { "type": "array", "items": { "type": "string" } },
                "department": { "type": "string" },
                "profile": {
                  "type": "object",
                  "properties": {
                    "firstName": { "type": "string" },
                    "lastName": { "type": "string" }
                  }
                }
              }
            }
            """;

    private SAPLCompletionProvider completionProvider;
    private ConfigurationManager   configManager;

    @BeforeEach
    void setup() {
        completionProvider = new SAPLCompletionProvider();
        configManager      = createConfigManager();
    }

    static Stream<Arguments> schemaPropertiesOfferedTestCases() {
        return Stream.of(
                arguments("Function call - all time properties", "policy \"test\" permit where time.now().",
                        List.of("year", "month", "day", "hour", "minute", "second")),
                arguments("Function call typing - partial match", "policy \"test\" permit where time.now().ye",
                        List.of("year", "month", "day")),
                arguments("Attribute - user properties with nested",
                        "policy \"test\" permit where subject.<auth.user>.",
                        List.of("username", "roles", "roles[]", "department", "profile", "profile.firstName",
                                "profile.lastName")),
                arguments("Environment attribute - user properties",
                        "policy \"test\" permit where |<auth.currentUser>.", List.of("username", "department")),
                arguments("Imported function - short name", "import time.now policy \"test\" permit where now().",
                        List.of("year", "month", "day")),
                arguments("Imported function with alias",
                        "import time.now as currentTime policy \"test\" permit where currentTime().",
                        List.of("year", "month", "day")),
                arguments("Chained function calls - last function schema used",
                        "policy \"test\" permit where standard.textOf(value).time.now().",
                        List.of("year", "month", "day")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaPropertiesOfferedTestCases")
    void whenContextMatches_thenSchemaPropertiesOffered(String description, String document,
            List<String> expectedProperties) {
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        assertThat(labels).containsAll(expectedProperties);
    }

    static Stream<Arguments> noSchemaExtensionsTestCases() {
        return Stream.of(
                arguments("Simple variable access - no function schema", "policy \"test\" permit where subject.",
                        List.of("year", "month", "day")),
                arguments("Function without schema - no properties",
                        "policy \"test\" permit where standard.textOf(value).",
                        List.of("year", "month", "day", "username")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noSchemaExtensionsTestCases")
    void whenContextDoesNotMatch_thenNoSchemaExtensions(String description, String document,
            List<String> unexpectedProperties) {
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        assertThat(labels).doesNotContainAnyElementsOf(unexpectedProperties);
    }

    @Test
    void whenSchemaExtensionDetailIsSet_thenContainsFunctionName() {
        var document = """
                policy "test"
                permit
                where
                  time.now().""";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var yearItem    = completions.stream().filter(i -> "year".equals(i.getLabel())).findFirst();

        assertThat(yearItem).isPresent();
        assertThat(yearItem.get().getDetail()).contains("time.now");
    }

    private List<CompletionItem> getCompletions(String content, Position position) {
        var parsedDocument = new SAPLParsedDocument("test://test.sapl", content);
        return completionProvider.provideCompletions(parsedDocument, position, configManager);
    }

    private static Position positionAtEnd(String document) {
        var lines = document.split("\n", -1);
        var line  = lines.length - 1;
        var col   = lines[line].length();
        return new Position(line, col);
    }

    private static ConfigurationManager createConfigManager() {
        var config = createConfiguration();
        return new ConfigurationManager(id -> config);
    }

    private static LSPConfiguration createConfiguration() {
        var timeNow     = new EntryDocumentation(EntryType.FUNCTION, "now", "Returns current time", TIME_NOW_SCHEMA,
                List.of());
        var textOf      = new EntryDocumentation(EntryType.FUNCTION, "textOf", "Converts to text", null, List.of());
        var timeLib     = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "time", "Time functions", "Docs",
                List.of(timeNow));
        var standardLib = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "standard", "Standard functions",
                "Docs", List.of(textOf));

        var authUser    = new EntryDocumentation(EntryType.ATTRIBUTE, "user", "Get user", AUTH_USER_SCHEMA, List.of());
        var currentUser = new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, "currentUser", "Current user",
                "{ \"type\": \"object\", \"properties\": { \"username\": {}, \"department\": {} } }", List.of());
        var authPip     = new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, "auth", "Auth PIP", "Docs",
                List.of(authUser, currentUser));

        var bundle = new DocumentationBundle(List.of(timeLib, standardLib, authPip));
        return new LSPConfiguration("", bundle, Map.of(), null, null);
    }

}
