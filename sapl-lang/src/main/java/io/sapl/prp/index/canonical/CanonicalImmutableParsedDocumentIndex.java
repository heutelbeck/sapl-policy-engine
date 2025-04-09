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
package io.sapl.prp.index.canonical;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.grammar.sapl.impl.util.ImportsUtil;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.Document;
import io.sapl.prp.PolicyRetrievalException;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.canonical.ordering.DefaultPredicateOrderStrategy;
import io.sapl.prp.index.canonical.ordering.PredicateOrderStrategy;
import reactor.core.publisher.Mono;

public class CanonicalImmutableParsedDocumentIndex implements UpdateEventDrivenPolicyRetrievalPoint {

    private final CanonicalIndexDataContainer indexDataContainer;

    private final Map<String, Document> documents;

    private final PredicateOrderStrategy predicateOrderStrategy;

    private final boolean consistent;

    private final AttributeStreamBroker attributeStreamBroker;

    private final FunctionContext functionCtx;

    public CanonicalImmutableParsedDocumentIndex(PredicateOrderStrategy predicateOrderStrategy,
            AttributeStreamBroker attributeStreamBroker, FunctionContext functionCtx) {
        this(Collections.emptyMap(), predicateOrderStrategy, true, attributeStreamBroker, functionCtx);
    }

    public CanonicalImmutableParsedDocumentIndex(AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionCtx) {
        this(Collections.emptyMap(), new DefaultPredicateOrderStrategy(), true, attributeStreamBroker, functionCtx);
    }

    private CanonicalImmutableParsedDocumentIndex(Map<String, Document> updatedDocuments,
            PredicateOrderStrategy predicateOrderStrategy, boolean consistent,
            AttributeStreamBroker attributeStreamBroker, FunctionContext functionCtx) {
        this.documents              = updatedDocuments;
        this.predicateOrderStrategy = predicateOrderStrategy;
        this.consistent             = consistent;
        this.attributeStreamBroker  = attributeStreamBroker;
        this.functionCtx            = functionCtx;

        Map<String, DisjunctiveFormula> targets = this.documents.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> retainTarget(entry.getValue())));

        this.indexDataContainer = new CanonicalIndexDataCreationStrategy(predicateOrderStrategy).constructNew(documents,
                targets);
    }

    CanonicalImmutableParsedDocumentIndex recreateIndex(Map<String, Document> updatedDocuments, boolean consistent) {
        return new CanonicalImmutableParsedDocumentIndex(updatedDocuments, predicateOrderStrategy, consistent,
                attributeStreamBroker, functionCtx);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        if (!consistent) {
            return Mono.just(PolicyRetrievalResult.invalidPrpResult());
        }
        try {
            return CanonicalIndexAlgorithm.match(indexDataContainer);
        } catch (PolicyEvaluationException e) {
            return Mono.just(PolicyRetrievalResult.retrievalErrorResult(e.getMessage()));
        }
    }

    @Override
    public UpdateEventDrivenPolicyRetrievalPoint apply(PrpUpdateEvent event) {
        final var newDocuments        = new HashMap<>(documents);
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
        return recreateIndex(newDocuments, newConsistencyState);
    }

    // only PUBLISH or WITHDRAW
    void applyUpdate(Map<String, Document> newDocuments, PrpUpdateEvent.Update update) {
        final var name = update.getDocument().sapl().getPolicyElement().getSaplName();
        if (update.getType() == Type.WITHDRAW) {
            newDocuments.remove(name);
        } else {
            if (newDocuments.containsKey(name)) {
                throw new PolicyRetrievalException("Fatal error. Policy name collision. A document with a name ('"
                        + name + "') identical to an existing document was published to the PRP.");
            }
            newDocuments.put(name, update.getDocument());
        }
    }

    private DisjunctiveFormula retainTarget(Document document) {
        final var          targetExpression = document.sapl().getImplicitTargetExpression();
        DisjunctiveFormula targetFormula;
        if (null == targetExpression) {
            targetFormula = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
        } else {
            final var imports = ImportsUtil.fetchImports(document.sapl(), attributeStreamBroker, functionCtx);
            targetFormula = TreeWalker.walk(targetExpression, imports);
        }

        return targetFormula;
    }

    @Override
    public Collection<Document> allDocuments() {
        return documents.values();
    }

    @Override
    public boolean isConsistent() {
        return consistent;
    }

}
