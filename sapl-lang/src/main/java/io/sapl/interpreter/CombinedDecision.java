package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CombinedDecision implements Traced {

	@Getter
	AuthorizationDecision          authorizationDecision;
	String                         combiningAlgorithm;
	List<DocumentEvaluationResult> documentEvaluationResults = new LinkedList<>();

	private CombinedDecision(AuthorizationDecision authorizationDecision, String combiningAlgorithm,
			List<DocumentEvaluationResult> documentEvaluationResults) {
		this.authorizationDecision = authorizationDecision;
		this.combiningAlgorithm    = combiningAlgorithm;
		this.documentEvaluationResults.addAll(documentEvaluationResults);
	}

	public static CombinedDecision of(AuthorizationDecision authorizationDecision, String combiningAlgorithm) {
		return new CombinedDecision(authorizationDecision, combiningAlgorithm, List.of());
	}

	public static CombinedDecision of(AuthorizationDecision authorizationDecision, String combiningAlgorithm,
			List<DocumentEvaluationResult> documentEvaluationResults) {
		return new CombinedDecision(authorizationDecision, combiningAlgorithm, documentEvaluationResults);
	}

	public CombinedDecision withEvaluationResult(DocumentEvaluationResult result) {
		var newCombindedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults);
		documentEvaluationResults.add(result);
		return newCombindedDecision;
	}

	public CombinedDecision withDecisionAndEvaluationResult(AuthorizationDecision authorizationDecision,
			DocumentEvaluationResult result) {
		var newCombindedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
				documentEvaluationResults);
		documentEvaluationResults.add(result);
		return newCombindedDecision;
	}

	@Override
	public String evaluationTree() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public String report() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public JsonNode jsonReport() {
		// TODO Auto-generated method stub
		return Val.JSON.objectNode();
	}

}
