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
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;

@DisplayName("SAPLCompletionProvider")
class SAPLCompletionProviderTests {

    private static SAPLCompletionProvider completionProvider;
    private static ConfigurationManager   configManager;

    @BeforeAll
    static void setup() {
        completionProvider = new SAPLCompletionProvider();
        configManager      = new ConfigurationManager();
    }

    @Test
    @DisplayName("EFFECT context offers permit, deny, suspend")
    void whenAfterPolicyName_thenAllEffectKeywordsOffered() {
        var labels = labelsAtEnd("policy \"test\" ");

        assertThat(labels).contains("permit", "deny", "suspend");
    }

    @Test
    @DisplayName("COMBINING_ALGORITHM context offers SAPL 4.0 phrases")
    void whenAfterPolicySetName_thenSapl4AlgorithmsOffered() {
        var labels = labelsAtEnd("set \"test\" ");

        assertThat(labels).contains("first", "priority deny", "priority permit", "priority suspend", "unanimous",
                "unanimous strict", "unique");
    }

    @Test
    @DisplayName("COMBINING_ALGORITHM context does not offer SAPL 3.0 dead names")
    void whenAfterPolicySetName_thenSapl3AlgorithmsAbsent() {
        var labels = labelsAtEnd("set \"test\" ");

        assertThat(labels).doesNotContain("deny-overrides", "permit-overrides", "first-applicable",
                "only-one-applicable", "deny-unless-permit", "permit-unless-deny");
    }

    @Test
    @DisplayName("POLICY_AFTER_EFFECT triggers after suspend like permit and deny")
    void whenAfterSuspend_thenStructureKeywordsOffered() {
        var labels = labelsAtEnd("policy \"test\" suspend ");

        assertThat(labels).contains("obligation", "advice", "transform");
    }

    private static Set<String> labelsAtEnd(String content) {
        var parsedDocument = new SAPLParsedDocument("test://test.sapl", content);
        var position       = positionAtEnd(content);
        return extractLabels(completionProvider.provideCompletions(parsedDocument, position, configManager));
    }

    private static Position positionAtEnd(String document) {
        var lines = document.split("\n", -1);
        var line  = lines.length - 1;
        var col   = lines[line].length();
        return new Position(line, col);
    }

    private static Set<String> extractLabels(List<CompletionItem> completions) {
        return completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
    }

}
