package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyElement;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PolicyDecision implements DocumentEvaluationResult {

	final PolicyElement    document;
	final Decision         entitlement;
	final Optional<Val>    targetResult;
	final Optional<Val>    whereResult;
	final List<Val>        obligations = new LinkedList<>();
	final List<Val>        advice      = new LinkedList<>();
	final Optional<Val>    resource;
	final Optional<String> errorMessage;

	private PolicyDecision(PolicyElement document, Decision entitlement, Optional<Val> targetResult,
			Optional<Val> whereResult, List<Val> obligations, List<Val> advice, Optional<Val> resource,
			Optional<String> errorMessage) {
		this.document     = document;
		this.targetResult = targetResult;
		this.whereResult  = whereResult;
		this.resource     = resource;
		this.entitlement  = entitlement;
		this.errorMessage = errorMessage;
		this.obligations.addAll(obligations);
		this.advice.addAll(advice);
	}

	public static PolicyDecision ofTargetExpressionEvaluation(Policy policy, Val targetExpressionResult,
			Decision entitlement) {
		return new PolicyDecision(policy, entitlement, Optional.ofNullable(targetExpressionResult), Optional.empty(),
				List.of(), List.of(), Optional.empty(), Optional.empty());
	}

	public static PolicyDecision ofImportError(Policy policy, Decision entitlement, String errorMessage) {
		return new PolicyDecision(policy, entitlement, Optional.empty(), Optional.empty(), List.of(), List.of(),
				Optional.empty(), Optional.ofNullable(errorMessage));
	}

	public static PolicyDecision fromWhereResult(PolicyElement document, Decision entitlement, Val whereResult) {
		return new PolicyDecision(document, entitlement, Optional.empty(), Optional.ofNullable(whereResult), List.of(),
				List.of(), Optional.empty(), Optional.empty());
	}

	public PolicyDecision withObligation(Val obligation) {
		var policyDecison = new PolicyDecision(document, entitlement, targetResult, whereResult, obligations, advice,
				resource, errorMessage);
		policyDecison.obligations.add(obligation);
		return policyDecison;
	}

	public PolicyDecision withAdvice(Val advice) {
		var policyDecison = new PolicyDecision(document, entitlement, targetResult, whereResult, obligations,
				this.advice, resource, errorMessage);
		policyDecison.advice.add(advice);
		return policyDecison;
	}

	public PolicyDecision withResource(Val resource) {
		return new PolicyDecision(document, entitlement, targetResult, whereResult, obligations, advice,
				Optional.ofNullable(resource), errorMessage);
	}

	@Override
	public DocumentEvaluationResult withTargetResult(Val targetResult) {
		return new PolicyDecision(document, entitlement, Optional.ofNullable(targetResult), whereResult, obligations,
				advice, resource, errorMessage);
	}

	public AuthorizationDecision getAuthorizationDecision() {

		if (targetResult.isPresent() && targetResult.get().isBoolean() && !targetResult.get().getBoolean())
			return AuthorizationDecision.NOT_APPLICABLE;

		if (hasErrors() || whereResult.isEmpty())
			return AuthorizationDecision.INDETERMINATE;

		if (!whereResult.get().getBoolean())
			return AuthorizationDecision.NOT_APPLICABLE;

		var authzDecision = AuthorizationDecision.DENY;
		if (entitlement == Decision.PERMIT)
			authzDecision = AuthorizationDecision.PERMIT;

		authzDecision = authzDecision.withObligations(collectConstraints(obligations));
		authzDecision = authzDecision.withAdvice(collectConstraints(advice));
		if (resource.isPresent())
			authzDecision = authzDecision.withResource(resource.get().get());
		return authzDecision;
	}

	private boolean hasErrors() {
		return (targetResult.isPresent() && targetResult.get().isError())
				|| (getWhereResult().isPresent() && getWhereResult().get().isError())
				|| containsErrorOrUndefined(getObligations()) || containsErrorOrUndefined(getAdvice())
				|| (getResource().isPresent() && (getResource().get().isError() || getResource().get().isUndefined()));
	}

	private ArrayNode collectConstraints(List<Val> constraints) {
		var array = Val.JSON.arrayNode();
		for (var constraint : constraints) {
			array.add(constraint.get());
		}
		return array;
	}

	private boolean containsErrorOrUndefined(List<Val> values) {
		return values.stream().filter(val -> val.isError() || val.isUndefined()).findAny().isPresent();
	}

	@Override
	public String evaluationTree() {
		// TODO
		return "";
	}

	@Override
	public String report() {
		// TODO
		return "";
	}

	@Override
	public JsonNode jsonReport() {
		// TODO
		return Val.JSON.objectNode();
	}

}
