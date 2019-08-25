package io.sapl.api.pdp.multirequest;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds a {@link Response SAPL response} together with the ID of
 * the corresponding {@link Request SAPL request}.
 *
 * @see io.sapl.api.pdp.Response
 * @see io.sapl.api.pdp.multirequest.IdentifiableRequest
 */
@Value
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class IdentifiableResponse {

	private String requestId;

	private Response response;

	public IdentifiableResponse() {
		requestId = null;
		response = null;
	}

	public static IdentifiableResponse INDETERMINATE = new IdentifiableResponse(null, Response.INDETERMINATE);

}
