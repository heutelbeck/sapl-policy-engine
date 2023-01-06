package io.sapl.interpreter;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.PolicyElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class PolicySetDecision implements DocumentEvaluationResult {

	final CombinedDecision combinedDecision;
	final PolicyElement    document;
	final Optional<Val>    targetResult;
	final Optional<String> errorMessage;

	private PolicySetDecision(CombinedDecision combinedDecision, PolicyElement document, Optional<Val> matches,
			Optional<String> errorMessage) {
		this.combinedDecision = combinedDecision;
		this.document         = document;
		this.targetResult     = matches;
		this.errorMessage     = errorMessage;
	}

	public static PolicySetDecision of(CombinedDecision combinedDecision, PolicyElement document) {
		return new PolicySetDecision(combinedDecision, document, Optional.empty(), Optional.empty());
	}

	public static PolicySetDecision error(PolicyElement document, String errorMessage) {
		return new PolicySetDecision(null, document, Optional.empty(), Optional.ofNullable(errorMessage));
	}

	public static PolicySetDecision ofTargetError(PolicyElement document, Val targetValue, String combiningAlgorithm) {
		return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.INDETERMINATE, combiningAlgorithm),
				document, Optional.ofNullable(targetValue), Optional.empty());
	}

	public static PolicySetDecision notApplicable(PolicyElement document, Val targetValue, String combiningAlgorithm) {
		return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.NOT_APPLICABLE, combiningAlgorithm),
				document, Optional.ofNullable(targetValue), Optional.empty());
	}

	public static DocumentEvaluationResult ofImportError(PolicyElement document, String errorMessage,
			String combiningAlgorithm) {
		return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.INDETERMINATE, combiningAlgorithm),
				document, Optional.empty(), Optional.ofNullable(errorMessage));
	}

	@Override
	public DocumentEvaluationResult withTargetResult(Val targetResult) {
		return new PolicySetDecision(combinedDecision, document, Optional.ofNullable(targetResult), errorMessage);
	}

	@Override
	public AuthorizationDecision getAuthorizationDecision() {
		if (errorMessage.isPresent())
			return AuthorizationDecision.INDETERMINATE;

		return combinedDecision.getAuthorizationDecision();
	}

	@Override
	public JsonNode getTrace() {
		var trace = Val.JSON.objectNode();
		trace.set("documentType", Val.JSON.textNode("policy set"));
		trace.set("policySetName", Val.JSON.textNode(document.getSaplName()));
		trace.set("combinedDecision", combinedDecision.getTrace());
		errorMessage.ifPresent(error -> trace.set("error", Val.JSON.textNode(errorMessage.get())));
		targetResult.ifPresent(target -> trace.set("target", target.getTrace()));
		return trace;
	}

}
