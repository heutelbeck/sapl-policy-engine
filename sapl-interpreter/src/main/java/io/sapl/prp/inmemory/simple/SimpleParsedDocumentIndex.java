package io.sapl.prp.inmemory.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;

public class SimpleParsedDocumentIndex implements ParsedDocumentIndex {
	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	Map<String, SAPL> publishedDocuments = new ConcurrentHashMap<>();

	@Override
	public PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		boolean errorOccured = false;
		List<SAPL> result = new ArrayList<>();
		for (Entry<String, SAPL> entry : publishedDocuments.entrySet()) {
			try {
				if (INTERPRETER.matches(request, entry.getValue(), functionCtx, variables)) {
					result.add(entry.getValue());
				}
			} catch (PolicyEvaluationException e) {
				errorOccured = true;
			}
		}
		return new PolicyRetrievalResult(result, errorOccured);

	}

	@Override
	public void put(String documentKey, SAPL sapl) {
		publishedDocuments.put(documentKey, sapl);
	}

	@Override
	public void remove(String documentKey) {
		publishedDocuments.remove(documentKey);
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
