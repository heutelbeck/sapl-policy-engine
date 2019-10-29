package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import reactor.core.publisher.Flux;

/**
 * A policy retrieval point is responsible for selecting all the policies matching a given
 * request.
 */
public interface PolicyRetrievalPoint {

	/**
	 * Returns a {@link Flux} of policy retrieval results containing all the policies or
	 * policy sets having a target expression that matches the given authorization
	 * subscription. The given function context and variables constitute the environment
	 * the target expressions are evaluated in.
	 * @param authzSubscription the authorization subscription for which matching policies
	 * are to be retrieved.
	 * @param functionCtx the function context being part of the target expression's
	 * evaluation environment.
	 * @param variables the variables being part of the target expression's evaluation
	 * environment.
	 * @return a {@link Flux} providing the policy retrieval results containing all the
	 * matching policies or policy sets. New results are only added to the stream if they
	 * are different from the preceding result.
	 */
	Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables);

}
