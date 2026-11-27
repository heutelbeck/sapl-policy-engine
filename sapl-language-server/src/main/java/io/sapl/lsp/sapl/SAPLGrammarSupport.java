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
import io.sapl.lsp.core.GrammarSupport;
import io.sapl.lsp.core.ParsedDocument;

/**
 * Grammar support implementation for SAPL policy language.
 * Provides parsing, highlighting, completion, and validation for SAPL
 * documents.
 */
public class SAPLGrammarSupport implements GrammarSupport {

    private static final String       GRAMMAR_ID      = "sapl";
    private static final List<String> FILE_EXTENSIONS = List.of(".sapl");
    private static final List<String> TRIGGER_CHARS   = List.of(".", "<", "|");

    private final SAPLSemanticTokensProvider semanticTokensProvider;
    private final SAPLCompletionProvider     completionProvider;
    private final SAPLDiagnosticsProvider    diagnosticsProvider;
    private final SAPLFormattingProvider     formattingProvider;
    private final SAPLDocumentSymbolProvider documentSymbolProvider;
    private final SAPLFoldingRangeProvider   foldingRangeProvider;
    private final SAPLSelectionRangeProvider selectionRangeProvider;
    private final SAPLHoverProvider          hoverProvider;
    private final SAPLRenameProvider         renameProvider;

    /**
     * Creates a new SAPL grammar support instance.
     */
    public SAPLGrammarSupport() {
        this.semanticTokensProvider = new SAPLSemanticTokensProvider();
        this.completionProvider     = new SAPLCompletionProvider();
        this.diagnosticsProvider    = new SAPLDiagnosticsProvider();
        this.formattingProvider     = new SAPLFormattingProvider();
        this.documentSymbolProvider = new SAPLDocumentSymbolProvider();
        this.foldingRangeProvider   = new SAPLFoldingRangeProvider();
        this.selectionRangeProvider = new SAPLSelectionRangeProvider();
        this.hoverProvider          = new SAPLHoverProvider();
        this.renameProvider         = new SAPLRenameProvider();
    }

    @Override
    public String getGrammarId() {
        return GRAMMAR_ID;
    }

    @Override
    public List<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public ParsedDocument parseDocument(String uri, String content) {
        return new SAPLParsedDocument(uri, content);
    }

    @Override
    public SemanticTokens provideSemanticTokens(ParsedDocument document) {
        return semanticTokensProvider.provideSemanticTokens(document);
    }

    @Override
    public SemanticTokensLegend getSemanticTokensLegend() {
        return new SemanticTokensLegend(SAPLSemanticTokenTypes.TOKEN_TYPES, SAPLSemanticTokenTypes.TOKEN_MODIFIERS);
    }

    @Override
    public List<CompletionItem> provideCompletions(ParsedDocument document, Position position,
            ConfigurationManager configurationManager) {
        return completionProvider.provideCompletions(document, position, configurationManager);
    }

    @Override
    public List<Diagnostic> provideDiagnostics(ParsedDocument document) {
        return diagnosticsProvider.provideDiagnostics(document);
    }

    @Override
    public List<String> getCompletionTriggerCharacters() {
        return TRIGGER_CHARS;
    }

    @Override
    public List<TextEdit> provideFormatting(ParsedDocument document) {
        return formattingProvider.provideFormatting(document);
    }

    @Override
    public List<DocumentSymbol> provideDocumentSymbols(ParsedDocument document) {
        return documentSymbolProvider.provideDocumentSymbols(document);
    }

    @Override
    public List<FoldingRange> provideFoldingRanges(ParsedDocument document) {
        return foldingRangeProvider.provideFoldingRanges(document);
    }

    @Override
    public List<SelectionRange> provideSelectionRanges(ParsedDocument document, List<Position> positions) {
        return selectionRangeProvider.provideSelectionRanges(document, positions);
    }

    @Override
    public Hover provideHover(ParsedDocument document, Position position, ConfigurationManager configurationManager) {
        return hoverProvider.provideHover(document, position, configurationManager);
    }

    @Override
    public PrepareRenameResult prepareRename(ParsedDocument document, Position position) {
        return renameProvider.prepareRename(document, position);
    }

    @Override
    public WorkspaceEdit provideRename(ParsedDocument document, Position position, String newName) {
        return renameProvider.provideRename(document, position, newName);
    }

}
