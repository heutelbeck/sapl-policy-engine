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
package io.sapl.prp;


import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class GenericInMemoryIndexedPolicyRetrievalPoint implements PolicyRetrievalPoint, Disposable {

    private final Flux<ImmutableParsedDocumentIndex> index;
    private final Disposable indexSubscription;
    private final PrpUpdateEventSource eventSource;

    public GenericInMemoryIndexedPolicyRetrievalPoint(ImmutableParsedDocumentIndex seedIndex,
                                                      PrpUpdateEventSource eventSource) {
        this.eventSource = eventSource;
        index = Flux.from(eventSource.getUpdates()).scan(seedIndex, ImmutableParsedDocumentIndex::apply).skip(1L)
                .share().cache(1);
        // initial subscription, so that the index starts building upon startup
        indexSubscription = Flux.from(index).subscribe();
    }

    @Override
    public Flux<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
        return Flux.from(index).flatMap(idx -> idx.retrievePolicies(subscriptionScopedEvaluationContext))
                .doOnNext(this::logMatching);
    }

    @Override
    public void dispose() {
        indexSubscription.dispose();
        eventSource.dispose();
    }

    private void logMatching(PolicyRetrievalResult result) {
        if (result.getMatchingDocuments().isEmpty()) {
            log.trace("|-- Matching documents: NONE");
        } else {
            log.trace("|-- Matching documents:");
            for (AuthorizationDecisionEvaluable doc : result.getMatchingDocuments()) {
                log.trace("| |-- * {} ({})",
                        (doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getSaplName() : doc.toString(),
                        (doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getClass().getSimpleName() : doc.toString());
            }
        }
    }

}
