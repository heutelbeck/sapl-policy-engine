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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;

/**
 * Integration tests for import statement completions.
 * These tests validate that completion works correctly when typing import
 * statements.
 */
class ImportCompletionIntegrationTests {

    private SAPLCompletionProvider completionProvider;
    private ConfigurationManager   configManager;

    @BeforeEach
    void setup() {
        completionProvider = new SAPLCompletionProvider();
        configManager      = createConfigManager();
    }

    @Test
    void whenCursorAfterImportKeyword_thenLibrariesAndFunctionsOffered() {
        var document = "import ";
        var position = new Position(0, document.length());

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        // Should offer library names and fully qualified function names
        assertThat(labels).contains("time", "time.now", "time.dayOfWeek");
    }

    @Test
    void whenCursorTypingLibraryName_thenMatchingLibrariesOffered() {
        var document = "import ti";
        var position = new Position(0, document.length());

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        // Should still offer all import completions (LSP client does filtering)
        assertThat(labels).contains("time", "time.now");
    }

    @Test
    void whenCursorAfterLibraryDot_thenFunctionNamesOffered() {
        var document = "import time.";
        var position = new Position(0, document.length());

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        // Should offer fully qualified function names
        assertThat(labels).contains("time.now", "time.dayOfWeek");
    }

    @Test
    void whenMultipleImports_thenSubsequentImportGetsCompletions() {
        var document = """
                import time.now
                import """;
        var position = positionAtEnd(document);

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        assertThat(labels).contains("time", "time.dayOfWeek", "clock");
    }

    @Test
    void whenImportBeforePolicy_thenPartialImportGetsCompletions() {
        var document = """
                import time.now
                import time.d
                policy "test" permit""";
        var position = new Position(1, "import time.d".length());

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        assertThat(labels).contains("time.dayOfWeek");
    }

    @Test
    void whenPIPsConfigured_thenPIPLibrariesOfferedForImport() {
        var document = "import ";
        var position = new Position(0, document.length());

        var completions = getCompletions(document, position);
        var labels      = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());

        // Should offer PIP library names
        assertThat(labels).contains("auth");
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
        var manager = new ConfigurationManager();
        manager.registerConfiguration("", createConfiguration());
        return manager;
    }

    private static LSPConfiguration createConfiguration() {
        // Function library: time
        var timeNow   = new EntryDocumentation(EntryType.FUNCTION, "now", "Returns current time", null, List.of());
        var dayOfWeek = new EntryDocumentation(EntryType.FUNCTION, "dayOfWeek", "Returns day of week", null, List.of());
        var timeLib   = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "time", "Time functions", "Docs",
                List.of(timeNow, dayOfWeek));

        // Function library: clock
        var clockNow = new EntryDocumentation(EntryType.FUNCTION, "now", "Returns current clock", null, List.of());
        var clockLib = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "clock", "Clock functions", "Docs",
                List.of(clockNow));

        // PIP: auth
        var authUser = new EntryDocumentation(EntryType.ATTRIBUTE, "user", "Get user", null, List.of());
        var authPip  = new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, "auth", "Auth PIP", "Docs",
                List.of(authUser));

        var bundle = new DocumentationBundle(List.of(timeLib, clockLib, authPip));
        return new LSPConfiguration("", bundle, Map.of(), null, null);
    }

}
