package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.Request;
import io.sapl.interpreter.functions.FunctionContext;

/**
 * A policy retrieval point is responsible for selecting all the policies matching
 * a given request.
 */
public interface PolicyRetrievalPoint {

    /**
     * Returns a policy retrieval result containing all the policies or policy sets
     * having a target expression that matches the given request. The given function
     * context and variables constitute the environment the target expressions are
     * evaluated in.
     *
     * @param request the request for which matching policies are to be retrieved.
     * @param functionCtx the function context being part of the target expression's
     *                    evaluation environment.
     * @param variables the variables being part of the target expression's evaluation
     *                  environment.
     * @return a policy retrieval result containing all the policies or policy sets
     *         matching the given request.
     */
	PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
                                           Map<String, JsonNode> variables);

}
