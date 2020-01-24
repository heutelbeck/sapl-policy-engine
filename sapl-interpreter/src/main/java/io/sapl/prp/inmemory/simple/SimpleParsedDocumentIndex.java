/**
 * Copyright © 2017 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class SimpleParsedDocumentIndex implements ParsedDocumentIndex {

	Map<String, SAPL> publishedDocuments = new ConcurrentHashMap<>();

	@Override
	public PolicyRetrievalResult retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {

		final List<SAPL> result = new ArrayList<>();
		boolean errorOccurred = false;

		EvaluationContext evaluationCtx = null;
		try {
			final VariableContext variableCtx = new VariableContext(authzSubscription, variables);
			evaluationCtx = new EvaluationContext(functionCtx, variableCtx);
		}
		catch (PolicyEvaluationException e) {
			errorOccurred = true;
		}
		if (!errorOccurred) {
			for (SAPL sapl : publishedDocuments.values()) {
				try {
					if (sapl.matches(evaluationCtx)) {
						result.add(sapl);
					}
				}
				catch (PolicyEvaluationException e) {
					errorOccurred = true;
				}
			}
		}
		return new PolicyRetrievalResult(result, errorOccurred);
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
