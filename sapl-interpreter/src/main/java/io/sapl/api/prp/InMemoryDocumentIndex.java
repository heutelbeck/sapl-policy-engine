package io.sapl.api.prp;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;

public interface InMemoryDocumentIndex extends PolicyRetrievalPoint {

	void insert(String documentKey, String document) throws PolicyEvaluationException;

	void publish(String documentKey);

	void unpublish(String documentKey);

	void updateFunctionContext(FunctionContext functionCtx);

	void setLiveMode();

}
