package io.sapl.prp.inmemory.simple;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.InMemoryDocumentIndex;
import io.sapl.api.prp.ParsedDocumentPolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;

public class NaiveInMemoryDocumentIndex implements InMemoryDocumentIndex {
	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	Map<String, SAPL> parsedDocuments = new ConcurrentHashMap<>();
	ParsedDocumentPolicyRetrievalPoint index = new SimpleParsedDocumentIndex();

	@Override
	public PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return index.retrievePolicies(request, functionCtx, variables);
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
