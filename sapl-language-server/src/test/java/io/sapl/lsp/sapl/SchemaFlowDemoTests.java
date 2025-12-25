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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.lsp4j.CompletionItemKind.Variable;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Tests for schema flow through value definitions.
 */
@Slf4j
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

        log.debug("=== Schema Flow Through var Assignment ===");
        log.debug("Document:\n{}", document);

        var completions     = getCompletions(document, position);
        var userCompletions = completions.stream().filter(c -> c.getLabel().startsWith("user")).toList();

        log.debug("Completions containing 'user': {}",
                userCompletions.stream().map(c -> c.getLabel() + " [" + c.getDetail() + "]").toList());

        assertThat(completions).as("Schema should flow from 'subject' to 'user' variable")
                .anyMatch(c -> c.getLabel().startsWith("user."));
    }

    @Test
    void demo2_explicitSchemaOnVar() {
        // Note: Grammar requires schema AFTER assignment: var x = expr schema {...}
        var document = """
                policy "test"
                permit
                where
                  var security = {} schema { "type": "object", "properties": { "timeout": {}, "retries": {} } };
                  """;
        var position = new Position(4, 2);

        log.debug("=== Explicit Schema on var Definition ===");
        log.debug("Document:\n{}", document);
        log.debug("Note: Grammar syntax is 'var x = expr schema {{...}}' (schema AFTER assignment)");

        var completions       = getCompletions(document, position);
        var configCompletions = completions.stream().filter(c -> c.getLabel().startsWith("security")).toList();

        log.debug("Completions containing 'security': {}",
                configCompletions.stream().map(c -> c.getLabel() + " [" + c.getDetail() + "]").toList());

        assertThat(completions).as("Explicit schema on var should provide property completions")
                .anyMatch(c -> c.getLabel().startsWith("security."));
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

        log.debug("=== Nested Schema Flow ===");
        log.debug("Document:\n{}", document);

        var completions        = getCompletions(document, position);
        var profileCompletions = completions.stream().filter(c -> c.getLabel().startsWith("profile")).toList();

        log.debug("Completions containing 'profile': {}",
                profileCompletions.stream().map(c -> c.getLabel() + " [" + c.getDetail() + "]").toList());

        assertThat(completions).as("Nested schema should flow from 'user.profile' to 'profile' variable")
                .anyMatch(c -> "profile.firstName".equals(c.getLabel()) || "profile.lastName".equals(c.getLabel())
                        || "profile.email".equals(c.getLabel()));
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

        log.debug("=== Multiple var Chain ===");
        log.debug("Document:\n{}", document);

        var completions = getCompletions(document, position);

        for (var varName : new String[] { "a", "b", "c" }) {
            var expansions = completions.stream().filter(c -> c.getLabel().startsWith(varName + "."))
                    .map(c -> c.getLabel()).toList();
            log.debug("Variable '{}' expansions: {}", varName, expansions.isEmpty() ? "(none)" : expansions);
        }

        assertThat(completions).as("Schema should flow through chain: subject -> a -> b -> c")
                .anyMatch(c -> c.getLabel().startsWith("a.")).anyMatch(c -> c.getLabel().startsWith("b."))
                .anyMatch(c -> c.getLabel().startsWith("c."));
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

        log.debug("=== All Subscription Elements with Schemas ===");
        log.debug("Document:\n{}", document);

        var completions = getCompletions(document, position);

        for (var element : new String[] { "subject", "action", "resource", "environment" }) {
            var expansions = completions.stream().filter(c -> c.getLabel().startsWith(element + "."))
                    .map(c -> c.getLabel()).toList();
            log.debug("Element '{}' expansions: {}", element, expansions);
        }

        assertThat(completions).as("All subscription elements should have schema expansions")
                .anyMatch(c -> c.getLabel().startsWith("subject.")).anyMatch(c -> c.getLabel().startsWith("action."))
                .anyMatch(c -> c.getLabel().startsWith("resource."))
                .anyMatch(c -> c.getLabel().startsWith("environment."));
    }

    @Test
    @DisplayName("Completion after dot should not duplicate prefix")
    void completionAfterDotShouldNotDuplicatePrefix() {
        var document = """
                subject schema { "type": "object", "properties": { "userId": {} } }
                policy "test"
                permit
                where
                  subject.""";
        // Position at end of "subject." (line 4, after the dot)
        var position = new Position(4, 10);

        var completions = getCompletions(document, position);

        // Log all completions for debugging
        log.debug("=== All completions at 'subject.' ===");
        completions.forEach(c -> {
            var te = c.getTextEdit();
            if (te != null && te.isLeft()) {
                var edit = te.getLeft();
                log.debug("  [TextEdit] label='{}', range=({},{})-({},{}), newText='{}'", c.getLabel(),
                        edit.getRange().getStart().getLine(), edit.getRange().getStart().getCharacter(),
                        edit.getRange().getEnd().getLine(), edit.getRange().getEnd().getCharacter(), edit.getNewText());
            } else {
                log.debug("  [NO TextEdit] label='{}', insertText='{}'", c.getLabel(), c.getInsertText());
            }
        });

        // Check for duplicates - same label should not appear multiple times
        var subjectUserIdCompletions = completions.stream().filter(c -> "subject.userId".equals(c.getLabel())).toList();
        log.debug("Found {} completions with label 'subject.userId'", subjectUserIdCompletions.size());

        assertThat(subjectUserIdCompletions).as("Should have exactly one subject.userId completion (no duplicates)")
                .hasSize(1);

        var subjectUserIdCompletion = subjectUserIdCompletions.getFirst();

        assertThat(subjectUserIdCompletion).as("Should offer subject.userId completion").isNotNull();

        var textEdit = subjectUserIdCompletion.getTextEdit();
        assertThat(textEdit).as("Completion should use TextEdit").isNotNull();
        assertThat(textEdit.isLeft()).as("TextEdit should be standard TextEdit").isTrue();

        var edit = textEdit.getLeft();

        // Verify TextEdit range
        assertThat(edit.getRange().getStart().getCharacter()).as("Start at beginning of 'subject'").isEqualTo(2);
        assertThat(edit.getRange().getEnd().getCharacter()).as("End at cursor position").isEqualTo(10);
        assertThat(edit.getNewText()).as("NewText is full completion").isEqualTo("subject.userId");

        // Verify applying TextEdit produces correct result
        var lines     = document.split("\n", -1);
        var line      = lines[4];
        var afterEdit = line.substring(0, edit.getRange().getStart().getCharacter()) + edit.getNewText()
                + line.substring(edit.getRange().getEnd().getCharacter());

        log.debug("Before: '{}', After: '{}'", line, afterEdit);
        assertThat(afterEdit).as("Result should be 'subject.userId' not 'subject.subject.userId'")
                .isEqualTo("  subject.userId");

        // Verify ALL variable completions with dots use TextEdit (not just insertText)
        var schemaPathCompletions = completions.stream()
                .filter(c -> c.getLabel().contains(".") && c.getKind() == Variable).toList();
        log.debug("Schema path variable completions: {}", schemaPathCompletions.size());
        for (var completion : schemaPathCompletions) {
            assertThat(completion.getTextEdit())
                    .as("Schema path completion '%s' should use TextEdit", completion.getLabel()).isNotNull();
        }
    }

    private List<CompletionItem> getCompletions(String content, Position position) {
        var document = new SAPLParsedDocument("file:///test.sapl", content);
        // Default ConfigurationManager uses DefaultConfigurationProvider which returns
        // LSPConfiguration.minimal()
        var configManager = new ConfigurationManager();
        return provider.provideCompletions(document, position, configManager);
    }

}
