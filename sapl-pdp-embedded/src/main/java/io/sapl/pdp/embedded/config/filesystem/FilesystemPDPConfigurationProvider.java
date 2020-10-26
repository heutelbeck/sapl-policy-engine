/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.directorywatcher.InitialWatchEvent;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class FilesystemPDPConfigurationProvider implements PDPConfigurationProvider {

	private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

	private static final Pattern CONFIG_FILE_REGEX_PATTERN = Pattern.compile("pdp\\.json");

	private final ObjectMapper mapper = new ObjectMapper();

	private final ReentrantLock lock = new ReentrantLock();

	private String path;

	private PolicyDecisionPointConfiguration config;

	private Scheduler dirWatcherScheduler;

	private ReplayProcessor<WatchEvent<Path>> dirWatcherEventProcessor = ReplayProcessor
			.cacheLastOrDefault(InitialWatchEvent.INSTANCE);

	public FilesystemPDPConfigurationProvider(@NonNull String configPath) {
		path = configPath;
		if (configPath.startsWith("~" + File.separator) || configPath.startsWith("~/")) {
			this.path = System.getProperty("user.home") + configPath.substring(1);
		} else if (configPath.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		}

		initializeConfig();

		final Path watchDir = Paths.get(path);
		final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);

		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
				CONFIG_FILE_REGEX_PATTERN);
		dirWatcherScheduler = Schedulers.newElastic("configWatcher");
		final Flux<WatchEvent<Path>> dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnNext(event -> {
			updateConfig(event);
			dirWatcherEventProcessor.onNext(event);
		}).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

		dirWatcherFlux.subscribe();
	}

	private void initializeConfig() {
		try {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path), CONFIG_FILE_GLOB_PATTERN)) {
				for (Path filePath : stream) {
					log.info("load: {}", filePath);
					config = mapper.readValue(filePath.toFile(), PolicyDecisionPointConfiguration.class);
				}
				if (config == null) {
					config = new PolicyDecisionPointConfiguration();
				}
			}
		} catch (IOException e) {
			log.error("Error while initializing the pdp configuration.", e);
		}
	}

	private void updateConfig(WatchEvent<Path> watchEvent) {
		final WatchEvent.Kind<Path> kind = watchEvent.kind();
		final Path fileName = watchEvent.context();
		try {
			lock.lock();

			final Path absoluteFilePath = Paths.get(path, fileName.toString());
			if (kind == ENTRY_CREATE) {
				log.info("reading pdp config from {}", fileName);
				if (absoluteFilePath.toFile().length() > 0) {
					config = mapper.readValue(absoluteFilePath.toFile(), PolicyDecisionPointConfiguration.class);
				}
			} else if (kind == ENTRY_DELETE) {
				log.info("deleted pdp config file {}. Using default configuration", fileName);
				config = new PolicyDecisionPointConfiguration();
			} else if (kind == ENTRY_MODIFY) {
				if (absoluteFilePath.toFile().length() > 0) {
					log.info("updating pdp config from {}", fileName);
					config = mapper.readValue(absoluteFilePath.toFile(), PolicyDecisionPointConfiguration.class);
				} else {
					// watcher emits event twice. once for timestamp and once for modification of
					// contents.
					// ignore files of size zero to fix
					log.trace("event ignored");
				}
			} else {
				log.error("unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
			}
		} catch (IOException e) {
			log.error("Error while updating the pdp config.", e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Flux<DocumentsCombinator> getDocumentsCombinator() {
		// @formatter:off
		return dirWatcherEventProcessor.map(event -> config.getAlgorithm()).distinctUntilChanged().map(algorithm -> {
			log.trace("|-- Current PDP config: combining algorithm = {}", algorithm);
			return convert(algorithm);
		});
		// @formatter:on
	}

	@Override
	public Flux<Map<String, JsonNode>> getVariables() {
		// @formatter:off
		return dirWatcherEventProcessor.map(event -> config.getVariables()).distinctUntilChanged()
				.doOnNext(variables -> log.trace("|-- Current PDP config: variables = {}", variables));
		// @formatter:on
	}

	@Override
	public void dispose() {
		if (!dirWatcherScheduler.isDisposed()) {
			dirWatcherScheduler.dispose();
		}
		if (!dirWatcherEventProcessor.isDisposed()) {
			dirWatcherEventProcessor.dispose();
		}
	}

}
