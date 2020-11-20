package io.sapl.reimpl.prp;

import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class GenericInMemoryIndexedPolicyRetrievalPoint implements PolicyRetrievalPoint, Disposable {

	private Flux<ImmutableParsedDocumentIndex> index;
	private Disposable indexSubscription;
	private PrpUpdateEventSource eventSource;

	public GenericInMemoryIndexedPolicyRetrievalPoint(ImmutableParsedDocumentIndex seedIndex,
			PrpUpdateEventSource eventSource) {
		this.eventSource = eventSource;
		index = Flux.from(eventSource.getUpdates()).scan(seedIndex, (index, event) -> index.apply(event)).skip(1L)
				.share().cache();
		// initial subscription, so that the index starts building upon startup
		indexSubscription = index.subscribe();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
		return Flux.from(index).flatMap(idx -> idx.retrievePolicies(subscriptionScopedEvaluationContext))
				.doOnNext(this::logMatching);
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
			for (AuthorizationDecisionEvaluable doc : result.getMatchingDocuments()) {
				log.trace("| |-- * {} ({})",
						(doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getSaplName() : doc.toString(),
						(doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getClass().getSimpleName()
								: doc.toString());
			}
		}
		log.trace("|");
	}

}
