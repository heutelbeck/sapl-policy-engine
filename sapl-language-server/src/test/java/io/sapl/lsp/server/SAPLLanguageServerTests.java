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

import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SAPLLanguageServerTests {

    private SAPLLanguageServer server;

    @BeforeEach
    void setUp() {
        server = new SAPLLanguageServer();
    }

    @Test
    void whenInitialize_thenReturnsCapabilities() throws ExecutionException, InterruptedException {
        var params = new InitializeParams();
        var result = server.initialize(params).get();

        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isNotNull();
        assertThat(result.getCapabilities().getTextDocumentSync().getLeft()).isEqualTo(TextDocumentSyncKind.Full);
        assertThat(result.getCapabilities().getSemanticTokensProvider()).isNotNull();
        assertThat(result.getCapabilities().getCompletionProvider()).isNotNull();
        assertThat(result.getServerInfo()).isNotNull();
        assertThat(result.getServerInfo().getName()).contains("SAPL");
    }

    @Test
    void whenShutdown_thenReturnsNull() throws ExecutionException, InterruptedException {
        var result = server.shutdown().get();
        assertThat(result).isNull();
    }

    @Test
    void whenInitialize_thenServicesAreAvailable() throws ExecutionException, InterruptedException {
        var params = new InitializeParams();
        server.initialize(params).get();

        assertThat(server.getTextDocumentService()).isNotNull();
        assertThat(server.getWorkspaceService()).isNotNull();
        assertThat(server.getDocumentManager()).isNotNull();
        assertThat(server.getConfigurationManager()).isNotNull();
        assertThat(server.getGrammarRegistry()).isNotNull();
    }

}
