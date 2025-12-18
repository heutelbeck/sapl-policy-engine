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
package io.sapl.lsp.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.core.GrammarRegistry;
import io.sapl.lsp.core.document.DocumentManager;
import io.sapl.lsp.sapl.SAPLGrammarSupport;
import io.sapl.lsp.sapltest.SAPLTestGrammarSupport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * SAPL Language Server implementation based on ANTLR.
 * Provides LSP support for SAPL (.sapl) and SAPLTest (.sapltest) documents.
 */
@Slf4j
public class SAPLLanguageServer implements LanguageServer, LanguageClientAware {

    private static final String SERVER_NAME    = "SAPL Language Server (ANTLR)";
    private static final String SERVER_VERSION = "4.0.0-SNAPSHOT";

    @Getter
    private LanguageClient client;

    private final GrammarRegistry         grammarRegistry;
    private final DocumentManager         documentManager;
    private final ConfigurationManager    configurationManager;
    private final SAPLTextDocumentService textDocumentService;
    private final SAPLWorkspaceService    workspaceService;

    /**
     * Creates a new SAPL Language Server instance with support for both SAPL and
     * SAPLTest grammars.
     */
    public SAPLLanguageServer() {
        this.grammarRegistry = new GrammarRegistry();

        // Register SAPL grammar support
        var saplGrammar = new SAPLGrammarSupport();
        grammarRegistry.register(saplGrammar);
        grammarRegistry.setDefaultGrammar(saplGrammar.getGrammarId());

        // Register SAPLTest grammar support
        var saplTestGrammar = new SAPLTestGrammarSupport();
        grammarRegistry.register(saplTestGrammar);

        log.info("Registered grammars: {} (SAPL: {}, SAPLTest: {})", grammarRegistry.getAllFileExtensions(),
                saplGrammar.getFileExtensions(), saplTestGrammar.getFileExtensions());

        this.documentManager      = new DocumentManager(grammarRegistry);
        this.configurationManager = new ConfigurationManager();

        // Services must be created in constructor for LSP4J method discovery.
        // They use a reference to this server to get the client lazily.
        this.textDocumentService = new SAPLTextDocumentService(this, documentManager, configurationManager);
        this.workspaceService    = new SAPLWorkspaceService();
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        log.info("Language client connected");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        log.info("Initializing {} v{}", SERVER_NAME, SERVER_VERSION);

        var capabilities = createServerCapabilities();
        var serverInfo   = new ServerInfo(SERVER_NAME, SERVER_VERSION);
        var result       = new InitializeResult(capabilities, serverInfo);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        log.info("Shutting down {}", SERVER_NAME);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        log.info("Exiting {}", SERVER_NAME);
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    /**
     * Creates the server capabilities to advertise to the client.
     *
     * @return configured server capabilities
     */
    private ServerCapabilities createServerCapabilities() {
        var capabilities = new ServerCapabilities();

        // Document synchronization - full sync for simplicity
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        // Semantic tokens for syntax highlighting
        capabilities.setSemanticTokensProvider(createSemanticTokensOptions());

        // Completion support
        var completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(grammarRegistry.getAllCompletionTriggerCharacters());
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);

        return capabilities;
    }

    /**
     * Creates semantic tokens options with the legend for token types.
     *
     * @return configured semantic tokens options
     */
    private SemanticTokensWithRegistrationOptions createSemanticTokensOptions() {
        var options = new SemanticTokensWithRegistrationOptions();
        options.setFull(true);
        options.setRange(false);
        options.setLegend(grammarRegistry.getCombinedSemanticTokensLegend());
        return options;
    }

    /**
     * Gets the document manager for accessing parsed documents.
     *
     * @return the document manager
     */
    public DocumentManager getDocumentManager() {
        return documentManager;
    }

    /**
     * Gets the configuration manager for accessing PDP configurations.
     *
     * @return the configuration manager
     */
    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    /**
     * Gets the grammar registry for accessing grammar support implementations.
     *
     * @return the grammar registry
     */
    public GrammarRegistry getGrammarRegistry() {
        return grammarRegistry;
    }

}
