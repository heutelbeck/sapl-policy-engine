package io.sapl.interpreter;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;

public interface DocumentEvaluationResult extends Traced {
	AuthorizationDecision getAuthorizationDecision();
	DocumentEvaluationResult withTargetResult(Val targetResult);
}
