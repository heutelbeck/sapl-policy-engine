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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Optional;

/**
 * Reactive source for variables and combining algorithm configuration in the
 * playground.
 * Provides streams that emit updates whenever variables or the combining
 * algorithm change.
 * <p>
 * Uses replay sinks to ensure new subscribers immediately receive the current
 * configuration state. This allows the PDP to react to configuration changes
 * and re-evaluate active authorization subscriptions.
 * <p>
 * Initializes with DENY_OVERRIDES algorithm and empty variables map.
 */
public class PlaygroundVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    /*
     * Sink for emitting combining algorithm updates.
     * Configured to replay the latest value to new subscribers.
     */
    private final Sinks.Many<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmSink;

    /*
     * Sink for emitting variables updates.
     * Configured to replay the latest value to new subscribers.
     */
    private final Sinks.Many<Optional<Map<String, Val>>> variablesSink;

    /*
     * Flux exposing the stream of combining algorithm updates.
     */
    private final Flux<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmFlux;

    /*
     * Flux exposing the stream of variables updates.
     */
    private final Flux<Optional<Map<String, Val>>> variablesFlux;

    /**
     * Creates a new variables and combinator source.
     * Initializes with DENY_OVERRIDES as the default combining algorithm
     * and an empty variables map. Configures replay sinks to ensure
     * new subscribers receive the current state immediately.
     */
    public PlaygroundVariablesAndCombinatorSource() {
        combiningAlgorithmSink = Sinks.many().replay().latest();
        variablesSink          = Sinks.many().replay().latest();

        combiningAlgorithmFlux = combiningAlgorithmSink.asFlux();
        variablesFlux          = variablesSink.asFlux();
        setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        setVariables(Map.of());
    }

    /**
     * Updates the combining algorithm.
     * Emits the new algorithm to all subscribers, triggering re-evaluation
     * of active authorization subscriptions.
     *
     * @param algorithm the combining algorithm to use, or null for no algorithm
     */
    public final void setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) {
        combiningAlgorithmSink.tryEmitNext(Optional.ofNullable(algorithm));
    }

    /**
     * Updates the variables available during policy evaluation.
     * Emits the new variables map to all subscribers, triggering re-evaluation
     * of active authorization subscriptions.
     *
     * @param variables map of variable names to values, or null for no variables
     */
    public final void setVariables(Map<String, Val> variables) {
        variablesSink.tryEmitNext(Optional.ofNullable(variables));
    }

    /**
     * Returns the reactive stream of combining algorithm updates.
     * Subscribers receive the current algorithm immediately, followed by
     * updates whenever the algorithm changes.
     *
     * @return flux of optional combining algorithm values
     */
    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return combiningAlgorithmFlux;
    }

    /**
     * Returns the reactive stream of variables updates.
     * Subscribers receive the current variables immediately, followed by
     * updates whenever the variables change.
     *
     * @return flux of optional variables maps
     */
    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return variablesFlux;
    }

    /**
     * Completes all streams and releases resources.
     * Called automatically when the component is destroyed.
     * Prevents memory leaks by properly closing reactive streams.
     */
    @Override
    @PreDestroy
    public void destroy() {
        combiningAlgorithmSink.tryEmitComplete();
        variablesSink.tryEmitComplete();
    }
}
