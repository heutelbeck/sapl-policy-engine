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
package io.sapl.pdp.embedded.config.filesystem;

import static io.sapl.util.filemonitoring.FileMonitorUtil.monitorDirectory;
import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinatorFactory;
import io.sapl.pdp.embedded.config.VariablesAndCombinatorSource;
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
	private final Flux<Optional<PolicyDecisionPointConfiguration>> configFlux;
	private final Disposable monitorSubscription;

	public FileSystemVariablesAndCombinatorSource(String configurationPath) {
		watchDir = resolveHomeFolderIfPresent(configurationPath);
		log.info("Monitor folder for config: {}", watchDir);
		Flux<FileEvent> monitoringFlux = monitorDirectory(watchDir,
				file -> file.getName().equals(CONFIG_FILE_GLOB_PATTERN));
		configFlux = monitoringFlux.scan(loadConfig(), this::processWatcherEvent).distinctUntilChanged().share()
				.cache();
		monitorSubscription = configFlux.subscribe();
	}

	private Optional<PolicyDecisionPointConfiguration> loadConfig() {
		Path configurationFile = Paths.get(watchDir, CONFIG_FILE_GLOB_PATTERN);
		log.info("loading config from: {}", configurationFile.toAbsolutePath());
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
	public Flux<Optional<DocumentsCombinator>> getDocumentsCombinator() {
		return Flux.from(configFlux).switchMap(config -> {
			if (config.isPresent()) {
				return Flux.just(Optional.of(DocumentsCombinatorFactory.getCombinator(config.get().getAlgorithm())));
			} else {
				return Flux.<Optional<DocumentsCombinator>>just(Optional.empty());
			}
		}).log();
	}

	@Override
	public Flux<Optional<Map<String, JsonNode>>> getVariables() {
		return Flux.from(configFlux).switchMap(config -> {
			if (config.isPresent()) {
				return Flux.just(Optional.of(config.get().getVariables()));
			} else {
				return Flux.<Optional<Map<String, JsonNode>>>just(Optional.empty());
			}
		}).log();
	}

	private Optional<PolicyDecisionPointConfiguration> processWatcherEvent(
			Optional<PolicyDecisionPointConfiguration> lastConfig, FileEvent fileEvent) {
		if (fileEvent instanceof FileDeletedEvent) {
			log.info("config deleted. reverting to default config.");
			return Optional.of(new PolicyDecisionPointConfiguration());
		}
		// MODIFY or CREATED
		return loadConfig();
	}

	@Override
	public void dispose() {
		if (!monitorSubscription.isDisposed())
			monitorSubscription.dispose();
	}

}