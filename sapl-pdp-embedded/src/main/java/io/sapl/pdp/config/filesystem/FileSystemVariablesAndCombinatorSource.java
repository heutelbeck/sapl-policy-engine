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
package io.sapl.pdp.config.filesystem;

import static io.sapl.util.filemonitoring.FileMonitorUtil.monitorDirectory;
import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class FileSystemVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

    private static final Map<String, PolicyDocumentCombiningAlgorithm> ALGORITHMS = Map.of("deny-overrides",
            PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, "permit-overrides",
            PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES, "only-one-applicable",
            PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE, "deny-unless-permit",
            PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT, "permit-unless-deny",
            PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path watchDir;

    private final Flux<Optional<PolicyDecisionPointConfiguration>> configFlux;

    private final Disposable monitorSubscription;

    public FileSystemVariablesAndCombinatorSource(String configurationPath) {
        watchDir = resolveHomeFolderIfPresent(configurationPath);
        log.info("Monitoring folder for PDP configuration: {}", watchDir);
        final var monitoringFlux = monitorDirectory(watchDir, file -> CONFIG_FILE_GLOB_PATTERN.equals(file.getName()));
        configFlux          = monitoringFlux.scan(loadConfig(), (x, fileEvent) -> processWatcherEvent(fileEvent))
                .distinctUntilChanged().share().cache(1);
        monitorSubscription = Flux.from(configFlux).subscribe();
    }

    private Optional<PolicyDecisionPointConfiguration> loadConfig() {
        Path configurationFile = Paths.get(watchDir.toString(), CONFIG_FILE_GLOB_PATTERN);
        log.info("Loading PDP configuration from: {}", configurationFile.toAbsolutePath());
        if (Files.notExists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            // If file does not exist, return default configuration
            final var defaultConfiguration = new PolicyDecisionPointConfiguration();
            log.info("No PDP configuration file present. Use default configuration: {}", defaultConfiguration);
            return Optional.of(defaultConfiguration);
        }
        try {
            final var jsonNode = MAPPER.readValue(configurationFile.toFile(), JsonNode.class);
            final var config   = new PolicyDecisionPointConfiguration();
            if (jsonNode == null) {
                return Optional.empty();
            }
            if (jsonNode.has("algorithm")) {
                final var algorithmString = jsonNode.get("algorithm").asText().toLowerCase();
                var       algorithmEnum   = ALGORITHMS.get(algorithmString);
                if (algorithmEnum == null) {
                    algorithmEnum = PolicyDocumentCombiningAlgorithm.valueOf(algorithmString.toUpperCase());
                }
                config.setAlgorithm(algorithmEnum);
            }
            final var variables = new HashMap<String, Val>();
            if (jsonNode.has("variables")) {
                jsonNode.get("variables").properties().forEach(field -> variables.put(field.getKey(),
                        Val.of(field.getValue()).withTrace(VariablesAndCombinatorSource.class)));
            }
            config.setVariables(variables);
            return Optional.of(config);
        } catch (IOException e) {
            log.info("Error reading PDP configuration file. No configuration available.", e);
            return Optional.empty();
        }
    }

    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return configFlux.map(config -> config.map(PolicyDecisionPointConfiguration::getAlgorithm));
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return Flux.from(configFlux)
                .switchMap(config -> config
                        .map(policyDecisionPointConfiguration -> Flux
                                .just(Optional.of(policyDecisionPointConfiguration.getVariables())))
                        .orElseGet(() -> Flux.just(Optional.empty())));
    }

    private Optional<PolicyDecisionPointConfiguration> processWatcherEvent(FileEvent fileEvent) {
        if (fileEvent instanceof FileDeletedEvent) {
            log.info("Configuration file deleted. Reverting to default config.");
            return Optional.of(new PolicyDecisionPointConfiguration());
        }
        // MODIFY or CREATED
        return loadConfig();
    }

    @Override
    public void destroy() {
        if (!monitorSubscription.isDisposed())
            monitorSubscription.dispose();
    }

}
