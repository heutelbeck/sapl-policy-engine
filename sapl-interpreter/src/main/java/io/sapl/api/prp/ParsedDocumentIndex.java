package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;

public interface ParsedDocumentIndex {

	void put(String documentKey, SAPL sapl);

	void remove(String documentKey);

	void updateFunctionContext(FunctionContext functionCtx);

	void setLiveMode();

	PolicyRetrievalResult retrievePolicies(AuthSubscription authSubscription, FunctionContext functionCtx,
			Map<String, JsonNode> variables);

}
