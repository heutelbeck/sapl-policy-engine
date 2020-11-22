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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinatorFactory;
import io.sapl.pdp.embedded.config.VariablesAndCombinatorSource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class FileSystemVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

	private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";
	private static final Pattern CONFIG_FILE_REGEX_PATTERN = Pattern.compile("pdp\\.json");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String path;
	private final Path watchDir;
	private Scheduler dirWatcherScheduler;
	private Flux<PolicyDecisionPointConfiguration> configFlux;
	private Disposable monitorSubscription;

	public FileSystemVariablesAndCombinatorSource(String configurationPath) {

		// First resolve actual path

		if (configurationPath.startsWith("~" + File.separator) || configurationPath.startsWith("~/")) {
			path = System.getProperty("user.home") + configurationPath.substring(1);
		} else if (configurationPath.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		} else {
			path = configurationPath;
		}
		watchDir = Paths.get(path);

		// Set up directory watcher
		log.info("Monitor folder for config: {}", watchDir.toAbsolutePath());
		final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);
		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
				CONFIG_FILE_REGEX_PATTERN);
		dirWatcherScheduler = Schedulers.newElastic("configWatcher");

		configFlux = Flux.<WatchEvent<Path>>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnCancel(adapter::cancel).scan(loadConfig(), this::processWatcherEvent).distinctUntilChanged()
				.subscribeOn(dirWatcherScheduler).share().cache();
		monitorSubscription = configFlux.subscribe();
	}

	private PolicyDecisionPointConfiguration loadConfig() {
		Path configurationFile = Paths.get(path, CONFIG_FILE_GLOB_PATTERN);
		log.info("loading config from: {}", configurationFile.toAbsolutePath());
		if (Files.notExists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
			// If file does not exist, return default configuration
			return new PolicyDecisionPointConfiguration();
		}
		try {
			return MAPPER.readValue(configurationFile.toFile(), PolicyDecisionPointConfiguration.class);
		} catch (IOException e) {
			throw new RuntimeException("FATAL ERROR: Could not read configuration file: " + e.getMessage());
		}
	}

	@Override
	public Flux<DocumentsCombinator> getDocumentsCombinator() {
		return Flux.from(configFlux).map(PolicyDecisionPointConfiguration::getAlgorithm).distinctUntilChanged()
				.map(DocumentsCombinatorFactory::getCombinator);
	}

	@Override
	public Flux<Map<String, JsonNode>> getVariables() {
		return Flux.from(configFlux).map(PolicyDecisionPointConfiguration::getVariables).distinctUntilChanged()
				.map(HashMap::new);
	}

	private PolicyDecisionPointConfiguration processWatcherEvent(PolicyDecisionPointConfiguration lastConfig,
			WatchEvent<Path> watchEvent) {
		var kind = watchEvent.kind();
		if (kind != ENTRY_DELETE && kind != ENTRY_CREATE && kind != ENTRY_MODIFY) {
			log.debug("dropping unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
			return lastConfig;
		}

		if (kind == ENTRY_DELETE) {
			log.info("config deleted. revertig to default config.");
			return new PolicyDecisionPointConfiguration();
		}
		var absoluteFilePath = Paths.get(path, CONFIG_FILE_GLOB_PATTERN);
		if (absoluteFilePath.toFile().length() == 0) {
			log.debug("dropping potential duplicate event. {}", kind);
			return lastConfig;
		}

		// MODIFY or CREATED
		log.debug("reloading config");
		return loadConfig();
	}

	@Override
	public void dispose() {
		if (!monitorSubscription.isDisposed())
			monitorSubscription.dispose();
		if (!dirWatcherScheduler.isDisposed())
			dirWatcherScheduler.dispose();
	}

}
