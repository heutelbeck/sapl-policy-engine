/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.test.utils.ClasspathHelper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Slf4j
public class ClasspathVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

    private final PolicyDecisionPointConfiguration config;

    public ClasspathVariablesAndCombinatorSource(@NonNull String configPath, @NonNull ObjectMapper mapper,
            PolicyDocumentCombiningAlgorithm testInternalConfiguredCombiningAlg,
            Map<String, JsonNode> testInternalConfiguredVariables) {
        log.info("Loading the PDP configuration from bundled resources: '{}'", configPath);

        var configDirectoryPath = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), configPath);

        log.debug("reading config from directory {}", configDirectoryPath);
        PolicyDecisionPointConfiguration pdpConfig = null;
        try (var stream = Files.newDirectoryStream(configDirectoryPath, CONFIG_FILE_GLOB_PATTERN)) {
            var filesIterator = stream.iterator();
            if (filesIterator.hasNext()) {
                var filePath = filesIterator.next();
                log.info("loading PDP configuration: {}", filePath.toAbsolutePath());
                pdpConfig = mapper.readValue(filePath.toFile(), PolicyDecisionPointConfiguration.class);
            }
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }

        if (pdpConfig == null) {
            log.info("No PDP configuration found in resources. Using defaults.");
            this.config = new PolicyDecisionPointConfiguration();
        } else {
            this.config = pdpConfig;
        }

        if (testInternalConfiguredCombiningAlg != null) {
            this.config.setAlgorithm(testInternalConfiguredCombiningAlg);
        }
        if (testInternalConfiguredVariables != null) {
            this.config.setVariables(testInternalConfiguredVariables);
        }
    }

    @Override
    public Flux<Optional<CombiningAlgorithm>> getCombiningAlgorithm() {
        return Flux.just(config.getAlgorithm()).map(CombiningAlgorithmFactory::getCombiningAlgorithm).map(Optional::of);
    }

    @Override
    public Flux<Optional<Map<String, JsonNode>>> getVariables() {
        return Flux.just(config.getVariables()).map(HashMap::new).map(Optional::of);
    }

}
