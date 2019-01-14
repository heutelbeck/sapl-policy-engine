package io.sapl.prp.embedded;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.PrpImplementation;
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

	public static final String DEFAULT_PATH = "classpath:policies";
	public static final String POLICY_FILE_EXTENSION = ".sapl";

    private String path;
	private PrpImplementation prpImplementation;
	private FunctionContext functionCtx;
	private ParsedDocumentIndex parsedDocIdx;

	private SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	private Scheduler dirWatcherScheduler;
	private Disposable dirWatcherFluxSubscription;
    private ReplayProcessor<String> dirWatcherEventProcessor = ReplayProcessor.cacheLastOrDefault("initial event");

	private ReentrantLock lock;

	public ResourcesPolicyRetrievalPoint(String policyPath, PrpImplementation prpImplementation, FunctionContext functionCtx)
			throws IOException {
        this.path = (policyPath == null) ? DEFAULT_PATH : policyPath;
        this.prpImplementation = prpImplementation;
        this.functionCtx = functionCtx;

        final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
        final Resource configFile = pm.getResource(path + "/" + EmbeddedPolicyDecisionPoint.PDP_JSON);
        final URI configFileURI = configFile.getURI();
        final Path watchDir = Paths.get(configFileURI).getParent();
        final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);

        this.lock = new ReentrantLock();

        init();

        final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter();
        dirWatcherScheduler = Schedulers.newElastic("dirWatcher");
        final Flux<String> dirWatcherFlux = Flux.<String> push(sink -> {
                    adapter.setSink(sink);
                    directoryWatcher.watch(adapter);
                })
                .doOnNext(e -> {
                    if ("policy modification event".equals(e)) {
                        init();
                    }
                    dirWatcherEventProcessor.onNext(e);
                })
                .doOnCancel(adapter::cancel)
                .subscribeOn(dirWatcherScheduler);

        dirWatcherFluxSubscription = dirWatcherFlux.subscribe();
    }

    private void init() {
        try {
            lock.lock();
            parsedDocIdx = prpImplementation == PrpImplementation.INDEXED
                    ? new FastParsedDocumentIndex(functionCtx)
                    : new SimpleParsedDocumentIndex();

            final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
            final Resource[] policyFiles = pm.getResources(path + "/*" + POLICY_FILE_EXTENSION);
            for (Resource policyFile : policyFiles) {
                final SAPL saplDocument = interpreter.parse(policyFile.getInputStream());
                this.parsedDocIdx.put(policyFile.getFilename(), saplDocument);
            }
            this.parsedDocIdx.setLiveMode();
        } catch (IOException e) {
            LOGGER.error("Error while initializing the document index.", e);
        } catch (PolicyEvaluationException e) {
            LOGGER.error("Error while initializing the document index.", e);
        } finally {
            lock.unlock();
        }
    }

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(Request request, FunctionContext functionCtx, Map<String, JsonNode> variables) {
	    return dirWatcherEventProcessor
                .map(e -> {
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
