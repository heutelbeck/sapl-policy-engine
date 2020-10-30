package io.sapl.reimpl.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class GenericPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private Flux<ImmutableParsedDocumentIndex> index;
	private Disposable indexSubscription;

	public GenericPolicyRetrievalPoint(ImmutableParsedDocumentIndex seedIndex, PrpUpdateEventSource eventSource) {
		index = Flux.from(eventSource.getUpdates()).log().scan(seedIndex, (index, event) -> index.apply(event)).skip(1L)
				.cache().share();
		// initial subscription, so that the index directly starts building upon startup
		indexSubscription = index.subscribe();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return Flux.from(index).flatMap(idx -> idx.retrievePolicies(authzSubscription, functionCtx, variables))
				.doOnNext(this::logMatching);
	}

	@Override
	public void dispose() {
		indexSubscription.dispose();
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
