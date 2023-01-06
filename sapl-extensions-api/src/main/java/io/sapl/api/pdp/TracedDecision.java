package io.sapl.api.pdp;

import io.sapl.api.interpreter.Traced;

public interface TracedDecision extends Traced {
	AuthorizationDecision getAuthorizationDecision();

	TracedDecision modified(AuthorizationDecision authzDecision, String explanation);
}
