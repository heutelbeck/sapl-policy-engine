/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * Uses the {@link WebClientRequestExecutor} the send reactive HTTP requests to
 * a remote policy information point providing the according REST endpoints.
 */
@NoArgsConstructor
@PolicyInformationPoint(name = HttpPolicyInformationPoint.NAME, description = HttpPolicyInformationPoint.DESCRIPTION)
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
	 * 
	 * @param requestExecutor the {@link WebClientRequestExecutor} mock
	 */
	HttpPolicyInformationPoint(WebClientRequestExecutor requestExecutor) {
		this.requestExecutor = requestExecutor;
	}

	@Attribute(docs = GET_DOCS)
	public Flux<Val> get(@Text @JsonObject Val value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, GET);
	}

	@Attribute(docs = POST_DOCS)
	public Flux<Val> post(@Text @JsonObject Val value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, POST);
	}

	@Attribute(docs = PUT_DOCS)
	public Flux<Val> put(@Text @JsonObject Val value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, PUT);
	}

	@Attribute(docs = PATCH_DOCS)
	public Flux<Val> patch(@Text @JsonObject Val value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, PATCH);
	}

	@Attribute(docs = DELETE_DOCS)
	public Flux<Val> delete(@Text @JsonObject Val value, Map<String, JsonNode> variables) {
		return executeReactiveRequest(value, DELETE);
	}

	private Flux<Val> executeReactiveRequest(Val value, HttpMethod httpMethod) {
		try {
			final RequestSpecification saplRequest = getRequestSpecification(value.get());
			return getRequestExecutor().executeReactiveRequest(saplRequest, httpMethod)
					.onErrorMap(IOException.class, AttributeException::new).map(Val::of);
		} catch (AttributeException e) {
			return Flux.error(e);
		}
	}

	private RequestSpecification getRequestSpecification(JsonNode value) throws AttributeException {
		if (value.isTextual()) {
			final RequestSpecification saplRequest = new RequestSpecification();
			saplRequest.setUrl(value);
			return saplRequest;
		} else {
			try {
				return RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
	}

	private WebClientRequestExecutor getRequestExecutor() {
		return requestExecutor != null ? requestExecutor : new WebClientRequestExecutor();
	}

}
