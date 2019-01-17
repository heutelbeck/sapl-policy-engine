package io.sapl.pip.http;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

@NoArgsConstructor
@PolicyInformationPoint(name = HttpPolicyInformationPoint.NAME, description = HttpPolicyInformationPoint.DESCRIPTION)
public class HttpPolicyInformationPoint {

	static final String NAME = "http";
	static final String DESCRIPTION = "Policy Information Point and attributes for consuming HTTP services";

	private static final String GET_DOCS = "Sends an HTTP GET request to the url provided in the value parameter and returns the response.";
	private static final String REACTIVE_GET_DOCS = "Sends an HTTP GET request to the url provided in the value parameter and returns a flux of responses.";
	private static final String POST_DOCS = "Sends an HTTP POST request to the url provided in the value parameter and returns the response.";
	private static final String REACTIVE_POST_DOCS = "Sends an HTTP POST request to the url provided in the value parameter and returns a flux of responses.";
	private static final String PUT_DOCS = "Sends an HTTP PUT request to the url provided in the value parameter and returns the response.";
	private static final String PATCH_DOCS = "Sends an HTTP PATCH request to the url provided in the value parameter and returns the response.";
	private static final String DELETE_DOCS = "Sends an HTTP DELETE request to the url provided in the value parameter and returns the response.";

	private static final String OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION = "Object no HTTP request object specification.";

	@Attribute(docs = GET_DOCS)
	public JsonNode get(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return HttpClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_GET);
	}

	@Attribute(reactive = true, docs = REACTIVE_GET_DOCS)
	public Flux<JsonNode> rget(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return WebClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_GET);
	}

	@Attribute(docs = POST_DOCS)
	public JsonNode post(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return HttpClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_POST);
	}

	@Attribute(reactive = true, docs = REACTIVE_POST_DOCS)
	public Flux<JsonNode> rpost(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return WebClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_GET);
	}

	@Attribute(docs = PUT_DOCS)
	public JsonNode put(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return HttpClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_PUT);
	}

	@Attribute(docs = PATCH_DOCS)
	public JsonNode patch(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return HttpClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_PATCH);
	}

	@Attribute(docs = DELETE_DOCS)
	public JsonNode delete(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final RequestSpecification saplRequest = getRequestSpecification(value);
		return HttpClientRequestExecutor.executeRequest(saplRequest, RequestSpecification.HTTP_DELETE);
	}

	private RequestSpecification getRequestSpecification(@JsonObject @Text JsonNode value) throws AttributeException {
		if (value.isTextual()) {
			final RequestSpecification saplRequest = new RequestSpecification();
			saplRequest.setUrl(value);
			return saplRequest;
		}
		else {
			try {
				return RequestSpecification.from(value);
			}
			catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
	}
}
