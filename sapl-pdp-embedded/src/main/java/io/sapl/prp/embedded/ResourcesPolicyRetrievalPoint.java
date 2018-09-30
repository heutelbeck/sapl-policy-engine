package io.sapl.prp.embedded;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.api.prp.ReactivePolicyRetrievalPoint;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.embedded.PrpImplementation;
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint, ReactivePolicyRetrievalPoint {

	public static final String DEFAULT_PATH = "classpath:policies";

    private String path;
	private PrpImplementation prpImplementation;
	private FunctionContext functionCtx;
	private ParsedDocumentIndex parsedDocIdx;

	private SAPLInterpreter interpreter = new DefaultSAPLInterpreter();
	private DirectoryWatcher directoryWatcher;
	private ReentrantLock lock;

	public ResourcesPolicyRetrievalPoint(String policyPath, PrpImplementation prpImplementation, FunctionContext functionCtx)
			throws IOException {
        this.path = (policyPath == null) ? DEFAULT_PATH : policyPath;
        this.prpImplementation = prpImplementation;
        this.functionCtx = functionCtx;

        final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
        final Resource configFile = pm.getResource(path + "/pdp.json");
        final URI configFileURI = configFile.getURI();
        final Path watchDir = Paths.get(configFileURI).getParent();
        this.directoryWatcher = new DirectoryWatcher(watchDir);

        this.lock = new ReentrantLock();

        init();
    }

    private void init() {
        try {
            lock.lock();
            parsedDocIdx = prpImplementation == PrpImplementation.INDEXED
                    ? new FastParsedDocumentIndex(functionCtx)
                    : new SimpleParsedDocumentIndex();

            final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
            final Resource[] policyFiles = pm.getResources(path + "/*.sapl");
            for (Resource policyFile : policyFiles) {
                final SAPL saplDocument = interpreter.parse(policyFile.getInputStream());
                this.parsedDocIdx.put(policyFile.getFilename(), saplDocument);
            }
            this.parsedDocIdx.setLiveMode();
        }
        catch (Exception e) {
            LOGGER.error("Error while initializing the document index.", e);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
	public PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
	    try {
            lock.lock();
            return parsedDocIdx.retrievePolicies(request, functionCtx, variables);
        }
        finally {
	        lock.unlock();
        }
	}

	@Override
	public Flux<PolicyRetrievalResult> reactiveRetrievePolicies(Request request, FunctionContext functionCtx, Map<String, JsonNode> variables) {
        final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter();
        final Flux<String> dirWatcherFlux = Flux.push(sink -> {
            adapter.setSink(sink);
            directoryWatcher.watch(adapter);
        });

        return Flux.just("initial event")
                .concatWith(dirWatcherFlux.doOnCancel(adapter::cancel))
                .map(e -> {
                    if (e.equals("policy modification event")) {
                        init();
                    }
                    return parsedDocIdx.retrievePolicies(request, functionCtx, variables);
                })
                .distinctUntilChanged();
	}

}
