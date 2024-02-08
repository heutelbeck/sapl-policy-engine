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
package io.sapl.pdp.config.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private static final String DEFAULT_CONFIG_PATH = "/policies";
    private static final String CONFIG_FILE         = "pdp.json";

    private PolicyDecisionPointConfiguration config;

    public ResourcesVariablesAndCombinatorSource() throws InitializationException {
        this(DEFAULT_CONFIG_PATH);
    }

    public ResourcesVariablesAndCombinatorSource(String configPath) throws InitializationException {
        this(configPath, new ObjectMapper());
    }

    public ResourcesVariablesAndCombinatorSource(@NonNull String configPath, @NonNull ObjectMapper mapper)
            throws InitializationException {
        log.info("Loading the PDP configuration from bundled resources: '{}'", configPath);
        try (var scanResult = new ClassGraph().acceptPathsNonRecursive(configPath).scan()) {
            var configs = scanResult.getResourcesWithLeafName(CONFIG_FILE);
            if (configs.isEmpty()) {
                config = new PolicyDecisionPointConfiguration();
                log.warn(
                        "No unique SAPL policies/policy sets found in resources under path {} use default configuration: {}",
                        configPath, config);
                return;
            }
            configs.forEachByteArrayThrowingIOException((Resource res, byte[] rawDocument) -> {
                log.debug("Loading configuration {}", res.getPath());
                var jsonDocument = new String(rawDocument, StandardCharsets.UTF_8);
                var jsonNode     = mapper.readValue(jsonDocument, JsonNode.class);
                this.config = new PolicyDecisionPointConfiguration();
                if (jsonNode.has("algorithm")) {
                    this.config
                            .setAlgorithm(PolicyDocumentCombiningAlgorithm.valueOf(jsonNode.get("algorithm").asText()));
                }
                var variables = new HashMap<String, Val>();
                if (jsonNode.has("variables")) {
                    jsonNode.get("variables").fields().forEachRemaining(field -> {
                        variables.put(field.getKey(),
                                Val.of(field.getValue()).withTrace(VariablesAndCombinatorSource.class));
                    });
                }
                this.config.setVariables(variables);
            });
        } catch (IOException e) {
            throw new InitializationException(e,
                    "Failed to load configuration pdp.json from '" + configPath + "' in resoures");
        }
    }

    @Override
    public Flux<Optional<CombiningAlgorithm>> getCombiningAlgorithm() {
        return Flux.just(config.getAlgorithm()).map(CombiningAlgorithmFactory::getCombiningAlgorithm).map(Optional::of);
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return Flux.just(config.getVariables()).map(HashMap::new).map(Optional::of);
    }

}
