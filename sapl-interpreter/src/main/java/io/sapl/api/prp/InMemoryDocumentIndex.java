package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Request;
import io.sapl.interpreter.functions.FunctionContext;

public interface InMemoryDocumentIndex {

	void insert(String documentKey, String document) throws PolicyEvaluationException;

	void publish(String documentKey);

	void unpublish(String documentKey);

	void updateFunctionContext(FunctionContext functionCtx);

	void setLiveMode();

	PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
										   Map<String, JsonNode> variables);

}
