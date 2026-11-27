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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.lsp.sapl.ProtocolText;
import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("SAPL language server")
class SAPLLanguageServerTests {

    private static final long TIMEOUT_SECONDS = 10;

    private static final String URI = "file:///policies/test.sapl";

    private static final String COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION = "policy \"p\" permit action = \"read\";";

    private static final Range WHOLE_LINE = new Range(new Position(0, 0),
            new Position(0, COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.length()));

    @Mock
    private LanguageClient client;

    @Captor
    private ArgumentCaptor<PublishDiagnosticsParams> publications;

    private SAPLLanguageServer server;

    @BeforeEach
    void setUp() {
        server = new SAPLLanguageServer();
        server.connect(client);
    }

    @Test
    @DisplayName("announces full document synchronization, semantic tokens, completion and its name")
    void whenInitializedThenTheServerAnnouncesItsCapabilitiesAndItsName() throws Exception {
        val result = initialized();

        assertThat(result).satisfies(initialized -> {
            assertThat(initialized.getCapabilities().getTextDocumentSync().getLeft())
                    .isEqualTo(TextDocumentSyncKind.Full);
            assertThat(initialized.getCapabilities().getSemanticTokensProvider()).isNotNull();
            assertThat(initialized.getCapabilities().getCompletionProvider()).isNotNull();
            assertThat(initialized.getServerInfo().getName()).contains("SAPL");
        });
    }

    @Test
    @DisplayName("announces that it offers quick fixes as code actions")
    void whenInitializedThenTheServerAnnouncesQuickFixesAsCodeActions() throws Exception {
        val codeActionProvider = initialized().getCapabilities().getCodeActionProvider();

        assertThat(codeActionProvider.getRight().getCodeActionKinds()).containsExactly(CodeActionKind.QuickFix);
    }

    @Test
    @DisplayName("answers the shutdown request without a result, and refuses every request after it as invalid")
    void whenShutDownThenNoResultIsReturnedAndEveryLaterRequestIsRefusedAsInvalid() throws Exception {
        initialized();
        open(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);

        val result        = server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        val laterRequests = List.of(server.getTextDocumentService().codeAction(codeActionParams(WHOLE_LINE)),
                server.shutdown(), server.initialize(new InitializeParams()));

        assertThat(result).isNull();
        assertThat(laterRequests).allSatisfy(answer -> assertThat(answer).failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class).havingCause().isInstanceOfSatisfying(
                        ResponseErrorException.class, refusal -> assertThat(refusal.getResponseError().getCode())
                                .isEqualTo(ResponseErrorCode.InvalidRequest.getValue())));
    }

    @Test
    @DisplayName("ignores a document opened after shutdown, as it ignores every notification but exit")
    void whenDocumentIsOpenedAfterShutdownThenNothingIsPublished() throws Exception {
        initialized();
        server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        open(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);

        verify(client, never()).publishDiagnostics(any());
    }

    @Test
    @DisplayName("publishes a comparison written with '=' as the problem of the catalogue at the '='")
    void whenDocumentWithAComparisonWrittenWithTheSignOfADefinitionIsOpenedThenTheProblemIsPublishedAtTheSign()
            throws Exception {
        initialized();

        open(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);

        assertThat(lastPublication()).satisfies(publication -> {
            assertThat(publication.getUri()).isEqualTo(URI);
            assertThat(publication.getDiagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.getCode().getLeft()).isEqualTo("assignment-instead-of-comparison");
                assertThat(ProtocolText.textOf(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION, diagnostic.getRange()))
                        .isEqualTo("=");
            });
        });
    }

    @Test
    @DisplayName("answers a request for code actions with the quick fix, whose edit leaves a document without problems")
    void whenQuickFixIsRequestedAndAppliedThenTheDocumentHasNoProblem() throws Exception {
        initialized();
        open(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);
        val published = lastPublication().getDiagnostics();
        assertThat(published).hasSize(1);

        val codeActions = codeActionsFor(published.getFirst().getRange());

        assertThat(codeActions).singleElement().extracting(Either::getRight).satisfies(quickFix -> {
            assertThat(quickFix.getTitle()).isEqualTo("Replace with '=='");
            assertThat(quickFix.getDiagnostics()).isEqualTo(published);
        });
        change(ProtocolText.withEditsApplied(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION,
                codeActions.getFirst().getRight().getEdit().getChanges().get(URI)));
        assertThat(lastPublication().getDiagnostics()).isEmpty();
    }

    @Test
    @DisplayName("answers a request for code actions for a document it does not know with none")
    void whenCodeActionsAreRequestedForAnUnknownDocumentThenNoneAreOffered() throws Exception {
        initialized();

        val codeActions = codeActionsFor(new Range(new Position(0, 0), new Position(0, 0)));

        assertThat(codeActions).isEmpty();
    }

    private InitializeResult initialized() throws Exception {
        return server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void open(String content) {
        val document = new TextDocumentItem(URI, "sapl", 1, content);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document));
    }

    private void change(String content) {
        val document = new VersionedTextDocumentIdentifier(URI, 2);
        val changes  = List.of(new TextDocumentContentChangeEvent(content));
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(document, changes));
    }

    private List<Either<Command, CodeAction>> codeActionsFor(Range range) throws Exception {
        return server.getTextDocumentService().codeAction(codeActionParams(range)).get(TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    private static CodeActionParams codeActionParams(Range range) {
        return new CodeActionParams(new TextDocumentIdentifier(URI), range, new CodeActionContext(List.of()));
    }

    private PublishDiagnosticsParams lastPublication() {
        verify(client, atLeastOnce()).publishDiagnostics(publications.capture());
        return publications.getValue();
    }

}
