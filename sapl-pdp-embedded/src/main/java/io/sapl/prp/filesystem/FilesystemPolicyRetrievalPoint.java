package io.sapl.prp.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
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

	private String path;
	private ParsedDocumentIndex parsedDocIdx;

	private SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	private Scheduler dirWatcherScheduler;
	private Disposable dirWatcherFluxSubscription;
	private ReplayProcessor<String> dirWatcherEventProcessor = ReplayProcessor.cacheLastOrDefault("initial event");

	private ReentrantLock lock;

	public FilesystemPolicyRetrievalPoint(String policyPath) {
		this(policyPath, null);
	}

	public FilesystemPolicyRetrievalPoint(@NonNull String policyPath, FunctionContext functionCtx) {
		this.path = policyPath;
		if (path.startsWith("~" + File.separator)) {
			path = System.getProperty("user.home") + path.substring(1);
		} else if (path.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		}

		parsedDocIdx = functionCtx != null ? new FastParsedDocumentIndex(functionCtx) : new SimpleParsedDocumentIndex();

		final Path watchDir = Paths.get(path);
		final PolicyDirectoryWatcher directoryWatcher = new PolicyDirectoryWatcher(watchDir);

		this.lock = new ReentrantLock();

		init();

		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter();
		dirWatcherScheduler = Schedulers.newElastic("dirWatcher");
		final Flux<String> dirWatcherFlux = Flux.<String>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnNext(e -> {
			if ("policy modification event".equals(e)) {
				init();
			}
			dirWatcherEventProcessor.onNext(e);
		}).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

		dirWatcherFluxSubscription = dirWatcherFlux.subscribe();
	}

	private void init() {
		try {
			lock.lock();

			DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path), POLICY_FILE_PATTERN);
			try {
				for (Path filePath : stream) {
					LOGGER.info("load: {}",filePath);
					final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
					this.parsedDocIdx.put(filePath.toString(), saplDocument);
				}
			} finally {
				stream.close();
			}
			this.parsedDocIdx.setLiveMode();
		} catch (IOException | PolicyEvaluationException e) {
			LOGGER.error("Error while initializing the document index.", e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return dirWatcherEventProcessor.map(e -> {
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
}
