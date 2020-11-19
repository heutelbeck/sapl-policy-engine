package io.sapl.reimpl.prp;

import java.util.Map;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import reactor.core.publisher.Mono;

public interface ImmutableParsedDocumentIndex {

	Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext);

	ImmutableParsedDocumentIndex apply(PrpUpdateEvent event);

	default void applyEvent(Map<String, SAPL> newDocuments, PrpUpdateEvent event) {
		for (var update : event.getUpdates()) {
			applyUpdate(newDocuments, update);
		}
	}

	private void applyUpdate(Map<String, SAPL> newDocuments, Update update) {
		var name = update.getDocument().getPolicyElement().getSaplName();
		if (update.getType() == Type.UNPUBLISH) {
			newDocuments.remove(name);
		} else {
			if (newDocuments.containsKey(name)) {
				throw new RuntimeException("Fatal error. Policy name collision. A document with a name ('" + name
						+ "') identical to an existing document was published to the PRP.");
			}
			newDocuments.put(name, update.getDocument());
		}
	}

}