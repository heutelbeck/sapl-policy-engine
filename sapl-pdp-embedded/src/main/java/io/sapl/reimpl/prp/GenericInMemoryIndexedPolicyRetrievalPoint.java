package io.sapl.reimpl.prp;

import java.util.Map;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Slf4j
public class GenericInMemoryIndexedPolicyRetrievalPoint implements PolicyRetrievalPoint, Disposable {

	private Flux<ImmutableParsedDocumentIndex> index;
	private Disposable indexSubscription;
	private PrpUpdateEventSource eventSource;

	public GenericInMemoryIndexedPolicyRetrievalPoint(ImmutableParsedDocumentIndex seedIndex,
			PrpUpdateEventSource eventSource) {
		this.eventSource = eventSource;
		index = Flux.from(eventSource.getUpdates()).log(null, Level.FINE, SignalType.ON_NEXT)
				.scan(seedIndex, (index, event) -> index.apply(event)).log(null, Level.FINE, SignalType.ON_NEXT)
				.skip(1L).log(null, Level.FINE, SignalType.ON_NEXT).share().cache();
		// initial subscription, so that the index starts building upon startup
		indexSubscription = index.subscribe();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return Flux.from(index).log(null, Level.FINE, SignalType.ON_NEXT)
				.flatMap(idx -> idx.retrievePolicies(authzSubscription, functionCtx, variables))
				.log(null, Level.FINE, SignalType.ON_NEXT).doOnNext(this::logMatching);
	}

	@Override
	public void dispose() {
		indexSubscription.dispose();
		eventSource.dispose();
	}

	private void logMatching(PolicyRetrievalResult result) {
		if (result.getMatchingDocuments().isEmpty()) {
			log.trace("|-- Matching documents: NONE");
		} else {
			log.trace("|-- Matching documents:");
			for (SAPL doc : result.getMatchingDocuments()) {
				log.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(),
						doc.getPolicyElement().getClass().getName());
			}
		}
		log.trace("|");
	}

}
