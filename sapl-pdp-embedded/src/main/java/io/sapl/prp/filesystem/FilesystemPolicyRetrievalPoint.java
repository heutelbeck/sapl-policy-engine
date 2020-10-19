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
package io.sapl.prp.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.directorywatcher.InitialWatchEvent;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@Slf4j
public class FilesystemPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";

	private static final Pattern POLICY_FILE_REGEX_PATTERN = Pattern.compile(".+\\.sapl");

	private final ReentrantLock lock = new ReentrantLock();

	private final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	private String path;

	private ParsedDocumentIndex parsedDocIdx;

	private Scheduler dirWatcherScheduler;

	private ReplayProcessor<WatchEvent<Path>> dirWatcherEventProcessor = ReplayProcessor
			.cacheLastOrDefault(InitialWatchEvent.INSTANCE);

	public FilesystemPolicyRetrievalPoint(@NonNull String policyPath,
			@NonNull ParsedDocumentIndex parsedDocumentIndex) {
		if (policyPath.startsWith("~" + File.separator) || policyPath.startsWith("~/")) {
			this.path = System.getProperty("user.home") + policyPath.substring(1);
		} else if (policyPath.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		} else {
			this.path = policyPath;
		}

		this.parsedDocIdx = parsedDocumentIndex;

		initializeIndex();

		final Path watchDir = Paths.get(path);
		final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);

		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
				POLICY_FILE_REGEX_PATTERN);
		dirWatcherScheduler = Schedulers.newElastic("policyWatcher");
		final Flux<WatchEvent<Path>> dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnNext(event -> {
			updateIndex(event);
			dirWatcherEventProcessor.onNext(event);
		}).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

		dirWatcherFlux.subscribe();
	}

	private void initializeIndex() {
		try {
			lock.lock();

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path), POLICY_FILE_GLOB_PATTERN)) {
				for (Path filePath : stream) {
					LOGGER.info("load: {}", filePath);
					final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
					parsedDocIdx.put(filePath.toString(), saplDocument);
				}
			}
			parsedDocIdx.setLiveMode();
		} catch (IOException | PolicyEvaluationException e) {
			LOGGER.error("Error while initializing the document index.", e);
		} finally {
			lock.unlock();
		}
	}

	private void updateIndex(WatchEvent<Path> watchEvent) {
		final WatchEvent.Kind<Path> kind = watchEvent.kind();
		final Path fileName = watchEvent.context();
		try {
			lock.lock();

			final Path absoluteFilePath = Paths.get(path, fileName.toString());
			final String absoluteFileName = absoluteFilePath.toString();
			if (kind == ENTRY_CREATE) {
				LOGGER.info("adding {} to index", fileName);
				final SAPL saplDocument = interpreter.parse(Files.newInputStream(absoluteFilePath));
				parsedDocIdx.put(absoluteFileName, saplDocument);
			} else if (kind == ENTRY_DELETE) {
				LOGGER.info("removing {} from index", fileName);
				parsedDocIdx.remove(absoluteFileName);
			} else if (kind == ENTRY_MODIFY) {
				LOGGER.info("updating {} in index", fileName);
				final SAPL saplDocument = interpreter.parse(Files.newInputStream(absoluteFilePath));
				parsedDocIdx.put(absoluteFileName, saplDocument);
			} else {
				LOGGER.error("unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
			}
		} catch (IOException | PolicyEvaluationException e) {
			LOGGER.error("Error while updating the document index.", e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return dirWatcherEventProcessor.flatMap(event -> {
			try {
				lock.lock();
				return Flux.from(parsedDocIdx.retrievePolicies(authzSubscription, functionCtx, variables))
						.doOnNext(this::logMatching);
			} finally {
				lock.unlock();
			}
		});
	}

	private void logMatching(PolicyRetrievalResult result) {
		if (result.getMatchingDocuments().isEmpty()) {
			LOGGER.trace("|-- Matching documents: NONE");
		} else {
			LOGGER.trace("|-- Matching documents:");
			for (SAPL doc : result.getMatchingDocuments()) {
				LOGGER.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(),
						doc.getPolicyElement().getClass().getName());
			}
		}
		LOGGER.trace("|");
	}

}
