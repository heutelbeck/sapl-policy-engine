package io.sapl.server.ce.pdp;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;
import io.sapl.server.ce.service.sapldocument.SaplDocumentService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CEServerPolicyRetrievalPoint implements PolicyRetrievalPoint, SaplDocumentPublisher {
	private final ReentrantLock indexLocker = new ReentrantLock();
	private final ParsedDocumentIndex documentIndex = new SimpleParsedDocumentIndex();
	private final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	@Autowired
	private SaplDocumentService saplDocumentService;

	private FluxSink<SaplDocument> changedSaplDocumentFluxSink;
	private Flux<SaplDocument> changedSaplDocumentFlux;

	private Disposable monitorPolicies;

	@PostConstruct
	public void init() {
		this.initChangedEventFlux();
		this.initIndex();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return this.changedSaplDocumentFlux.flatMap((saplDocument) -> {
			this.indexLocker.lock();

			try {
				return Flux.from(this.documentIndex.retrievePolicies(authzSubscription, functionCtx, variables));
			} finally {
				this.indexLocker.unlock();
			}
		});
	}

	@Override
	public void publishSaplDocument(@NonNull SaplDocument saplDocument) {
		this.addSaplDocumentToIndex(saplDocument);

		this.changedSaplDocumentFluxSink.next(saplDocument);
	}

	@Override
	public void unpublishSaplDocument(@NonNull SaplDocument saplDocument) {
		this.removeSaplDocumentFromIndex(saplDocument);

		this.changedSaplDocumentFluxSink.next(saplDocument);
	}

	@PreDestroy
	public void destroy() {
		this.monitorPolicies.dispose();
	}

	private static String generateDocumentKeyFromSaplDocument(@NonNull SaplDocument saplDocument) {
		return saplDocument.getId().toString();
	}

	private void initChangedEventFlux() {
		ReplayProcessor<SaplDocument> processor = ReplayProcessor.create();
		this.changedSaplDocumentFluxSink = processor.sink();
		this.changedSaplDocumentFlux = processor;
		this.monitorPolicies = processor.subscribe();
	}

	private void initIndex() {
		Collection<SaplDocument> saplDocuments = this.saplDocumentService.getAll();

		this.indexLocker.lock();
		try {
			for (SaplDocument saplDocument : saplDocuments) {
				this.addSaplDocumentToIndexWithoutLock(saplDocument);
			}

			this.documentIndex.setLiveMode();
		} finally {
			this.indexLocker.unlock();
		}

		// fire initial event for PDP implementation
		this.changedSaplDocumentFluxSink.next(new SaplDocument());
	}

	private void addSaplDocumentToIndexWithoutLock(@NonNull SaplDocument saplDocument) {
		SAPL sapl = this.generateSAPLfromSaplDocument(saplDocument);
		if (sapl == null) {
			return;
		}

		String documentKey = CEServerPolicyRetrievalPoint.generateDocumentKeyFromSaplDocument(saplDocument);
		this.documentIndex.put(documentKey, sapl);
	}

	private void removeSaplDocumentFromIndexWithoutLock(@NonNull SaplDocument saplDocument) {
		String documentKey = CEServerPolicyRetrievalPoint.generateDocumentKeyFromSaplDocument(saplDocument);
		this.documentIndex.remove(documentKey);
	}

	private void addSaplDocumentToIndex(@NonNull SaplDocument saplDocument) {
		this.indexLocker.lock();

		try {
			this.addSaplDocumentToIndexWithoutLock(saplDocument);
		} finally {
			this.indexLocker.unlock();
		}
	}

	private void removeSaplDocumentFromIndex(@NonNull SaplDocument saplDocument) {
		this.indexLocker.lock();

		try {
			this.removeSaplDocumentFromIndexWithoutLock(saplDocument);
		} finally {
			this.indexLocker.unlock();
		}
	}

	private SAPL generateSAPLfromSaplDocument(@NonNull SaplDocument saplDocument) {
		SaplDocumentVersion publishedVersion = saplDocument.getPublishedVersion();
		if (publishedVersion == null) {
			return null;
		}

		String valueOfPublishedVersion = publishedVersion.getValue();
		try {
			return this.interpreter.parse(valueOfPublishedVersion);
		} catch (PolicyEvaluationException e) {
			log.warn("cannot parse value of published document (sapl document id: {}, version: {}, value: {})",
					saplDocument.getId(), publishedVersion.getVersionNumber(), valueOfPublishedVersion);
			return null;
		}
	}
}
