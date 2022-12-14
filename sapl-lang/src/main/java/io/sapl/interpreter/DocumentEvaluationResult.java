package io.sapl.interpreter;

import io.sapl.api.pdp.AuthorizationDecision;

public interface DocumentEvaluationResult extends Traced {
	AuthorizationDecision getAuthorizationDecision();
}
