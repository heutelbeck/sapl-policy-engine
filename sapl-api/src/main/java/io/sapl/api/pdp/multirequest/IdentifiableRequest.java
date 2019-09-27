package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import lombok.Getter;
import lombok.ToString;

/**
 * Holds a {@link Request SAPL request} together with an ID used to identify the request
 * and to assign the request its corresponding {@link Response SAPL response}.
 *
 * @see io.sapl.api.pdp.Request
 * @see io.sapl.api.pdp.multirequest.IdentifiableResponse
 */
@Getter
@ToString
public class IdentifiableRequest {

	private String requestId;

	private Request request;

	/**
	 * Creates a new {@code IdentifiableRequest} instance holding the given request ID and
	 * request.
	 * @param requestId the ID assigned to the given request. Must not be {@code null}.
	 * @param request the request assigned to the given ID. Must not be {@code null}.
	 */
	public IdentifiableRequest(String requestId, Request request) {
		requireNonNull(requestId, "requestId must not be null");
		requireNonNull(request, "request must not be null");

		this.requestId = requestId;
		this.request = request;
	}

}
