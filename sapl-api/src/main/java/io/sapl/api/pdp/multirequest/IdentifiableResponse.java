package io.sapl.api.pdp.multirequest;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.Response;
import lombok.AllArgsConstructor;
import lombok.Value;

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
