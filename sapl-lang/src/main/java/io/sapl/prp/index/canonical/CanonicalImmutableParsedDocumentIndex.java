/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.canonical.ordering.DefaultPredicateOrderStrategy;
import io.sapl.prp.index.canonical.ordering.PredicateOrderStrategy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
public class CanonicalImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {

    private final CanonicalIndexDataContainer indexDataContainer;
    private final Map<String, SAPL> documents;
    private final PredicateOrderStrategy predicateOrderStrategy;
    private final EvaluationContext pdpScopedEvaluationContext;

    public CanonicalImmutableParsedDocumentIndex(PredicateOrderStrategy predicateOrderStrategy,
                                                 EvaluationContext pdpScopedEvaluationContext) {
        this(Collections.emptyMap(), predicateOrderStrategy, pdpScopedEvaluationContext);
    }

    public CanonicalImmutableParsedDocumentIndex(EvaluationContext pdpScopedEvaluationContext) {
        this(Collections.emptyMap(), new DefaultPredicateOrderStrategy(), pdpScopedEvaluationContext);
    }

    private CanonicalImmutableParsedDocumentIndex(Map<String, SAPL> updatedDocuments,
                                                  PredicateOrderStrategy predicateOrderStrategy,
                                                  EvaluationContext pdpScopedEvaluationContext) {
        this.documents = updatedDocuments;
        this.predicateOrderStrategy = predicateOrderStrategy;
        this.pdpScopedEvaluationContext = pdpScopedEvaluationContext;
        Map<String, DisjunctiveFormula> targets = this.documents.entrySet().stream().collect(
                Collectors.toMap(Entry::getKey, entry -> retainTarget(entry.getValue(), pdpScopedEvaluationContext)));

        this.indexDataContainer = new CanonicalIndexDataCreationStrategy(predicateOrderStrategy).constructNew(documents,
                targets);
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
        try {
            return CanonicalIndexAlgorithm.match(subscriptionScopedEvaluationContext, indexDataContainer);
        } catch (PolicyEvaluationException e) {
            log.error("error while retrieving policies", e);
            return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
        }
    }

    @Override
    public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
        var newDocuments = new HashMap<>(documents);
        applyEvent(newDocuments, event);
        log.debug("returning updated index containing {} documents", newDocuments.size());
        return new CanonicalImmutableParsedDocumentIndex(newDocuments, predicateOrderStrategy,
                pdpScopedEvaluationContext);
    }

    private DisjunctiveFormula retainTarget(SAPL sapl, EvaluationContext pdpScopedEvaluationContext) {
        try {
            var targetExpression = sapl.getPolicyElement().getTargetExpression();
            DisjunctiveFormula targetFormula;
            if (targetExpression == null) {
                targetFormula = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
            } else {
                Map<String, String> imports = sapl.documentScopedEvaluationContext(pdpScopedEvaluationContext)
                        .getImports();
                targetFormula = TreeWalker.walk(targetExpression, imports);
            }

            return targetFormula;
        } catch (PolicyEvaluationException e) {
            log.error("exception while retaining target for document {}", sapl.getPolicyElement().getSaplName(), e);
            return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
        }
    }

}
