package io.sapl.prp.inmemory.simple;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.prp.InMemoryDocumentIndex;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;

public class SimpleInMemoryDocumentIndex implements InMemoryDocumentIndex {

	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	Map<String, SAPL> parsedDocuments = new ConcurrentHashMap<>();

	ParsedDocumentIndex index = new SimpleParsedDocumentIndex();

	@Override
	public PolicyRetrievalResult retrievePolicies(AuthSubscription authSubscription, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return index.retrievePolicies(authSubscription, functionCtx, variables);
	}

	@Override
	public void insert(String documentKey, String document) throws PolicyEvaluationException {
		parsedDocuments.put(documentKey, INTERPRETER.parse(document));
	}

	@Override
	public void publish(String documentKey) {
		index.put(documentKey, parsedDocuments.get(documentKey));
	}

	@Override
	public void unpublish(String documentKey) {
		index.remove(documentKey);
	}

	@Override
	public void updateFunctionContext(FunctionContext functionCtx) {
		// NOP
	}

	@Override
	public void setLiveMode() {
		// NOP
	}

}
