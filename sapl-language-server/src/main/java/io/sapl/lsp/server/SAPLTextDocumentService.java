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
package io.sapl.lsp.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.core.document.DocumentManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles text document operations for the Language Server.
 * Provides validation, semantic tokens, and completion support.
 * Supports multiple grammars (SAPL, SAPLTest) through DocumentManager routing.
 */
@Slf4j
public class SAPLTextDocumentService implements TextDocumentService {

    private final SAPLLanguageServer   server;
    private final DocumentManager      documentManager;
    private final ConfigurationManager configurationManager;

    /**
     * Creates a new text document service.
     *
     * @param server the language server (for lazy client access)
     * @param documentManager the document manager with grammar registry
     * @param configurationManager the configuration manager
     */
    public SAPLTextDocumentService(SAPLLanguageServer server,
            DocumentManager documentManager,
            ConfigurationManager configurationManager) {
        this.server               = server;
        this.documentManager      = documentManager;
        this.configurationManager = configurationManager;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var uri     = params.getTextDocument().getUri();
        var content = params.getTextDocument().getText();

        log.debug("Document opened: {}", uri);
        documentManager.openDocument(uri, content);
        validateAndPublishDiagnostics(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();

        // We use full sync, so take the full content from the first change
        if (!params.getContentChanges().isEmpty()) {
            var content = params.getContentChanges().getFirst().getText();
            log.debug("Document changed: {}", uri);
            documentManager.updateDocument(uri, content);
            validateAndPublishDiagnostics(uri);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Document closed: {}", uri);
        documentManager.closeDocument(uri);

        // Clear diagnostics for closed document
        var client = server.getClient();
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Document saved: {}", uri);
        // Re-validate on save
        validateAndPublishDiagnostics(uri);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Semantic tokens requested for: {}", uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return new SemanticTokens(List.of());
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideSemanticTokens(document);
        });
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Formatting requested for: {}", uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return List.of();
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideFormatting(document);
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Document symbols requested for: {}", uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return List.of();
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideDocumentSymbols(document).stream()
                    .map(Either::<SymbolInformation, DocumentSymbol>forRight).toList();
        });
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Folding ranges requested for: {}", uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return List.of();
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideFoldingRanges(document);
        });
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        var uri = params.getTextDocument().getUri();
        log.debug("Selection ranges requested for: {}", uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return List.of();
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideSelectionRanges(document, params.getPositions());
        });
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        var uri      = params.getTextDocument().getUri();
        var position = params.getPosition();
        log.debug("Hover requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return null;
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideHover(document, position, configurationManager);
        });
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        var uri      = params.getTextDocument().getUri();
        var position = params.getPosition();
        log.debug("Prepare rename requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return null;
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            var result         = grammarSupport.prepareRename(document, position);
            if (result == null) {
                return null;
            }
            return Either3.forSecond(result);
        });
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        var uri      = params.getTextDocument().getUri();
        var position = params.getPosition();
        var newName  = params.getNewName();
        log.debug("Rename requested at {}:{} in {} to '{}'", position.getLine(), position.getCharacter(), uri, newName);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                return null;
            }
            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            return grammarSupport.provideRename(document, position, newName);
        });
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        var uri      = params.getTextDocument().getUri();
        var position = params.getPosition();

        log.debug("Completion requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return CompletableFuture.supplyAsync(() -> {
            var document = documentManager.getDocument(uri);
            if (document == null) {
                log.warn("Completion: document not found for URI: {}", uri);
                return Either.forLeft(List.of());
            }

            var grammarSupport = documentManager.getGrammarSupportForUri(uri);
            var items          = grammarSupport.provideCompletions(document, position, configurationManager);
            log.debug("Completion: returning {} items", items.size());
            return Either.forLeft(items);
        });
    }

    /**
     * Validates the document and publishes diagnostics to the client.
     *
     * @param uri the document URI
     */
    private void validateAndPublishDiagnostics(String uri) {
        var client = server.getClient();
        if (client == null) {
            return;
        }

        var document = documentManager.getDocument(uri);
        if (document == null) {
            return;
        }

        var grammarSupport = documentManager.getGrammarSupportForUri(uri);
        var diagnostics    = grammarSupport.provideDiagnostics(document);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

}
