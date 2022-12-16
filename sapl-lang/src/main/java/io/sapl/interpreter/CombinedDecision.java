package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.Getter;
import lombok.ToString;

@ToString
public class CombinedDecision implements Traced {

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
		var newCombindedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults, errorMessage);
		documentEvaluationResults.add(result);
		return newCombindedDecision;
	}

	public CombinedDecision withDecisionAndEvaluationResult(AuthorizationDecision authorizationDecision,
			DocumentEvaluationResult result) {
		var newCombindedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults, errorMessage);
		documentEvaluationResults.add(result);
		return newCombindedDecision;
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set("combiningAlgorithm", Val.JSON.textNode(combiningAlgorithm));
		trace.set("authoriyationDecision", MAPPER.valueToTree(getAuthorizationDecision()));
		if (errorMessage.isPresent())
			trace.set("error", Val.JSON.textNode(errorMessage.get()));
		trace.set("evaluatedPolicies", listOfTracedToJsonArray(documentEvaluationResults));
		return trace;
	}

	private JsonNode listOfTracedToJsonArray(List<DocumentEvaluationResult> results) {
		var arrayNode = Val.JSON.arrayNode();
		results.forEach(r -> arrayNode.add(r.getTrace()));
		return arrayNode;
	}

}
