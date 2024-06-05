/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.pdp;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;

@Slf4j
@Component
@RequiredArgsConstructor
public class CEVariablesAndCombinatorSource implements VariablesAndCombinatorSource, PDPConfigurationPublisher {

    private Many<Collection<Variable>>             variablesProcessorSink = Sinks.many().replay().all();
    private Many<PolicyDocumentCombiningAlgorithm> combiningAlgorithmSink = Sinks.many().replay().all();

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return variablesProcessorSink.asFlux().map(CEVariablesAndCombinatorSource::variablesCollectionToMap)
                .map(Optional::of);
    }

    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return combiningAlgorithmSink.asFlux().map(Optional::of);
    }

    @Override
    public void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm) {
        combiningAlgorithmSink.emitNext(algorithm, EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public void publishVariables(@NonNull Collection<Variable> variables) {
        variablesProcessorSink.emitNext(variables, EmitFailureHandler.FAIL_FAST);
    }

    private static Map<String, Val> variablesCollectionToMap(@NonNull Collection<Variable> variables) {
        Map<String, Val> variablesAsMap = Maps.newHashMapWithExpectedSize(variables.size());
        for (Variable variable : variables) {
            try {
                variablesAsMap.put(variable.getName(), Val.ofJson(variable.getJsonValue()));
            } catch (JsonProcessingException e) {
                log.error("Ignoring variable {} not valid JSON.", variable.getName());
            }
        }

        return variablesAsMap;
    }
}
