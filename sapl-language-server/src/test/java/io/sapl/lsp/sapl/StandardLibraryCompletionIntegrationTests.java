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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Integration tests verifying that standard library functions and PIPs
 * are available in completions.
 */
class StandardLibraryCompletionIntegrationTests {

    private static SAPLCompletionProvider completionProvider;
    private static ConfigurationManager   configManager;

    @BeforeAll
    static void setup() {
        completionProvider = new SAPLCompletionProvider();
        // Default ConfigurationManager uses DefaultConfigurationProvider which loads
        // standard libraries
        configManager = new ConfigurationManager();
    }

    static Stream<Arguments> standardFunctionLibraryTestCases() {
        return Stream.of(arguments("standard library - length", "standard.length"),
                arguments("standard library - toString", "standard.toString"),
                arguments("standard library - onErrorMap", "standard.onErrorMap"),
                arguments("string library - concat", "string.concat"),
                arguments("string library - substring", "string.substring"),
                arguments("string library - toUpperCase", "string.toUpperCase"),
                arguments("string library - toLowerCase", "string.toLowerCase"),
                arguments("string library - contains", "string.contains"),
                arguments("array library - size", "array.size"),
                arguments("array library - containsAll", "array.containsAll"),
                arguments("array library - head", "array.head"), arguments("array library - last", "array.last"),
                arguments("math library - max", "math.max"), arguments("math library - min", "math.min"),
                arguments("filter library - blacken", "filter.blacken"),
                arguments("filter library - remove", "filter.remove"),
                arguments("filter library - replace", "filter.replace"),
                arguments("time library - dayOfWeek", "time.dayOfWeek"),
                arguments("time library - dateOf", "time.dateOf"),
                arguments("object library - hasKey", "object.hasKey"),
                arguments("encoding library - base64Encode", "encoding.base64Encode"),
                arguments("encoding library - base64Decode", "encoding.base64Decode"),
                arguments("json library - jsonToVal", "json.jsonToVal"),
                arguments("patterns library - isValidRegex", "patterns.isValidRegex"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("standardFunctionLibraryTestCases")
    void whenInExpressionContext_thenStandardFunctionOffered(String description, String expectedFunction) {
        var document = "policy \"test\" permit where ";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = extractLabels(completions);

        assertThat(labels).contains(expectedFunction);
    }

    static Stream<Arguments> pipAttributeTestCases() {
        return Stream.of(arguments("time PIP - now", "<time.now>"),
                arguments("time PIP - systemTimeZone", "<time.systemTimeZone>"),
                arguments("time PIP - nowIsAfter", "<time.nowIsAfter>"),
                arguments("time PIP - nowIsBefore", "<time.nowIsBefore>"),
                arguments("time PIP - nowIsBetween", "<time.nowIsBetween>"),
                arguments("time PIP - toggle", "<time.toggle>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pipAttributeTestCases")
    void whenInAttributeContext_thenPIPAttributeOffered(String description, String expectedAttribute) {
        var document = "policy \"test\" permit where |<";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = extractLabels(completions);

        assertThat(labels).contains(expectedAttribute);
    }

    @Test
    void whenInExpressionContext_thenAllMajorLibrariesRepresented() {
        var document = "policy \"test\" permit where ";
        var position = positionAtEnd(document);

        var completions     = getCompletions(document, position);
        var labels          = extractLabels(completions);
        var libraryPrefixes = labels.stream().filter(label -> label.contains("."))
                .map(label -> label.substring(0, label.indexOf('.'))).collect(Collectors.toSet());

        assertThat(libraryPrefixes).containsAll(List.of("standard", "string", "array", "object", "math", "time",
                "filter", "encoding", "json", "patterns", "digest"));
    }

    @Test
    void whenInImportContext_thenLibraryNamesOffered() {
        var document = "import ";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = extractLabels(completions);

        assertThat(labels).containsAll(List.of("standard", "string", "array", "object", "math", "time", "filter"));
    }

    @Test
    void whenInExpressionContext_thenStringLibraryHasManyFunctions() {
        var config        = LSPConfiguration.minimal();
        var stringLibrary = config.documentationBundle().functionLibraries().stream()
                .filter(lib -> "string".equals(lib.name())).findFirst();

        assertThat(stringLibrary).isPresent();
        assertThat(stringLibrary.get().entries()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void whenCompletionsRequested_thenFunctionDocumentationIncluded() {
        var document = "policy \"test\" permit where ";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var toString    = completions.stream().filter(item -> "standard.toString".equals(item.getLabel())).findFirst();

        assertThat(toString).isPresent();
        assertThat(toString.get().getDocumentation()).isNotNull();
    }

    @Test
    void whenCompletionsRequested_thenSnippetsProvided() {
        var document = "policy \"test\" permit where ";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var withParams  = completions.stream().filter(item -> item.getLabel().startsWith("string."))
                .filter(item -> item.getInsertText() != null && item.getInsertText().contains("$")).findFirst();

        assertThat(withParams).isPresent();
    }

    @Test
    void whenTypingQualifiedPrefix_thenCompletionsFilteredByPrefix() {
        var document = "policy \"test\" permit where time.b";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = extractLabels(completions);

        // Should include time.* functions starting with 'b'
        assertThat(labels).contains("time.before", "time.between")
        // Should NOT include unrelated libraries
            .doesNotContain("bitwise.setBit")
            .doesNotContain("string.before");
    }

    @Test
    void whenTypingLibraryPrefix_thenOnlyMatchingLibrariesShown() {
        var document = "policy \"test\" permit where ti";
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = extractLabels(completions);

        // Should include time.* functions
        assertThat(labels.stream().anyMatch(label -> label.startsWith("time."))).isTrue();
        // Should NOT include string.* or other non-matching libraries
        assertThat(labels.stream().noneMatch(label -> label.startsWith("string."))).isTrue();
        assertThat(labels.stream().noneMatch(label -> label.startsWith("array."))).isTrue();
    }

    private List<CompletionItem> getCompletions(String content, Position position) {
        var parsedDocument = new SAPLParsedDocument("test://test.sapl", content);
        return completionProvider.provideCompletions(parsedDocument, position, configManager);
    }

    private static Set<String> extractLabels(List<CompletionItem> completions) {
        return completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
    }

    private static Position positionAtEnd(String document) {
        var lines = document.split("\n", -1);
        var line  = lines.length - 1;
        var col   = lines[line].length();
        return new Position(line, col);
    }

}
