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
package io.sapl.lsp.core;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import io.sapl.lsp.configuration.ConfigurationManager;

/**
 * Interface for grammar-specific language support operations.
 * Implementations provide parsing, highlighting, completion, and validation
 * for a specific grammar (SAPL, SAPLTest, etc.).
 */
public interface GrammarSupport {

    /**
     * Gets the grammar identifier.
     *
     * @return the grammar name (e.g., "sapl", "sapltest")
     */
    String getGrammarId();

    /**
     * Gets the file extensions supported by this grammar.
     *
     * @return list of file extensions (e.g., ".sapl", ".sapltest")
     */
    List<String> getFileExtensions();

    /**
     * Parses a document and returns a parsed representation.
     *
     * @param uri the document URI
     * @param content the document content
     * @return the parsed document
     */
    ParsedDocument parseDocument(String uri, String content);

    /**
     * Provides semantic tokens for syntax highlighting.
     *
     * @param document the parsed document
     * @return the semantic tokens
     */
    SemanticTokens provideSemanticTokens(ParsedDocument document);

    /**
     * Gets the semantic tokens legend for this grammar.
     *
     * @return the semantic tokens legend
     */
    SemanticTokensLegend getSemanticTokensLegend();

    /**
     * Provides completion items for a position in a document.
     *
     * @param document the parsed document
     * @param position the cursor position
     * @param configurationManager the configuration manager for context
     * @return list of completion items
     */
    List<CompletionItem> provideCompletions(ParsedDocument document, Position position,
            ConfigurationManager configurationManager);

    /**
     * Provides diagnostics for a parsed document.
     *
     * @param document the parsed document
     * @return list of diagnostics
     */
    List<Diagnostic> provideDiagnostics(ParsedDocument document);

    /**
     * Gets the completion trigger characters for this grammar.
     *
     * @return list of trigger characters
     */
    List<String> getCompletionTriggerCharacters();

}
