package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import lombok.Value;

/**
 * A multi-response holds a map of request IDs and corresponding {@link Response responses}.
 * It provides methods to {@link #setResponseForRequestWithId(String, Response)} add}
 * single responses related to a request ID, to {@link #getResponseForRequestWithId(String) get}
 * a single response for a given request ID and to {@link #iterator() iterate} over all the responses.
 *
 * @see io.sapl.api.pdp.Response
 */
@Value
public class MultiResponse implements Iterable<IdentifiableResponse> {

	@JsonInclude(NON_EMPTY)
	private Map<String, Response> responses = new HashMap<>();

	public static MultiResponse indeterminate() {
		final MultiResponse multiResponse = new MultiResponse();
		multiResponse.setResponseForRequestWithId("", Response.INDETERMINATE);
		return multiResponse;
	}

	/**
	 * @return the number of {@link Response responses} contained by this
	 *         multi-response.
	 */
	public int size() {
		return responses.size();
	}

	/**
	 * Adds the given tuple of request ID and related response to this multi-response.
	 *
	 * @param requestId the ID of the request related to the given response.
	 * @param response the response related to the request with the given ID.
	 */
	public void setResponseForRequestWithId(String requestId, Response response) {
		requireNonNull(requestId, "requestId must not be null");
		requireNonNull(response, "response must not be null");
		responses.put(requestId, response);
	}

	/**
	 * Retrieves the response related to the request with the given ID.
	 *
	 * @param requestId the ID of the request for which the related response
	 *                  has to be returned.
	 * @return the response related to the request with the given ID.
	 */
	public Response getResponseForRequestWithId(String requestId) {
		requireNonNull(requestId, "requestId must not be null");
		return responses.get(requestId);
	}

	/**
	 * Retrieves the authorization decision related to the request with the
	 * given ID.
	 *
	 * @param requestId the ID of the request for which the related
	 *                  authorization decision has to be returned.
	 * @return the authorization decision related to the request with the
	 *         given ID.
	 */
	public Decision getDecisionForRequestWithId(String requestId) {
		requireNonNull(requestId, "requestId must not be null");
		return responses.get(requestId).getDecision();
	}

	/**
	 * Returns {@code true} if the authorization decision related to the request
	 * with the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 *
	 * @param requestId the ID of the request for which the related flag
	 *                  indicating whether the authorization decision was
	 *                  PERMIT or not has to be returned.
	 * @return {@code true} if the authorization decision related to the request
	 *         with the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean isAccessPermittedForRequestWithId(String requestId) {
		requireNonNull(requestId, "requestId must not be null");
		return responses.get(requestId).getDecision() == Decision.PERMIT;
	}

	/**
	 * @return an {@link Iterator iterator} providing access to the
	 *         {@link IdentifiableResponse identifiable responses} created
	 *         from the data held by this multi-response.
	 */
	@Override
	public Iterator<IdentifiableResponse> iterator() {
		final Iterator<Map.Entry<String, Response>> responseIterator = responses
				.entrySet().iterator();
		return new Iterator<IdentifiableResponse>() {
			@Override
			public boolean hasNext() {
				return responseIterator.hasNext();
			}

			@Override
			public IdentifiableResponse next() {
				final Map.Entry<String, Response> responseEntry = responseIterator.next();
				return new IdentifiableResponse(responseEntry.getKey(),
						responseEntry.getValue());
			}
		};
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		final MultiResponse other = (MultiResponse) obj;

		final Map<String, Response> otherResponses = other.responses;
		if (responses.size() != otherResponses.size()) {
			return false;
		}

		final Set<String> thisKeys = responses.keySet();
		final Set<String> otherKeys = otherResponses.keySet();
		for (String key : thisKeys) {
			if (!otherKeys.contains(key)) {
				return false;
			}
			if (!Objects.equals(responses.get(key), otherResponses.get(key))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		for (Response response : responses.values()) {
			result = result * PRIME + (response == null ? 43 : response.hashCode());
		}
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiResponse {");
		for (IdentifiableResponse response : this) {
			sb.append("\n\t[").append("REQ-ID: ").append(response.getRequestId())
					.append(" | ").append("DECISION: ")
					.append(response.getResponse().getDecision()).append(" | ")
					.append("RESOURCE: ").append(response.getResponse().getResource())
					.append(" | ").append("OBLIGATIONS: ")
					.append(response.getResponse().getObligations()).append(" | ")
					.append("ADVICE: ").append(response.getResponse().getAdvices())
					.append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

}
