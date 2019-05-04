package io.sapl.prp.filesystem;

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

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class FilesystemPolicyRetrievalPoint implements PolicyRetrievalPoint, io.sapl.api.pdp.Disposable {

	public static final String POLICY_FILE_PATTERN = "*.sapl";
	public static final String POLICY_FILE_SUFFIX = ".sapl";

	private static final WatchEvent<Path> INITIAL_WATCH_EVENT = new InitialWatchEventToBeIgnored();

	private String path;
	private ParsedDocumentIndex parsedDocIdx;

	private SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	private Scheduler dirWatcherScheduler;
	private Disposable dirWatcherFluxSubscription;
	private ReplayProcessor<WatchEvent<Path>> dirWatcherEventProcessor = ReplayProcessor.cacheLastOrDefault(INITIAL_WATCH_EVENT);
	private ReentrantLock lock;

	public FilesystemPolicyRetrievalPoint(@NonNull String policyPath, @NonNull ParsedDocumentIndex parsedDocumentIndex) {
		this.path = policyPath;
		if (path.startsWith("~" + File.separator)) {
			path = System.getProperty("user.home") + path.substring(1);
		} else if (path.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		}

		this.parsedDocIdx = parsedDocumentIndex;

		final Path watchDir = Paths.get(path);
		final PolicyDirectoryWatcher directoryWatcher = new PolicyDirectoryWatcher(watchDir);

		this.lock = new ReentrantLock();

		initializeIndex();

		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter();
		dirWatcherScheduler = Schedulers.newElastic("policyWatcher");
		final Flux<WatchEvent<Path>> dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnNext(event -> {
			if (event == INITIAL_WATCH_EVENT) {
				// don't update the index on the initial event (nothing has changed)
				dirWatcherEventProcessor.onNext(event);
			} else {
				updateIndex(event);
				dirWatcherEventProcessor.onNext(event);
			}
		}).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

		dirWatcherFluxSubscription = dirWatcherFlux.subscribe();
	}

	private void initializeIndex() {
		try {
			lock.lock();

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path), POLICY_FILE_PATTERN)) {
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
	public Flux<PolicyRetrievalResult> retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return dirWatcherEventProcessor.map(event -> {
			try {
				lock.lock();
				return parsedDocIdx.retrievePolicies(request, functionCtx, variables);
			} finally {
				lock.unlock();
			}
		});
	}

	@Override
	public void dispose() {
		dirWatcherFluxSubscription.dispose();
		dirWatcherScheduler.dispose();
	}


	/**
	 * The policy retrieval flux is combined with the PDP fluxes. Therefore an initial event
	 * is required even if nothing has been changed in the policies directory. Otherwise the
	 * combined flux would only emit items after the first policy modification.
	 * This class defines the type of the initial directory watch event. No index update will
	 * be triggered upon this event.
	 */
	private static class InitialWatchEventToBeIgnored implements WatchEvent<Path> {
		@Override
		public Kind<Path> kind() {
			return ENTRY_MODIFY;
		}

		@Override
		public int count() {
			return 0;
		}

		@Override
		public Path context() {
			return null;
		}
	}
}
