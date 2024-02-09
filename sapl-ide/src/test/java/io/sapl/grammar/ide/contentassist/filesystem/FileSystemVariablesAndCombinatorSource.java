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
package io.sapl.grammar.ide.contentassist.filesystem;

import static io.sapl.util.filemonitoring.FileMonitorUtil.monitorDirectory;
import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.io.File;
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
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String watchDir;

    private final String configPath;

    private final Flux<Optional<PolicyDecisionPointConfiguration>> configFlux;

    private final Disposable monitorSubscription;

    public FileSystemVariablesAndCombinatorSource(String configurationPath) {
        configPath = configurationPath;
        watchDir   = resolveHomeFolderIfPresent(configurationPath);
        log.info("Monitor folder for config: {}", watchDir);
        Flux<FileEvent> monitoringFlux = monitorDirectory(watchDir,
                file -> CONFIG_FILE_GLOB_PATTERN.equals(file.getName()));
        configFlux          = monitoringFlux.scan(loadConfig(), (__, fileEvent) -> processWatcherEvent(fileEvent))
                .distinctUntilChanged().share().cache(1);
        monitorSubscription = Flux.from(configFlux).subscribe();
    }

    private Optional<PolicyDecisionPointConfiguration> loadConfig() {
        Path configurationFile = Paths.get(watchDir, CONFIG_FILE_GLOB_PATTERN);
        log.info("Loading config from: {}", configurationFile.toAbsolutePath());
        if (Files.notExists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            // If file does not exist, return default configuration
            log.info("No config file present. Use default config.");
            return Optional.of(new PolicyDecisionPointConfiguration());
        }
        try {
            return Optional.of(MAPPER.readValue(configurationFile.toFile(), PolicyDecisionPointConfiguration.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Flux<Optional<CombiningAlgorithm>> getCombiningAlgorithm() {
        return Flux.from(configFlux)
                .switchMap(config -> config
                        .map(policyDecisionPointConfiguration -> Flux.just(Optional.of(CombiningAlgorithmFactory
                                .getCombiningAlgorithm(policyDecisionPointConfiguration.getAlgorithm()))))
                        .orElseGet(() -> Flux.just(Optional.empty())));
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {

        Map<String, Val> schemaMap = new HashMap<>();
        File[]           jsonFiles = new File(configPath).listFiles((dir, name) -> name.endsWith(".json"));

        if ((jsonFiles != null ? jsonFiles.length : 0) > 0) {
            for (File jsonFile : jsonFiles)
                try {
                    String   keyString = jsonFile.getName().substring(0, jsonFile.getName().lastIndexOf('.'));
                    JsonNode node      = MAPPER.readTree(jsonFile);
                    schemaMap.put(keyString, Val.of(node));
                } catch (Exception e) {
                    log.info("Error reading variables from file system: {}", e.getMessage());
                }
        }
        Optional<Map<String, Val>> optSchemaMap = Optional.ofNullable(schemaMap);

        return Flux.just(optSchemaMap);
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
