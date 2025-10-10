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
package io.sapl.playground.domain;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.Getter;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * In-memory policy retrieval point for the SAPL playground.
 * Stores parsed policy documents and provides them for evaluation.
 * <p>
 * This implementation is immutable to ensure thread-safety and avoid race
 * conditions.
 * All SAPL document objects are assumed to be immutable. Once constructed with
 * a set
 * of policy documents, the retrieval point's state cannot be modified.
 * <p>
 * The retrieval point tracks consistency - it becomes inconsistent if any
 * policy
 * document fails to parse or if multiple documents share the same name.
 */
@ToString
public class PlaygroundPolicyRetrievalPoint implements PolicyRetrievalPoint {

    /*
     * Map of document names to parsed policy documents.
     * Provides fast lookup of documents by name.
     */
    private final Map<String, Document> documentsByName = new HashMap<>();

    /*
     * Set of document names for duplicate detection.
     * Used to ensure no two documents have the same name.
     */
    private final Set<String> documentNames = new HashSet<>();

    /*
     * Indicates whether the policy retrieval point is in a consistent state.
     * Becomes false if any document fails to parse or if duplicate names exist.
     * Exposed to allow callers to check validity before use.
     */
    @Getter
    private boolean consistent = true;

    /*
     * SAPL interpreter for parsing policy document source strings.
     */
    private final SAPLInterpreter interpreter;

    /**
     * Creates a new policy retrieval point from policy document sources.
     * Parses all provided documents and checks for consistency.
     * <p>
     * The retrieval point will be marked as inconsistent if:
     * - Any document fails to parse
     * - Multiple documents have the same name
     *
     * @param documents list of SAPL policy document source strings
     * @param interpreter SAPL interpreter for parsing documents
     */
    public PlaygroundPolicyRetrievalPoint(List<String> documents, SAPLInterpreter interpreter) {
        this.interpreter = interpreter;
        documents.forEach(this::loadDocument);
    }

    /*
     * Loads and parses a policy document from source.
     * Marks the retrieval point as inconsistent if parsing fails
     * or if the document name conflicts with an existing document.
     */
    private void loadDocument(String source) {
        final var document = interpreter.parseDocument(source);

        if (document.isInvalid()) {
            this.consistent = false;
        }

        if (!documentNames.add(document.name())) {
            this.consistent = false;
        }

        documentsByName.put(document.name(), document);
    }

    /**
     * Retrieves policies that match the current evaluation context.
     * If the retrieval point is inconsistent, returns an invalid result.
     * Otherwise, evaluates all documents' target expressions and returns
     * matching policies.
     *
     * @return mono emitting the policy retrieval result with matching documents
     */
    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        if (!consistent) {
            return Mono.just(PolicyRetrievalResult.invalidPrpResult());
        }

        final var documentMatches = Flux
                .merge(documentsByName.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());

        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    /**
     * Returns all documents in this policy retrieval point.
     * Includes both matching and non-matching documents.
     *
     * @return collection of all policy documents
     */
    @Override
    public Collection<Document> allDocuments() {
        return documentsByName.values();
    }
}
