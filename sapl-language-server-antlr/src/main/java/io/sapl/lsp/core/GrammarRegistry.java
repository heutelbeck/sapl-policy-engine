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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.SemanticTokensLegend;

import lombok.extern.slf4j.Slf4j;

/**
 * Registry for grammar support implementations.
 * Routes document operations to the appropriate grammar based on file
 * extension.
 */
@Slf4j
public class GrammarRegistry {

    private final Map<String, GrammarSupport> grammarsByExtension = new HashMap<>();
    private final Map<String, GrammarSupport> grammarsById        = new HashMap<>();
    private GrammarSupport                    defaultGrammar;

    /**
     * Registers a grammar support implementation.
     *
     * @param grammarSupport the grammar support to register
     */
    public void register(GrammarSupport grammarSupport) {
        var grammarId = grammarSupport.getGrammarId();
        grammarsById.put(grammarId, grammarSupport);

        for (var extension : grammarSupport.getFileExtensions()) {
            grammarsByExtension.put(extension.toLowerCase(), grammarSupport);
            log.debug("Registered grammar '{}' for extension '{}'", grammarId, extension);
        }

        if (defaultGrammar == null) {
            defaultGrammar = grammarSupport;
        }
    }

    /**
     * Sets the default grammar to use when file extension is unknown.
     *
     * @param grammarId the grammar identifier
     */
    public void setDefaultGrammar(String grammarId) {
        var grammar = grammarsById.get(grammarId);
        if (grammar != null) {
            defaultGrammar = grammar;
        }
    }

    /**
     * Gets the grammar support for a document URI based on file extension.
     *
     * @param uri the document URI
     * @return the grammar support, or default if extension unknown
     */
    public GrammarSupport getGrammarForUri(String uri) {
        var extension = extractExtension(uri);
        return grammarsByExtension.getOrDefault(extension, defaultGrammar);
    }

    /**
     * Gets a grammar support by its identifier.
     *
     * @param grammarId the grammar identifier
     * @return optional containing the grammar support if found
     */
    public Optional<GrammarSupport> getGrammarById(String grammarId) {
        return Optional.ofNullable(grammarsById.get(grammarId));
    }

    /**
     * Gets all registered grammars.
     *
     * @return collection of grammar supports
     */
    public Collection<GrammarSupport> getAllGrammars() {
        return grammarsById.values();
    }

    /**
     * Gets all supported file extensions.
     *
     * @return list of file extensions
     */
    public List<String> getAllFileExtensions() {
        return new ArrayList<>(grammarsByExtension.keySet());
    }

    /**
     * Gets all completion trigger characters from all grammars.
     *
     * @return combined list of trigger characters
     */
    public List<String> getAllCompletionTriggerCharacters() {
        return grammarsById.values().stream().flatMap(g -> g.getCompletionTriggerCharacters().stream()).distinct()
                .toList();
    }

    /**
     * Creates a combined semantic tokens legend from all grammars.
     * For simplicity, uses the default grammar's legend.
     *
     * @return the semantic tokens legend
     */
    public SemanticTokensLegend getCombinedSemanticTokensLegend() {
        if (defaultGrammar != null) {
            return defaultGrammar.getSemanticTokensLegend();
        }
        return new SemanticTokensLegend(List.of(), List.of());
    }

    private String extractExtension(String uri) {
        if (uri == null) {
            return "";
        }
        var lastDot = uri.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return uri.substring(lastDot).toLowerCase();
    }

}
