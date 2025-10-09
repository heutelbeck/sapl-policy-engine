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
package io.sapl.playground;

import java.util.*;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.Getter;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@ToString
public class PlaygroundPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private final Map<String, Document> documentsByName = new HashMap<>();
    private final Set<String>           names           = new HashSet<>();
    @Getter
    private boolean                     consistent      = true;
    SAPLInterpreter                     parser;

    public PlaygroundPolicyRetrievalPoint(List<String> documents, SAPLInterpreter parser) {
        this.parser = parser;
        documents.forEach(this::load);
    }

    private void load(String source) {
        final var document = parser.parseDocument(source);
        if (document.isInvalid()) {
            this.consistent = false;
        }
        if (!names.add(document.name())) {
            this.consistent = false;
        }
        documentsByName.put(document.name(), document);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        if (!consistent)
            return Mono.just(PolicyRetrievalResult.invalidPrpResult());

        final var documentMatches = Flux
                .merge(documentsByName.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());

        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    @Override
    public Collection<Document> allDocuments() {
        return documentsByName.values();
    }

}
