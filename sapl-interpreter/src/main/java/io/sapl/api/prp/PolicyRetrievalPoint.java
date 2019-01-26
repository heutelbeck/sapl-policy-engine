package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.Request;
import io.sapl.interpreter.functions.FunctionContext;
import reactor.core.publisher.Flux;

/**
 * A policy retrieval point is responsible for selecting all the policies matching
 * a given request.
 */
public interface PolicyRetrievalPoint {

    /**
     * Returns a {@link Flux} of policy retrieval results containing all the policies
     * or policy sets having a target expression that matches the given request. The
     * given function context and variables constitute the environment the target
     * expressions are evaluated in.
     *
     * @param request the request for which matching policies are to be retrieved.
     * @param functionCtx the function context being part of the target expression's
     *                    evaluation environment.
     * @param variables the variables being part of the target expression's evaluation
     *                  environment.
     * @return a {@link Flux} providing the policy retrieval results containing all the
     *         matching policies or policy sets. New results are only added to the stream
     *         if they are different from the preceding result.
     */
    Flux<PolicyRetrievalResult> retrievePolicies(Request request, FunctionContext functionCtx,
                                                 Map<String, JsonNode> variables);

    /**
     * When clients of a policy retrieval point no longer need it, they should call
     * {@code dispose()} to give it the chance to clean up resources like subscriptions,
     * threads, directory watcher service registrations etc.
     */
    void dispose();

}
