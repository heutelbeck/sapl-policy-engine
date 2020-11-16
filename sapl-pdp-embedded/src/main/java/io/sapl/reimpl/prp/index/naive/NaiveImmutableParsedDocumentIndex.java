package io.sapl.reimpl.prp.index.naive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@Slf4j
@ToString
public class NaiveImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {
	// Mapping of Document Name to the parsed Document
	private final Map<String, SAPL> documents;

	public NaiveImmutableParsedDocumentIndex() {
		documents = new HashMap<>();
	}

	private NaiveImmutableParsedDocumentIndex(Map<String, SAPL> documents) {
		this.documents = documents;
	}

	@Override
	public Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(authzSubscription, variables);
		} catch (PolicyEvaluationException e) {
			return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
		}
		var evaluationCtx = new EvaluationContext(functionCtx, variableCtx);

		var retrieval = Mono.just(new PolicyRetrievalResult());
		for (SAPL document : documents.values()) {
			retrieval = retrieval.flatMap(decision -> {
				return document.matches(evaluationCtx).map(match -> {
					if (match.isError()) {
						return decision.withError();
					}
					if (!match.isBoolean()) {
						log.error("matching returned error. (Should never happen): {}", match.getMessage());
						return decision.withError();
					}
					if (match.getBoolean()) {
						return decision.withMatch(document);
					}
					return decision;
				});
			});
		}
		return retrieval;
	}

	@Override
	public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
		// Do a shallow copy. String is immutable, and SAPL is assumed to be too.
		var newDocuments = new HashMap<>(documents);
		applyEvent(newDocuments, event);
		return new NaiveImmutableParsedDocumentIndex(newDocuments);
	}

}
