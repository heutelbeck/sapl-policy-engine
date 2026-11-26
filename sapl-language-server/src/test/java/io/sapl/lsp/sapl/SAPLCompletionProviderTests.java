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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.core.ProtocolPositions;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SAPLCompletionProvider")
class SAPLCompletionProviderTests {

    @MethodSource("finderCursors")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Finder completions distinguish environment and receiver access")
    void whenCompletingFinderThenOnlyItsReceiverKindIsOffered(String marked, String expected) {
        var library  = new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, "office", "", "",
                List.of(new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, "environment", "", null, List.of()),
                        new EntryDocumentation(EntryType.ATTRIBUTE, "receiver", "", null, List.of())));
        var config   = new LSPConfiguration("audit", new DocumentationBundle(List.of(library)), Map.of(),
                LSPConfiguration.minimal().functionBroker());
        var manager  = new ConfigurationManager(ignored -> config);
        var offset   = marked.indexOf('§');
        var text     = marked.replace("§", "");
        var document = new SAPLParsedDocument("file:///audit/policy.sapl", text);

        var items = completionProvider.provideCompletions(document, new Position(0, offset), manager);

        assertThat(items).extracting(CompletionItem::getLabel).containsExactly(expected);
    }

    static Stream<Arguments> finderCursors() {
        return Stream.of("<", "|<", "subject.<", "subject.|<")
                .flatMap(opening -> Stream.of("§", "off§ice", "office.§", "office.re§ceiver")
                        .map(name -> arguments("policy \"p\" permit " + opening + name + ">;",
                                opening.startsWith("subject") ? "<office.receiver>" : "<office.environment>")));
    }

    @MethodSource("variableWordCursors")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Variable completion replaces its remaining word and preserves adjacent tokens")
    void whenCompletingInsideVariableThenEditReplacesRemainingWord(String marked, String name, String expected) {
        var config    = new LSPConfiguration("ordinary", new DocumentationBundle(List.of()), Map.of(),
                new DefaultFunctionBroker());
        var manager   = new ConfigurationManager(ignored -> config);
        var offset    = marked.indexOf('\u00a7');
        var text      = marked.replace("\u00a7", "");
        var positions = ProtocolPositions.of(text);
        var document  = new SAPLParsedDocument("file:///ordinary/policy.sapl", text);

        var items  = completionProvider.provideCompletions(document, positions.positionOf(offset), manager);
        var edit   = items.stream().filter(item -> name.equals(item.getLabel())).findFirst().orElseThrow().getTextEdit()
                .getLeft();
        var edited = text.substring(0, positions.offsetOf(edit.getRange().getStart())) + edit.getNewText()
                + text.substring(positions.offsetOf(edit.getRange().getEnd()));

        assertThat(edited).isEqualTo(expected);
    }

    static Stream<Arguments> variableWordCursors() {
        return Stream.of(
                arguments("policy \"p\" permit sub\u00a7ject == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit sub\u00a7stitute == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit subject\u00a7 == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit sub\u00a7 == true;", "subject", "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit \u00a7subject == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit \u00a7 true;", "subject", "policy \"p\" permit subject true;"),
                arguments("policy \"p\" permit sub\u00a7 next;", "subject", "policy \"p\" permit subject next;"),
                arguments("policy \"p\" permit sub\u00a7.name == true;", "subject",
                        "policy \"p\" permit subject.name == true;"),
                arguments("policy \"p\" permit sub\u00a7+1;", "subject", "policy \"p\" permit subject+1;"),
                arguments("policy \"p\" permit sub\u00a7_old == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit sub\u00a72 == true;", "subject", "policy \"p\" permit subject == true;"),
                arguments("\uFEFFpolicy \"\uD83D\uDE42\" permit sub\u00a7ject == true;", "subject",
                        "\uFEFFpolicy \"\uD83D\uDE42\" permit subject == true;"),
                arguments("policy \"p\" permit\r\nsub\u00a7ject == true;", "subject",
                        "policy \"p\" permit\r\nsubject == true;"),
                arguments("policy \"p\" permit sub\u00a7$rest == true;", "subject",
                        "policy \"p\" permit subject == true;"),
                arguments("policy \"p\" permit var $value=true; $v\u00a7alue;", "$value",
                        "policy \"p\" permit var $value=true; $value;"));
    }

    @MethodSource("configuredPropertyKeys")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Configured property completion reads the original object key")
    void whenSelectingConfiguredPropertyThenItsPathReadsOriginalKey(String key, String suffix) {
        var original   = Value
                .ofObject(Map.of("properties", Value.ofObject(Map.of("originalValue", Value.EMPTY_OBJECT))));
        var nested     = Value.ofObject(Map.of("name",
                Value.ofObject(Map.of("properties", Value.ofObject(Map.of("nestedValue", Value.EMPTY_OBJECT))))));
        var department = new LinkedHashMap<String, Value>();
        department.put("office", nested);
        department.put(key, original);
        var desk      = Value.ofObject(Map.of("department", Value.ofObject(department)));
        var config    = new LSPConfiguration("ordinary", new DocumentationBundle(List.of()), Map.of("desk", desk),
                new DefaultFunctionBroker());
        var manager   = new ConfigurationManager(ignored -> config);
        var text      = "policy \"p\" permit desk.department. == true;";
        var positions = ProtocolPositions.of(text);
        var document  = new SAPLParsedDocument("file:///ordinary/path.sapl", text);
        var path      = "desk.department." + suffix;

        var items = completionProvider.provideCompletions(document, positions.positionOf(text.indexOf(" ==")), manager);
        assertThat(items).extracting(CompletionItem::getLabel).contains(path);
        var edit   = items.stream().filter(item -> path.equals(item.getLabel())).findFirst().orElseThrow().getTextEdit()
                .getLeft();
        var edited = text.substring(0, positions.offsetOf(edit.getRange().getStart())) + edit.getNewText()
                + text.substring(positions.offsetOf(edit.getRange().getEnd()));
        assertThat(edited).isEqualTo("policy \"p\" permit " + path + " == true;");

        var selected         = "resource schema " + path + "\npolicy \"p\" permit resource.";
        var selectedDocument = new SAPLParsedDocument("file:///ordinary/selected.sapl", selected);
        var values           = completionProvider.provideCompletions(selectedDocument, positionAtEnd(selected),
                manager);
        assertThat(values).extracting(CompletionItem::getLabel).containsExactly("resource.originalValue");
    }

    static Stream<Arguments> configuredPropertyKeys() {
        return Stream.of(arguments("office_name", "office_name"), arguments("office", "office"),
                arguments("$office", "$office"), arguments("office name", "'office name'"),
                arguments("office-name", "'office-name'"), arguments("office.name", "'office.name'"),
                arguments("O'Reilly", "'O\\'Reilly'"), arguments("Office \"West\"", "'Office \\\"West\\\"'"),
                arguments("office\\name", "'office\\\\name'"), arguments("B\u00fcro", "'B\u00fcro'"));
    }

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

        assertThat(labels).isNotEmpty().doesNotContain("deny-overrides", "permit-overrides", "first-applicable",
                "only-one-applicable", "deny-unless-permit", "permit-unless-deny");
    }

    @Test
    @DisplayName("POLICY_AFTER_EFFECT triggers after suspend like permit and deny")
    void whenAfterSuspend_thenStructureKeywordsOffered() {
        var labels = labelsAtEnd("policy \"test\" suspend ");

        assertThat(labels).contains("obligation", "advice", "transform");
    }

    @Test
    @DisplayName("offers schema properties inside a policy after an import")
    void whenCompletingSubjectAfterAnImport_thenOffersItsSchemaProperties() {
        var labels = labelsAtEnd("""
                import time.dayOfWeek
                subject schema {"properties":{"role":{}}}
                policy "p" permit subject.ro""");

        assertThat(labels).contains("subject.role").doesNotContain("array");
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
