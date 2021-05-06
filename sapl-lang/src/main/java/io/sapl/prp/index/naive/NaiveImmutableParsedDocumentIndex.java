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
package io.sapl.prp.index.naive;

import com.google.common.collect.Sets;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@Slf4j
@ToString
public class NaiveImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {
    // Mapping of Document Name to the parsed Document
    private final Map<String, SAPL> documents;
    private final boolean consistent;

    public NaiveImmutableParsedDocumentIndex() {
        documents = new HashMap<>();
        consistent = true;
    }

    private NaiveImmutableParsedDocumentIndex(Map<String, SAPL> documents, boolean consistent) {
        this.documents = documents;
        this.consistent = consistent;
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
        return retrievePoliciesCollector(subscriptionScopedEvaluationContext);
    }

    public Mono<PolicyRetrievalResult> retrievePoliciesCollector(EvaluationContext subscriptionScopedEvaluationContext) {
        var retrieval = Mono.just(new PolicyRetrievalResult());
        if (!consistent) {
            return retrieval.map(PolicyRetrievalResult::withInvalidState);
        }

        var valMonoList = documents.values().stream()
                .map(document -> document.matches(subscriptionScopedEvaluationContext)
                        .map(val -> Pair.of(document, val))
                ).collect(Collectors.toList());

        var valFlux = Flux.concat(valMonoList);

        return valFlux.collect(PolicyRetrievalResultCollector.toResult());
    }


    @Override
    public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
        // Do a shallow copy. String is immutable, and SAPL is assumed to be too.
        var newDocuments = new HashMap<>(documents);
        var newConsistencyState = consistent;
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

    private void applyUpdate(Map<String, SAPL> newDocuments, PrpUpdateEvent.Update update) {
        var name = update.getDocument().getPolicyElement().getSaplName();
        if (update.getType() == Type.UNPUBLISH) {
            newDocuments.remove(name);
        } else if (update.getType() == Type.PUBLISH) {
            newDocuments.put(name, update.getDocument());
        }
    }

    public static class PolicyRetrievalResultCollector<T extends Pair<SAPL, Val>>
            implements Collector<T, PolicyRetrievalResultBuilder, PolicyRetrievalResult> {

        @Override
        public Supplier<PolicyRetrievalResultBuilder> supplier() {
            return PolicyRetrievalResultBuilder::builder;
        }

        @Override
        public BiConsumer<PolicyRetrievalResultBuilder, T> accumulator() {
            return PolicyRetrievalResultBuilder::add;
        }

        @Override
        public BinaryOperator<PolicyRetrievalResultBuilder> combiner() {
            return (left, right) -> left.combine(right.build());
        }

        @Override
        public Function<PolicyRetrievalResultBuilder, PolicyRetrievalResult> finisher() {
            return PolicyRetrievalResultBuilder::build;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.immutableEnumSet(Characteristics.UNORDERED);
        }

        public static <T extends Pair<SAPL, Val>> PolicyRetrievalResultCollector<T> toResult() {
            return new PolicyRetrievalResultCollector<>();
        }
    }

    private static class PolicyRetrievalResultBuilder {

        private PolicyRetrievalResult result = new PolicyRetrievalResult();

        public static PolicyRetrievalResultBuilder builder() {
            return new PolicyRetrievalResultBuilder();
        }

        public static void add(PolicyRetrievalResultBuilder policyRetrievalResultBuilder, Pair<SAPL, Val> pair) {
            policyRetrievalResultBuilder.addPair(pair);
        }

        private void addPair(Pair<SAPL, Val> pair) {
            var document = pair.getKey();
            var match = pair.getValue();

            if (match.isError()) {
                result = result.withError();
            }
            if (!match.isBoolean()) {
                log.error("matching returned error. (Should never happen): {}", match.getMessage());
                result = result.withError();
            }
            if (match.getBoolean()) {
                result = result.withMatch(document);
            }
        }

        public PolicyRetrievalResult build() {
            return result;
        }

        public PolicyRetrievalResultBuilder combine(PolicyRetrievalResult build) {
            build.getMatchingDocuments().forEach(result::withMatch);
            return this;
        }
    }

}
