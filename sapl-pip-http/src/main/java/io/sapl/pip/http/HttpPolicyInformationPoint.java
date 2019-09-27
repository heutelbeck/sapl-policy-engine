package io.sapl.pip.http;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Uses the {@link WebClientRequestExecutor} the send reactive HTTP requests to a remote
 * policy information point providing the according REST endpoints.
 */
@NoArgsConstructor
@PolicyInformationPoint(name = HttpPolicyInformationPoint.NAME, description = HttpPolicyInformationPoint.DESCRIPTION)
@Slf4j
public class HttpPolicyInformationPoint {

	static final String NAME = "http";
	static final String DESCRIPTION = "Policy Information Point and attributes for consuming HTTP services";

	private static final String GET_DOCS = "Sends an HTTP GET request to the url provided in the value parameter and returns a flux of responses.";

	private static final String POST_DOCS = "Sends an HTTP POST request to the url provided in the value parameter and returns a flux of responses.";

	private static final String PUT_DOCS = "Sends an HTTP PUT request to the url provided in the value parameter and returns a flux of responses.";

	private static final String PATCH_DOCS = "Sends an HTTP PATCH request to the url provided in the value parameter and returns a flux of responses.";

	private static final String DELETE_DOCS = "Sends an HTTP DELETE request to the url provided in the value parameter and returns a flux of responses.";

	private static final String OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION = "Object no HTTP request object specification.";

	private WebClientRequestExecutor requestExecutor;

	/**
	 * For Unit tests only.
	 * @param requestExecutor the {@link WebClientRequestExecutor} mock
	 */
	HttpPolicyInformationPoint(WebClientRequestExecutor requestExecutor) {
		this.requestExecutor = requestExecutor;
	}

	@Attribute(docs = GET_DOCS)
	public Flux<JsonNode> get(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, GET)
				.doOnNext(jsonNode -> LOGGER.trace("http.get({}) returned {}", value, jsonNode));
	}

	@Attribute(docs = POST_DOCS)
	public Flux<JsonNode> post(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, POST)
				.doOnNext(jsonNode -> LOGGER.trace("http.post({}) returned {}", value, jsonNode));
	}

	@Attribute(docs = PUT_DOCS)
	public Flux<JsonNode> put(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, PUT)
				.doOnNext(jsonNode -> LOGGER.trace("http.put({}) returned {}", value, jsonNode));
	}

	@Attribute(docs = PATCH_DOCS)
	public Flux<JsonNode> patch(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, PATCH)
				.doOnNext(jsonNode -> LOGGER.trace("http.patch({}) returned {}", value, jsonNode));
	}

	@Attribute(docs = DELETE_DOCS)
	public Flux<JsonNode> delete(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, DELETE)
				.doOnNext(jsonNode -> LOGGER.trace("http.delete({}) returned {}", value, jsonNode));
	}

	private Flux<JsonNode> executeReactiveRequest(@JsonObject @Text JsonNode value, HttpMethod httpMethod) {
		try {
			final RequestSpecification saplRequest = getRequestSpecification(value);
			return getRequestExecutor().executeReactiveRequest(saplRequest, httpMethod).onErrorMap(IOException.class,
					AttributeException::new);
		}
		catch (AttributeException e) {
			return Flux.error(e);
		}
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

	private WebClientRequestExecutor getRequestExecutor() {
		return requestExecutor != null ? requestExecutor : new WebClientRequestExecutor();
	}

}
