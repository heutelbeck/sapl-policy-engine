/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class SimpleParsedDocumentIndex implements ParsedDocumentIndex {

	Map<String, SAPL> publishedDocuments = new ConcurrentHashMap<>();

	@Override
	public Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		try {
			final VariableContext variableCtx = new VariableContext(authzSubscription, variables);
			final EvaluationContext evaluationCtx = new EvaluationContext(functionCtx, variableCtx);
			final AtomicBoolean errorInTarget = new AtomicBoolean(false);
			return Flux.fromIterable(publishedDocuments.values()).filterWhen(policy -> policy.matches(evaluationCtx))
					.onErrorContinue((t, o) -> {
						log.info("| |-- Error in target evaluation: {}", t.getMessage());
						errorInTarget.set(true);
					}).collectList().map(result -> new PolicyRetrievalResult(result, errorInTarget.get()));
		} catch (PolicyEvaluationException e) {
			return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
		}
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
