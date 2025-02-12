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
package io.sapl.test.integration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Slf4j
public final class InputStringVariablesAndCombinatorSource implements VariablesAndCombinatorSource {
    private final PolicyDecisionPointConfiguration config;

    public InputStringVariablesAndCombinatorSource(@NonNull String input, @NonNull ObjectMapper mapper,
            PolicyDocumentCombiningAlgorithm testInternalConfiguredCombiningAlg,
            Map<String, Val> testInternalConfiguredVariables) {
        log.info("Loading the PDP configuration from input string");
        try {
            final var jsonNode = mapper.readValue(input, JsonNode.class);
            this.config = new PolicyDecisionPointConfiguration();
            if (jsonNode.has("algorithm")) {
                this.config.setAlgorithm(PolicyDocumentCombiningAlgorithm.valueOf(jsonNode.get("algorithm").asText()));
            }
            final var variables = new HashMap<String, Val>();
            if (jsonNode.has("variables")) {
                jsonNode.get("variables").fields().forEachRemaining(field -> variables.put(field.getKey(),
                        Val.of(field.getValue()).withTrace(VariablesAndCombinatorSource.class)));
            }
            this.config.setVariables(variables);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }

        if (testInternalConfiguredCombiningAlg != null) {
            this.config.setAlgorithm(testInternalConfiguredCombiningAlg);
        }
        if (testInternalConfiguredVariables != null) {
            this.config.setVariables(testInternalConfiguredVariables);
        }
    }

    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return Flux.just(config.getAlgorithm()).map(Optional::of);
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return Flux.just(config.getVariables()).map(HashMap::new).map(Optional::of);
    }

}
