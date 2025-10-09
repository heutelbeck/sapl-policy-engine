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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Optional;

@Slf4j
public class PlaygroundVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private final Sinks.Many<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmSink;
    private final Sinks.Many<Optional<Map<String, Val>>>                 variablesSink;

    private final Flux<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmFlux;
    private final Flux<Optional<Map<String, Val>>>                 variablesFlux;

    public PlaygroundVariablesAndCombinatorSource() {
        combiningAlgorithmSink = Sinks.many().replay().latest();
        variablesSink          = Sinks.many().replay().latest();

        combiningAlgorithmFlux = combiningAlgorithmSink.asFlux();
        variablesFlux          = variablesSink.asFlux();
        setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        setVariables(Map.of());
    }

    public void setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) {
        log.error("A - update algorithm");
        combiningAlgorithmSink.tryEmitNext(Optional.ofNullable(algorithm));
    }

    public void setVariables(Map<String, Val> variables) {
        log.error("A - update variables");
        variablesSink.tryEmitNext(Optional.ofNullable(variables));
    }

    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return combiningAlgorithmFlux;
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return variablesFlux;
    }

    @Override
    @PreDestroy
    public void destroy() {
        combiningAlgorithmSink.tryEmitComplete();
        variablesSink.tryEmitComplete();
    }
}
