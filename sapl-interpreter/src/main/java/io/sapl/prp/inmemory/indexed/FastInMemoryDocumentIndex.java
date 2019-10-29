package io.sapl.prp.inmemory.indexed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.InMemoryDocumentIndex;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;

public class FastInMemoryDocumentIndex implements InMemoryDocumentIndex {

	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	ParsedDocumentIndex index = new FastParsedDocumentIndex();

	Map<String, SAPL> parsedDocuments = new ConcurrentHashMap<>();

	AtomicBoolean live = new AtomicBoolean(false);

	@Override
	public void insert(String documentKey, String document) throws PolicyEvaluationException {
		parsedDocuments.put(documentKey, INTERPRETER.parse(document));
	}

	@Override
	public void publish(String documentKey) {
		index.put(documentKey, parsedDocuments.get(documentKey));
	}

	@Override
	public PolicyRetrievalResult retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		if (live.get()) {
			return index.retrievePolicies(authzSubscription, functionCtx, variables);
		}
		else {
			throw new IndexStillInReplayMode();
		}
	}

	@Override
	public void unpublish(String documentKey) {
		index.remove(documentKey);
	}

	@Override
	public void updateFunctionContext(FunctionContext functionCtx) {
		index.updateFunctionContext(functionCtx);
	}

	@Override
	public void setLiveMode() {
		live.set(true);
	}

}
