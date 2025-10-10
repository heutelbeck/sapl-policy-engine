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
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import jakarta.annotation.PreDestroy;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * Reactive source for policy retrieval point updates in the playground.
 * Provides a stream of policy retrieval points that updates whenever
 * policy documents are changed.
 * <p>
 * Uses a replay sink to ensure new subscribers immediately receive
 * the current policy retrieval point state.
 */
public class PlaygroundPolicyRetrievalPointSource implements PolicyRetrievalPointSource {

    /*
     * SAPL interpreter for parsing policy documents.
     */
    private final SAPLInterpreter interpreter;

    /*
     * Sink for emitting policy retrieval point updates.
     * Configured to replay the latest value to new subscribers.
     */
    private final Sinks.Many<PolicyRetrievalPoint> policyRetrievalPointSink = Sinks.many().replay().latest();

    /*
     * Flux exposing the stream of policy retrieval point updates.
     */
    private final Flux<PolicyRetrievalPoint> policyRetrievalPointFlux = policyRetrievalPointSink.asFlux();

    /**
     * Creates a new policy retrieval point source.
     * Initializes with an empty set of policies.
     *
     * @param interpreter SAPL interpreter for parsing policy documents
     */
    public PlaygroundPolicyRetrievalPointSource(SAPLInterpreter interpreter) {
        this.interpreter = interpreter;
        updatePolicyRetrievalPoint(List.of());
    }

    /**
     * Updates the policy retrieval point with new policy documents.
     * Creates a new immutable policy retrieval point and emits it to subscribers.
     * All active subscriptions will receive the updated policies.
     *
     * @param documents list of SAPL policy document source strings
     */
    public void updatePolicyRetrievalPoint(List<String> documents) {
        val policyRetrievalPoint = new PlaygroundPolicyRetrievalPoint(documents, interpreter);
        policyRetrievalPointSink.tryEmitNext(policyRetrievalPoint);
    }

    /**
     * Returns the reactive stream of policy retrieval points.
     * Subscribers will receive the current policy retrieval point immediately,
     * followed by updates whenever policies change.
     *
     * @return flux of policy retrieval point updates
     */
    @Override
    public Flux<PolicyRetrievalPoint> policyRetrievalPoint() {
        return policyRetrievalPointFlux;
    }

    /**
     * Completes the policy retrieval point stream and releases resources.
     * Called automatically when the component is destroyed.
     * Prevents memory leaks by properly closing the reactive stream.
     */
    @Override
    @PreDestroy
    public void dispose() {
        policyRetrievalPointSink.tryEmitComplete();
    }
}
