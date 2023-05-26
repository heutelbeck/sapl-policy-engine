/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.Getter;
import lombok.ToString;

@ToString
public class CombinedDecision implements Traced {

	public static final String EVALUATED_POLICIES     = "evaluatedPolicies";
	public static final String ERROR                  = "error";
	public static final String AUTHORIZATION_DECISION = "authorizationDecision";
	public static final String COMBINING_ALGORITHM    = "combiningAlgorithm";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Getter
	AuthorizationDecision          authorizationDecision;
	String                         combiningAlgorithm;
	List<DocumentEvaluationResult> documentEvaluationResults = new LinkedList<>();
	Optional<String>               errorMessage;

	private CombinedDecision(AuthorizationDecision authorizationDecision, String combiningAlgorithm,
			List<DocumentEvaluationResult> documentEvaluationResults, Optional<String> errorMessage) {
		this.authorizationDecision = authorizationDecision;
		this.combiningAlgorithm    = combiningAlgorithm;
		this.documentEvaluationResults.addAll(documentEvaluationResults);
		this.errorMessage = errorMessage;
		MAPPER.registerModule(new Jdk8Module());
	}

	public static CombinedDecision error(String combiningAlgorithm, String errorMessage) {
		return new CombinedDecision(AuthorizationDecision.INDETERMINATE, combiningAlgorithm, List.of(),
				Optional.ofNullable(errorMessage));
	}

	public static CombinedDecision of(AuthorizationDecision authorizationDecision, String combiningAlgorithm) {
		return new CombinedDecision(authorizationDecision, combiningAlgorithm, List.of(), Optional.empty());
	}

	public static CombinedDecision of(AuthorizationDecision authorizationDecision, String combiningAlgorithm,
			List<DocumentEvaluationResult> documentEvaluationResults) {
		return new CombinedDecision(authorizationDecision, combiningAlgorithm, documentEvaluationResults,
				Optional.empty());
	}

	public CombinedDecision withEvaluationResult(DocumentEvaluationResult result) {
		var newCombinedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults, errorMessage);
		newCombinedDecision.documentEvaluationResults.add(result);
		return newCombinedDecision;
	}

	public CombinedDecision withDecisionAndEvaluationResult(AuthorizationDecision authorizationDecision,
			DocumentEvaluationResult result) {
		var newCombinedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults, errorMessage);
		newCombinedDecision.documentEvaluationResults.add(result);
		return newCombinedDecision;
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set(COMBINING_ALGORITHM, Val.JSON.textNode(combiningAlgorithm));
		trace.set(AUTHORIZATION_DECISION, MAPPER.valueToTree(getAuthorizationDecision()));
		errorMessage.ifPresent(s -> trace.set(ERROR, Val.JSON.textNode(s)));
		trace.set(EVALUATED_POLICIES, listOfTracedToJsonArray(documentEvaluationResults));
		return trace;
	}

	private JsonNode listOfTracedToJsonArray(List<DocumentEvaluationResult> results) {
		var arrayNode = Val.JSON.arrayNode();
		results.forEach(r -> arrayNode.add(r.getTrace()));
		return arrayNode;
	}

}
