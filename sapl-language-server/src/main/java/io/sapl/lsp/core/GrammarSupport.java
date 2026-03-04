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
package io.sapl.lsp.core;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

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

    /**
     * Provides formatting text edits for a parsed document.
     * Returns an empty list if the document has parse errors.
     *
     * @param document the parsed document
     * @return list of text edits to apply for formatting
     */
    List<TextEdit> provideFormatting(ParsedDocument document);

    /**
     * Provides document symbols for the outline view.
     *
     * @param document the parsed document
     * @return list of document symbols
     */
    List<DocumentSymbol> provideDocumentSymbols(ParsedDocument document);

    /**
     * Provides folding ranges for collapsible regions.
     *
     * @param document the parsed document
     * @return list of folding ranges
     */
    List<FoldingRange> provideFoldingRanges(ParsedDocument document);

    /**
     * Provides selection ranges for smart expand/shrink selection.
     *
     * @param document the parsed document
     * @param positions the cursor positions
     * @return list of selection ranges, one per position
     */
    List<SelectionRange> provideSelectionRanges(ParsedDocument document, List<Position> positions);

    /**
     * Provides hover information for a position in a document.
     *
     * @param document the parsed document
     * @param position the cursor position
     * @param configurationManager the configuration manager for documentation
     * lookup
     * @return hover information, or null if no hover is available
     */
    Hover provideHover(ParsedDocument document, Position position, ConfigurationManager configurationManager);

    /**
     * Prepares a rename operation at the given position.
     *
     * @param document the parsed document
     * @param position the cursor position
     * @return prepare rename result with the range and placeholder, or null if not
     * renamable
     */
    PrepareRenameResult prepareRename(ParsedDocument document, Position position);

    /**
     * Provides rename edits for a variable at the given position.
     *
     * @param document the parsed document
     * @param position the cursor position
     * @param newName the new name for the variable
     * @return workspace edit with all rename changes, or null if not renamable
     */
    WorkspaceEdit provideRename(ParsedDocument document, Position position, String newName);

}
