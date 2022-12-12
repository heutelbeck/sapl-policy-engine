package io.sapl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public interface SAPLDecision {
	AuthorizationDecision getDecision();
	String evaluationTree();
	String report();
	JsonNode jsonReport();
}
