package io.sapl.reimpl.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.interpreter.functions.FunctionContext;
import reactor.core.publisher.Mono;

public interface ImmutableParsedDocumentIndex {

	Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables);

	ImmutableParsedDocumentIndex apply(PrpUpdateEvent event);

}