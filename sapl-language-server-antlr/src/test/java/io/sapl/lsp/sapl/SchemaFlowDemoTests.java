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

import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Tests for schema flow through value definitions.
 */
class SchemaFlowDemoTests {

    private final SAPLCompletionProvider provider = new SAPLCompletionProvider();

    @Test
    void demo1_schemaFlowsThroughVarAssignment() {
        var document = """
                subject schema { "type": "object", "properties": { "name": {}, "role": {}, "clearance": {} } }
                policy "test"
                permit
                where
                  var user = subject;
                  """;
        var position = new Position(5, 2);

        System.out.println("=== Schema Flow Through var Assignment ===");
        System.out.println("Document:\n" + document);
        System.out.println("Question: Does 'user' get schema expansions from 'subject'?");
        System.out.println("\nCompletions containing 'user':");

        var completions = getCompletions(document, position);
        completions.stream().filter(c -> c.getLabel().startsWith("user"))
                .forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getDetail() + "]"));

        var hasUserExpansions = completions.stream().anyMatch(c -> c.getLabel().startsWith("user."));
        System.out.println(
                "\nResult: " + (hasUserExpansions ? "YES - Schema flows through!" : "NO - Schema does not flow"));
        System.out.println();
    }

    @Test
    void demo2_explicitSchemaOnVar() {
        // Note: Grammar requires schema AFTER assignment: var x = expr schema {...}
        var document = """
                policy "test"
                permit
                where
                  var config = {} schema { "type": "object", "properties": { "timeout": {}, "retries": {} } };
                  """;
        var position = new Position(4, 2);

        System.out.println("=== Explicit Schema on var Definition ===");
        System.out.println("Document:\n" + document);
        System.out.println("Note: Grammar syntax is 'var x = expr schema {...}' (schema AFTER assignment)");
        System.out.println("Question: Does explicit 'schema {...}' on var work?");
        System.out.println("\nCompletions containing 'config':");

        var completions = getCompletions(document, position);
        completions.stream().filter(c -> c.getLabel().startsWith("config"))
                .forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getDetail() + "]"));

        var hasConfigExpansions = completions.stream().anyMatch(c -> c.getLabel().startsWith("config."));
        System.out.println("\nResult: "
                + (hasConfigExpansions ? "YES - Explicit schema works!" : "NO - Explicit schema not working"));
        System.out.println();
    }

    @Test
    void demo3_nestedSchemaFlow() {
        var document = """
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
                where
                  var user = subject;
                  var profile = user.profile;
                  """;
        var position = new Position(18, 2);

        System.out.println("=== Nested Schema Flow ===");
        System.out.println("Document:\n" + document);
        System.out.println("Question: Does 'profile' get nested schema from 'user.profile'?");
        System.out.println("\nCompletions containing 'profile':");

        var completions = getCompletions(document, position);
        completions.stream().filter(c -> c.getLabel().startsWith("profile"))
                .forEach(c -> System.out.println("  - " + c.getLabel() + " [" + c.getDetail() + "]"));

        var hasNestedExpansions = completions.stream().anyMatch(c -> c.getLabel().equals("profile.firstName")
                || c.getLabel().equals("profile.lastName") || c.getLabel().equals("profile.email"));
        System.out.println(
                "\nResult: " + (hasNestedExpansions ? "YES - Nested schema flows!" : "NO - Nested schema not flowing"));
        System.out.println();
    }

    @Test
    void demo4_multipleVarChain() {
        var document = """
                subject schema { "type": "object", "properties": { "id": {}, "name": {}, "dept": {} } }
                policy "test"
                permit
                where
                  var a = subject;
                  var b = a;
                  var c = b;
                  """;
        var position = new Position(7, 2);

        System.out.println("=== Multiple var Chain ===");
        System.out.println("Document:\n" + document);
        System.out.println("Question: Does schema flow through chain: subject -> a -> b -> c?");
        System.out.println("\nCompletions for each variable:");

        var completions = getCompletions(document, position);

        for (var varName : new String[] { "a", "b", "c" }) {
            var expansions = completions.stream().filter(c -> c.getLabel().startsWith(varName + "."))
                    .map(c -> c.getLabel()).collect(Collectors.toList());
            System.out.println("  " + varName + ": " + (expansions.isEmpty() ? "(no expansions)" : expansions));
        }
        System.out.println();
    }

    @Test
    void demo5_subscriptionElementsAllHaveSchemas() {
        var document = """
                subject schema { "type": "object", "properties": { "userId": {} } }
                action schema { "type": "object", "properties": { "verb": {} } }
                resource schema { "type": "object", "properties": { "path": {} } }
                environment schema { "type": "object", "properties": { "time": {} } }
                policy "test"
                permit
                where
                  """;
        var position = new Position(7, 2);

        System.out.println("=== All Subscription Elements with Schemas ===");
        System.out.println("Document:\n" + document);
        System.out.println("\nSchema expansions for each subscription element:");

        var completions = getCompletions(document, position);

        for (var element : new String[] { "subject", "action", "resource", "environment" }) {
            var expansions = completions.stream().filter(c -> c.getLabel().startsWith(element + "."))
                    .map(c -> c.getLabel()).collect(Collectors.toList());
            System.out.println("  " + element + ": " + expansions);
        }
        System.out.println();
    }

    private java.util.List<org.eclipse.lsp4j.CompletionItem> getCompletions(String content, Position position) {
        var document      = new SAPLParsedDocument("file:///test.sapl", content);
        var configManager = new ConfigurationManager();
        configManager.registerConfiguration("", LSPConfiguration.minimal());
        return provider.provideCompletions(document, position, configManager);
    }

}
