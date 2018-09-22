package io.sapl.api.prp;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;

public interface ParsedDocumentIndex extends PolicyRetrievalPoint, ReactivePolicyRetrievalPoint {

	void put(String documentKey, SAPL sapl);

	void remove(String documentKey);

	void updateFunctionContext(FunctionContext functionCtx);

	void setLiveMode();
}
