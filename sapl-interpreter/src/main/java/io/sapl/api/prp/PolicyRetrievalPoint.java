package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.Request;
import io.sapl.interpreter.functions.FunctionContext;

public interface PolicyRetrievalPoint {

	PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx, Map<String, JsonNode> variables);

}
