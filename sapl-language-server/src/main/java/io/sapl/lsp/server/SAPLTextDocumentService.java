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

import static io.sapl.lsp.server.SAPLLanguageServer.refusedAfterShutdown;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
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
import io.sapl.lsp.core.GrammarSupport;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.document.DocumentManager;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles text document operations for the Language Server. Provides
 * validation, semantic tokens, completion and quick fixes. Supports multiple
 * grammars (SAPL, SAPLTest) through DocumentManager routing.
 * <p>
 * Provider NPE containment is a deliberate design decision. Providers run on
 * the raw, error-recovered parse tree of an in-progress document, where
 * children can be null while the author is still typing. Rather than
 * null-guard every provider exhaustively (notably the large formatting
 * visitor), faults are contained centrally: notification handlers via
 * {@link #safely}, request handlers via their {@link CompletableFuture} (a
 * fault becomes a failed response, the server stays up). The small providers
 * still guard their common name-token sites locally for graceful partial
 * results.
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
        val uri     = params.getTextDocument().getUri();
        val content = params.getTextDocument().getText();
        safely("didOpen", uri, () -> {
            log.debug("Document opened: {}", uri);
            documentManager.openDocument(uri, content);
            validateAndPublishDiagnostics(uri);
        });
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        val uri = params.getTextDocument().getUri();
        // We use full sync, so take the full content from the first change
        if (params.getContentChanges().isEmpty()) {
            return;
        }
        val content = params.getContentChanges().getFirst().getText();
        safely("didChange", uri, () -> {
            log.debug("Document changed: {}", uri);
            documentManager.updateDocument(uri, content);
            validateAndPublishDiagnostics(uri);
        });
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        val uri = params.getTextDocument().getUri();
        safely("didClose", uri, () -> {
            log.debug("Document closed: {}", uri);
            documentManager.closeDocument(uri);

            // Clear diagnostics for closed document
            val client = server.getClient();
            if (client != null) {
                client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
            }
        });
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        val uri = params.getTextDocument().getUri();
        safely("didSave", uri, () -> {
            log.debug("Document saved: {}", uri);
            // Re-validate on save
            validateAndPublishDiagnostics(uri);
        });
    }

    /**
     * Exception barrier for the document notification handlers (see the class-level
     * note on provider NPE containment). A fault while handling one document is
     * logged and contained so the server keeps serving others. After shutdown the
     * protocol allows no notification but exit, so a document notification is
     * ignored.
     */
    private void safely(String operation, String uri, Runnable action) {
        if (server.isShutDown()) {
            log.debug("Ignored {} for {} after shutdown", operation, uri);
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            log.error("Unhandled error during {} for {}", operation, uri, e);
        }
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        val uri = params.getTextDocument().getUri();
        log.debug("Semantic tokens requested for: {}", uri);

        return answered(uri, () -> new SemanticTokens(List.of()),
                (document, grammarSupport) -> grammarSupport.provideSemanticTokens(document));
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        val uri = params.getTextDocument().getUri();
        log.debug("Formatting requested for: {}", uri);

        return answered(uri, List::of, (document, grammarSupport) -> grammarSupport.provideFormatting(document));
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        val uri = params.getTextDocument().getUri();
        log.debug("Document symbols requested for: {}", uri);

        return answered(uri, List::of, (document, grammarSupport) -> grammarSupport.provideDocumentSymbols(document)
                .stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).toList());
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        val uri = params.getTextDocument().getUri();
        log.debug("Folding ranges requested for: {}", uri);

        return answered(uri, List::of, (document, grammarSupport) -> grammarSupport.provideFoldingRanges(document));
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        val uri = params.getTextDocument().getUri();
        log.debug("Selection ranges requested for: {}", uri);

        return answered(uri, List::of,
                (document, grammarSupport) -> grammarSupport.provideSelectionRanges(document, params.getPositions()));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        val uri      = params.getTextDocument().getUri();
        val position = params.getPosition();
        log.debug("Hover requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return answered(uri, () -> null,
                (document, grammarSupport) -> grammarSupport.provideHover(document, position, configurationManager));
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        val uri      = params.getTextDocument().getUri();
        val position = params.getPosition();
        log.debug("Prepare rename requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return answered(uri, () -> null, (document, grammarSupport) -> {
            val result = grammarSupport.prepareRename(document, position);
            if (result == null) {
                return null;
            }
            return Either3.forSecond(result);
        });
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        val uri      = params.getTextDocument().getUri();
        val position = params.getPosition();
        val newName  = params.getNewName();
        log.debug("Rename requested at {}:{} in {} to '{}'", position.getLine(), position.getCharacter(), uri, newName);

        return answered(uri, () -> null,
                (document, grammarSupport) -> grammarSupport.provideRename(document, position, newName));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        val uri      = params.getTextDocument().getUri();
        val position = params.getPosition();

        log.debug("Completion requested at {}:{} in {}", position.getLine(), position.getCharacter(), uri);

        return answered(uri, () -> {
            log.warn("Completion: document not found for URI: {}", uri);
            return Either.forLeft(List.of());
        }, (document, grammarSupport) -> {
            val items = grammarSupport.provideCompletions(document, position, configurationManager);
            log.debug("Completion: returning {} items", items.size());
            return Either.forLeft(items);
        });
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        val uri   = params.getTextDocument().getUri();
        val range = params.getRange();
        log.debug("Code actions requested in {}", uri);

        return answered(uri, List::of, (document, grammarSupport) -> grammarSupport.provideCodeActions(document, range)
                .stream().map(Either::<Command, CodeAction>forRight).toList());
    }

    /**
     * Answers a request about a document with the support of its grammar, apart
     * from the thread of the request. A fault of a provider becomes a failed
     * response (see the class-level note on provider NPE containment). After
     * shutdown the request is refused.
     *
     * @param uri the URI of the document
     * @param unknown the answer if no document is open under the URI
     * @param answer the answer for the document and the support of its grammar
     * @return the answer
     */
    private <T> CompletableFuture<T> answered(String uri, Supplier<T> unknown,
            BiFunction<ParsedDocument, GrammarSupport, T> answer) {
        if (server.isShutDown()) {
            return refusedAfterShutdown();
        }
        return CompletableFuture.supplyAsync(() -> {
            val document = documentManager.getDocument(uri);
            if (document == null) {
                return unknown.get();
            }
            return answer.apply(document, documentManager.getGrammarSupportForUri(uri));
        });
    }

    /**
     * Validates the document and publishes diagnostics to the client.
     *
     * @param uri the document URI
     */
    private void validateAndPublishDiagnostics(String uri) {
        val client = server.getClient();
        if (client == null) {
            return;
        }

        val document = documentManager.getDocument(uri);
        if (document == null) {
            return;
        }

        val grammarSupport = documentManager.getGrammarSupportForUri(uri);
        val diagnostics    = grammarSupport.provideDiagnostics(document);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

}
