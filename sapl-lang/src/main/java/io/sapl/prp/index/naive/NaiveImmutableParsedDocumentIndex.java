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
package io.sapl.prp.index.naive;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import lombok.Getter;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@ToString
public class NaiveImmutableParsedDocumentIndex implements UpdateEventDrivenPolicyRetrievalPoint {

    private final Map<String, Document> documentsById;
    @Getter
    private final boolean               consistent;

    public NaiveImmutableParsedDocumentIndex() {
        documentsById = new HashMap<>();
        consistent    = true;
    }

    public NaiveImmutableParsedDocumentIndex(Map<String, Document> documentsById, boolean consistent) {
        this.documentsById = documentsById;
        this.consistent    = consistent;
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        return retrievePoliciesCollector();
    }

    public Mono<PolicyRetrievalResult> retrievePoliciesCollector() {
        if (!consistent)
            return Mono.just(PolicyRetrievalResult.invalidPrpResult());

        final var documentMatches = Flux
                .merge(documentsById.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());

        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    @Override
    public UpdateEventDrivenPolicyRetrievalPoint apply(PrpUpdateEvent event) {
        // Do a shallow copy. String is immutable, and SAPL is assumed to be too.
        final var newDocuments        = new HashMap<>(documentsById);
        var       newConsistencyState = consistent;
        for (var update : event.getUpdates()) {
            if (update.getType() == Type.CONSISTENT) {
                newConsistencyState = true;
            } else if (update.getType() == Type.INCONSISTENT) {
                newConsistencyState = false;
            } else {
                applyUpdate(newDocuments, update);
            }
        }
        return new NaiveImmutableParsedDocumentIndex(newDocuments, newConsistencyState);
    }

    // only PUBLISH or WITHDRAW
    private void applyUpdate(Map<String, Document> newDocuments, PrpUpdateEvent.Update update) {
        final var name = update.getDocument().sapl().getPolicyElement().getSaplName();
        if (update.getType() == Type.WITHDRAW) {
            newDocuments.remove(name);
        } else {
            newDocuments.put(name, update.getDocument());
        }
    }

    @Override
    public Collection<Document> allDocuments() {
        return documentsById.values();
    }

}
