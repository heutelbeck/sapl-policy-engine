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

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;

/**
 * Integration tests for schema flow through variable definitions. These tests
 * verify that schema information propagates correctly through the completion
 * system when variables are assigned from schema-typed sources.
 */
class SchemaFlowIntegrationTests {

    private final SAPLCompletionProvider provider = new SAPLCompletionProvider();

    @Test
    void whenVarAssignedFromSubjectWithSchema_thenVarGetsSchemaExpansions() {
        var document    = """
                subject schema { "type": "object", "properties": { "name": {}, "role": {}, "clearance": {} } }
                policy "test"
                permit
                where
                  var user = subject;
                  """;
        var position    = new Position(5, 2);
        var completions = getCompletions(document, position);

        assertThat(completionLabels(completions)).contains("user.name", "user.role", "user.clearance");
    }

    @Test
    void whenVarHasExplicitSchema_thenVarGetsSchemaExpansions() {
        var document    = """
                policy "test"
                permit
                where
                  var security = {} schema { "type": "object", "properties": { "timeout": {}, "retries": {} } };
                  """;
        var position    = new Position(4, 2);
        var completions = getCompletions(document, position);

        assertThat(completionLabels(completions)).contains("security.timeout", "security.retries");
    }

    @Test
    void whenVarAssignedFromNestedSchemaPath_thenVarGetsNestedSchemaExpansions() {
        var document    = """
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
        var position    = new Position(18, 2);
        var completions = getCompletions(document, position);

        assertThat(completionLabels(completions)).contains("profile.firstName", "profile.lastName", "profile.email");
    }

    @Test
    void whenSchemaFlowsThroughMultipleVarChain_thenAllVarsGetSchemaExpansions() {
        var document    = """
                subject schema { "type": "object", "properties": { "id": {}, "name": {}, "dept": {} } }
                policy "test"
                permit
                where
                  var a = subject;
                  var b = a;
                  var c = b;
                  """;
        var position    = new Position(7, 2);
        var completions = getCompletions(document, position);

        var labels = completionLabels(completions);
        assertThat(labels).contains("a.id", "a.name", "a.dept").contains("b.id", "b.name", "b.dept").contains("c.id",
                "c.name", "c.dept");
    }

    @Test
    void whenAllSubscriptionElementsHaveSchemas_thenAllGetSchemaExpansions() {
        var document    = """
                subject schema { "type": "object", "properties": { "userId": {} } }
                action schema { "type": "object", "properties": { "verb": {} } }
                resource schema { "type": "object", "properties": { "path": {} } }
                environment schema { "type": "object", "properties": { "time": {} } }
                policy "test"
                permit
                where
                  """;
        var position    = new Position(7, 2);
        var completions = getCompletions(document, position);

        var labels = completionLabels(completions);
        assertThat(labels).contains("subject.userId").contains("action.verb").contains("resource.path")
                .contains("environment.time");
    }

    private List<CompletionItem> getCompletions(String content, Position position) {
        var document      = new SAPLParsedDocument("file:///test.sapl", content);
        var configManager = new ConfigurationManager();
        return provider.provideCompletions(document, position, configManager);
    }

    private List<String> completionLabels(List<CompletionItem> completions) {
        return completions.stream().map(CompletionItem::getLabel).toList();
    }

}
