package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class PolicyDecision implements SAPLDecision {

	final AuthorizationDecision decision;

	final String           documentName;
	final Decision         entitlement;
	final Optional<Val>    targetResult;
	final Optional<Val>    whereResult;
	final List<Val>        obligations = new LinkedList<>();
	final List<Val>        advice      = new LinkedList<>();
	final Optional<Val>    resource;
	final Optional<String> error;

	private PolicyDecision(AuthorizationDecision decision, String documentName, Decision entitlement,
			Optional<Val> targetResult, Optional<Val> whereResult, List<Val> obligations, List<Val> advice,
			Optional<Val> resource, Optional<String> error) {
		this.decision     = decision;
		this.documentName = documentName;
		this.entitlement  = entitlement;
		this.targetResult = targetResult;
		this.whereResult  = whereResult;
		this.obligations.addAll(obligations);
		this.advice.addAll(advice);
		this.resource = resource;
		this.error    = error;
	}

	public PolicyDecision(String documentName, Decision entitlement, Val where) {
		this.documentName = documentName;
		this.entitlement  = entitlement;
		this.whereResult  = Optional.ofNullable(where);
		this.decision     = null;
		this.targetResult = Optional.empty();
		this.resource     = Optional.empty();
		this.error        = Optional.empty();
	}

	public PolicyDecision(String documentName, String errorMessage) {
		this.documentName = documentName;
		this.entitlement  = Decision.INDETERMINATE;
		this.whereResult  = Optional.empty();
		this.decision     = AuthorizationDecision.INDETERMINATE;
		this.targetResult = Optional.empty();
		this.resource     = Optional.empty();
		this.error        = Optional.of(errorMessage);
	}

	public static PolicyDecision of(String documentName, Decision entitlement, Val where) {
		return new PolicyDecision(documentName, entitlement, where);
	}

	public static PolicyDecision error(String documentName, String errorMessage) {
		return new PolicyDecision(documentName, errorMessage);
	}

	public PolicyDecision withObligation(Val obligation) {
		var authzDecison = new PolicyDecision(decision, documentName, entitlement, targetResult, whereResult,
				obligations, advice, resource, error);
		authzDecison.obligations.add(obligation);
		return authzDecison;
	}

	public PolicyDecision withAdvice(Val advice) {
		var authzDecison = new PolicyDecision(decision, documentName, entitlement, targetResult, whereResult,
				obligations, this.advice, resource, error);
		authzDecison.advice.add(advice);
		return authzDecison;
	}

	public PolicyDecision withResource(Val resource) {
		return new PolicyDecision(decision, documentName, entitlement, targetResult, whereResult, obligations, advice,
				Optional.ofNullable(resource), error);
	}

	public PolicyDecision withDecision(AuthorizationDecision newDecision) {
		return new PolicyDecision(newDecision, documentName, entitlement, targetResult, whereResult, obligations,
				advice, resource, error);
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
