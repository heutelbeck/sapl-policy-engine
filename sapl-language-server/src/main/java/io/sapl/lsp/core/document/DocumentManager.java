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
package io.sapl.lsp.core.document;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.sapl.lsp.core.GrammarRegistry;
import io.sapl.lsp.core.GrammarSupport;
import io.sapl.lsp.core.ParsedDocument;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages open documents and their parsed representations.
 * Thread-safe for concurrent access from multiple LSP requests.
 * Supports multiple grammar types through GrammarRegistry.
 */
@Slf4j
public class DocumentManager {

    private final ConcurrentMap<String, ParsedDocument> documents = new ConcurrentHashMap<>();
    /**
     * -- GETTER --
     * Gets the grammar registry.
     *
     * @return the grammar registry
     */
    @Getter
    private final GrammarRegistry                       grammarRegistry;

    /**
     * Creates a new document manager with a grammar registry.
     *
     * @param grammarRegistry the grammar registry for multi-grammar support
     */
    public DocumentManager(GrammarRegistry grammarRegistry) {
        this.grammarRegistry = grammarRegistry;
    }

    /**
     * Opens a document and parses its content.
     *
     * @param uri the document URI
     * @param content the document content
     */
    public void openDocument(String uri, String content) {
        var grammarSupport = grammarRegistry.getGrammarForUri(uri);
        var document       = grammarSupport.parseDocument(uri, content);
        documents.put(uri, document);
        log.info("Document opened: {} -> grammar '{}' (errors: {})", uri, grammarSupport.getGrammarId(),
                document.hasErrors());
    }

    /**
     * Updates a document with new content and re-parses.
     *
     * @param uri the document URI
     * @param content the new document content
     */
    public void updateDocument(String uri, String content) {
        var grammarSupport = grammarRegistry.getGrammarForUri(uri);
        var document       = grammarSupport.parseDocument(uri, content);
        documents.put(uri, document);
        log.debug("Document updated and re-parsed: {} with grammar '{}' (errors: {})", uri,
                grammarSupport.getGrammarId(), document.hasErrors());
    }

    /**
     * Closes a document and removes it from tracking.
     *
     * @param uri the document URI
     */
    public void closeDocument(String uri) {
        documents.remove(uri);
        log.debug("Document closed: {}", uri);
    }

    /**
     * Gets a parsed document by URI.
     *
     * @param uri the document URI
     * @return the parsed document, or null if not open
     */
    public ParsedDocument getDocument(String uri) {
        return documents.get(uri);
    }

    /**
     * Checks if a document is open.
     *
     * @param uri the document URI
     * @return true if the document is open
     */
    public boolean isOpen(String uri) {
        return documents.containsKey(uri);
    }

    /**
     * Gets the grammar support for a document URI.
     *
     * @param uri the document URI
     * @return the grammar support for the file extension
     */
    public GrammarSupport getGrammarSupportForUri(String uri) {
        return grammarRegistry.getGrammarForUri(uri);
    }

}
