package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import io.sapl.api.pdp.Request;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableRequest {

	private String requestId;

	private Request request;

	public IdentifiableRequest(String requestId, Request request) {
		requireNonNull(requestId, "requestId must not be null");
		requireNonNull(request, "request must not be null");

		this.requestId = requestId;
		this.request = request;
	}

}
