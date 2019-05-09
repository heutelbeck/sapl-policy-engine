package io.sapl.api.pdp.multirequest;

import io.sapl.api.pdp.Decision;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class IdentifiableDecision {

	private String requestId;

	private Decision decision;

	public IdentifiableDecision(IdentifiableResponse identifiableResponse) {
		requestId = identifiableResponse.getRequestId();
		decision = identifiableResponse.getResponse().getDecision();
	}

}
