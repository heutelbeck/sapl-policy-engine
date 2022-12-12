package io.sapl.interpreter;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class PolicySetDecision implements SAPLDecision {
	final AuthorizationDecision decision;
	final String                documentName;
	final Optional<Val>         matches;
	final Optional<String>      combiningAlgorithm;
	final List<PolicyDecision>  policyDecisions = new LinkedList<>();
	final Optional<String>      errorMessage;

	private PolicySetDecision(String documentName, String errorMessage) {
		this.decision           = AuthorizationDecision.INDETERMINATE;
		this.documentName       = documentName;
		this.matches            = Optional.empty();
		this.combiningAlgorithm = Optional.empty();
		this.errorMessage       = Optional.ofNullable(errorMessage);
	}

	private PolicySetDecision(String documentName, AuthorizationDecision decision) {
		this.decision           = decision;
		this.matches            = Optional.empty();
		this.documentName       = documentName;
		this.combiningAlgorithm = Optional.empty();
		this.errorMessage       = Optional.empty();
	}

	public static PolicySetDecision error(String documentName, String errorMessage) {
		return new PolicySetDecision(documentName, errorMessage);
	}

	public static PolicySetDecision of(String documentName, AuthorizationDecision decision) {
		return new PolicySetDecision(documentName, decision);
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
