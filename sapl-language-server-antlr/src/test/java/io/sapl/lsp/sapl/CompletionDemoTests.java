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

import java.util.HashMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Demonstration of current LSP completion capabilities.
 * Run these tests to see what completions are offered in different contexts.
 */
class CompletionDemoTests {

    private final SAPLCompletionProvider provider = new SAPLCompletionProvider();

    @Test
    void demo1_emptyDocument() {
        var document = ""; // Empty document
        var position = new Position(0, 0);

        System.out.println("=== DEMO 1: Empty Document ===");
        System.out.println("Document: (empty)");
        System.out.println("Cursor: line 0, column 0");
        System.out.println("\nCompletions offered:");

        var completions = getCompletions(document, position);
        completions.forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getKind() + "]"));
        System.out.println();
    }

    @Test
    void demo2_afterPolicyKeyword() {
        var document = """
                policy "test"
                """;
        var position = new Position(1, 0); // After the policy declaration

        System.out.println("=== DEMO 2: After 'policy \"test\"' ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: line 1, column 0 (start of new line)");
        System.out.println("\nCompletions offered:");

        var completions = getCompletions(document, position);
        completions.forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getKind() + "]"));
        System.out.println();
    }

    @Test
    void demo3_insideWhereClause() {
        var document = """
                policy "test"
                permit
                where
                  """;
        var position = new Position(3, 2); // Inside where clause

        System.out.println("=== DEMO 3: Inside Where Clause ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: line 3, column 2 (after indentation)");
        System.out.println("\nCompletions offered:");

        var completions = getCompletions(document, position);
        completions.forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getKind() + "] "
                + (c.getDetail() != null ? "(" + c.getDetail() + ")" : "")));
        System.out.println();
    }

    @Test
    void demo4_withSchemaStatement() {
        var document = """
                subject schema { "type": "object", "properties": { "name": {}, "role": {}, "department": {} } }
                policy "test"
                permit
                where
                  """;
        var position = new Position(4, 2);

        System.out.println("=== DEMO 4: With Schema Statement ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: line 4, column 2");
        System.out.println("\nCompletions offered (note schema expansions):");

        var completions = getCompletions(document, position);
        var grouped     = completions.stream()
                .collect(Collectors.groupingBy(c -> c.getDetail() != null ? c.getDetail() : "Other"));
        grouped.forEach((detail, items) -> {
            System.out.println("\n  " + detail + ":");
            items.forEach(c -> System.out.println("    - " + c.getLabel()));
        });
        System.out.println();
    }

    @Test
    void demo5_withValueDefinitions() {
        var document = """
                policy "test"
                permit
                where
                  var user = subject;
                  var isAdmin = user.role == "ADMIN";
                  """;
        var position = new Position(5, 2);

        System.out.println("=== DEMO 5: With Value Definitions (Scope-Aware) ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: line 5, column 2 (after both var definitions)");
        System.out.println("\nCompletions offered (should include 'user' and 'isAdmin'):");

        var completions = getCompletions(document, position);
        var grouped     = completions.stream()
                .collect(Collectors.groupingBy(c -> c.getDetail() != null ? c.getDetail() : "Other"));
        grouped.forEach((detail, items) -> {
            System.out.println("\n  " + detail + ":");
            items.forEach(c -> System.out.println("    - " + c.getLabel()));
        });
        System.out.println();
    }

    @Test
    void demo6_withEnvironmentVariables() {
        var document = """
                policy "test"
                permit
                where
                  """;
        var position = new Position(3, 2);

        System.out.println("=== DEMO 6: With Environment Variables ===");
        System.out.println("Document:\n" + document);
        System.out.println("Environment variables configured:");
        System.out.println("  - appConfig: { serverUrl: 'https://api.example.com', timeout: 30 }");
        System.out.println("  - adminRoles: ['ADMIN', 'SUPERUSER']");
        System.out.println("\nCompletions offered:");

        var completions = getCompletionsWithEnvVars(document, position);
        var grouped     = completions.stream()
                .collect(Collectors.groupingBy(c -> c.getDetail() != null ? c.getDetail() : "Other"));
        grouped.forEach((detail, items) -> {
            System.out.println("\n  " + detail + ":");
            items.forEach(c -> System.out.println("    - " + c.getLabel()));
        });
        System.out.println();
    }

    @Test
    void demo7_policySetWithHeaderVars() {
        var document = """
                set "access-control"
                deny-overrides
                var defaultTimeout = 30
                var allowedRoles = ["ADMIN", "USER"]

                policy "check-role"
                permit where
                  """;
        var position = new Position(7, 2);

        System.out.println("=== DEMO 7: Policy Set with Header Variables ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: line 7, column 2 (inside policy, after set-level vars)");
        System.out.println("\nCompletions offered (should include 'defaultTimeout' and 'allowedRoles'):");

        var completions = getCompletions(document, position);
        var grouped     = completions.stream()
                .collect(Collectors.groupingBy(c -> c.getDetail() != null ? c.getDetail() : "Other"));
        grouped.forEach((detail, items) -> {
            System.out.println("\n  " + detail + ":");
            items.forEach(c -> System.out.println("    - " + c.getLabel()));
        });
        System.out.println();
    }

    @Test
    void demo8_functionCompletions() {
        var document = """
                policy "test"
                permit
                where
                  """;
        var position = new Position(3, 2);

        System.out.println("=== DEMO 8: Function Completions ===");
        System.out.println("Document:\n" + document);
        System.out.println("\nFunction completions offered:");

        var completions = getCompletions(document, position);
        completions.stream().filter(c -> c.getKind() == org.eclipse.lsp4j.CompletionItemKind.Function).forEach(
                c -> System.out.println("  - " + c.getLabel() + (c.getDetail() != null ? " -> " + c.getDetail() : "")));
        System.out.println();
    }

    @Test
    void demo9_afterImportKeyword() {
        var document = """
                import """;
        var position = new Position(0, 7);

        System.out.println("=== DEMO 9: After 'import' Keyword ===");
        System.out.println("Document: import ");
        System.out.println("Cursor: after 'import '");
        System.out.println("\nCompletions offered (library names):");

        var completions = getCompletions(document, position);
        completions.forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getKind() + "]"));
        System.out.println();
    }

    @Test
    void demo10_combiningAlgorithm() {
        var document = """
                set "test"
                """;
        var position = new Position(1, 0);

        System.out.println("=== DEMO 10: Combining Algorithm Position ===");
        System.out.println("Document:\n" + document);
        System.out.println("Cursor: after 'set \"test\"' on new line");
        System.out.println("\nCompletions offered (combining algorithms):");

        var completions = getCompletions(document, position);
        completions.forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getKind() + "]"));
        System.out.println();
    }

    // Helper methods

    private java.util.List<org.eclipse.lsp4j.CompletionItem> getCompletions(String content, Position position) {
        var document      = new SAPLParsedDocument("file:///test.sapl", content);
        var configManager = createConfigManager(LSPConfiguration.minimal());
        return provider.provideCompletions(document, position, configManager);
    }

    private java.util.List<org.eclipse.lsp4j.CompletionItem> getCompletionsWithEnvVars(String content,
            Position position) {
        var appConfig = ObjectValue.builder().put("serverUrl", Value.of("https://api.example.com"))
                .put("timeout", Value.of(30)).build();

        var variables = new HashMap<String, Value>();
        variables.put("appConfig", appConfig);
        variables.put("adminRoles",
                io.sapl.api.model.ArrayValue.builder().add(Value.of("ADMIN")).add(Value.of("SUPERUSER")).build());

        var config        = new LSPConfiguration("", LSPConfiguration.minimal().documentationBundle(), variables, null,
                null);
        var document      = new SAPLParsedDocument("file:///test.sapl", content);
        var configManager = createConfigManager(config);
        return provider.provideCompletions(document, position, configManager);
    }

    private ConfigurationManager createConfigManager(LSPConfiguration config) {
        var manager = new ConfigurationManager();
        manager.registerConfiguration("", config);
        return manager;
    }

}
