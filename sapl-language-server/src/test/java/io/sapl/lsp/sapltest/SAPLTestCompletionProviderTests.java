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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;

@DisplayName("SAPLTestCompletionProvider")
class SAPLTestCompletionProviderTests {

    private static SAPLTestCompletionProvider completionProvider;
    private static ConfigurationManager       configManager;

    @BeforeAll
    static void setup() {
        completionProvider = new SAPLTestCompletionProvider();
        configManager      = new ConfigurationManager();
    }

    @Test
    @DisplayName("decision keywords include suspend")
    void whenCompletionsRequested_thenSuspendDecisionOffered() {
        var labels = labels();

        assertThat(labels).contains("permit", "deny", "suspend", "indeterminate", "not-applicable");
    }

    @Test
    @DisplayName("combining algorithm keywords are SAPL 4.0 phrases")
    void whenCompletionsRequested_thenSapl4AlgorithmsOffered() {
        var labels = labels();

        assertThat(labels).contains("first", "priority deny", "priority permit", "priority suspend", "unanimous",
                "unanimous strict", "unique");
    }

    @Test
    @DisplayName("SAPL 3.0 dead algorithm names are not offered")
    void whenCompletionsRequested_thenSapl3AlgorithmsAbsent() {
        var labels = labels();

        assertThat(labels).isNotEmpty().doesNotContain("deny-overrides", "permit-overrides", "first-applicable",
                "only-one-applicable", "deny-unless-permit", "permit-unless-deny");
    }

    private static Set<String> labels() {
        var document = new SAPLTestParsedDocument("test://test.sapltest", "");
        var position = new Position(0, 0);
        return extractLabels(completionProvider.provideCompletions(document, position, configManager));
    }

    private static Set<String> extractLabels(List<CompletionItem> completions) {
        return completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
    }

}
