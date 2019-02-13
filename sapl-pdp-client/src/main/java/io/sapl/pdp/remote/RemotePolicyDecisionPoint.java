package io.sapl.pdp.remote;

import static io.sapl.webclient.URLSpecification.HTTPS_SCHEME;
import static org.springframework.http.HttpMethod.POST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String PDP_PATH_SINGLE_REQUEST = "/api/pdp/decide";
	private static final String PDP_PATH_MULTI_REQUEST = "/api/pdp/multi-decide";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private String host;
	private int port;

	public RemotePolicyDecisionPoint(String host, int port) {
		this.host = host;
		this.port = port;

		MAPPER.registerModule(new Jdk8Module());
	}

	@Override
	public Flux<Response> decide(Object subject, Object action, Object resource) {
		return decide(subject, action, resource, null);
	}

	@Override
	public Flux<Response> decide(Object subject, Object action, Object resource, Object environment) {
		final Request request = toRequest(subject, action, resource, environment);
		return decide(request);
	}

	@Override
	public Flux<Response> decide(Request request) {
		final RequestSpecification saplRequest = getRequestSpecification(request);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> MAPPER.convertValue(jsonNode, Response.class));
	}

	@Override
	public Flux<IdentifiableResponse> decide(MultiRequest multiRequest) {
		final RequestSpecification saplRequest = getRequestSpecification(multiRequest);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> MAPPER.convertValue(jsonNode, IdentifiableResponse.class));
	}

	private static Request toRequest(Object subject, Object action, Object resource, Object environment) {
		return new Request(
				MAPPER.convertValue(subject, JsonNode.class),
				MAPPER.convertValue(action, JsonNode.class),
				MAPPER.convertValue(resource, JsonNode.class),
				MAPPER.convertValue(environment, JsonNode.class)
		);
	}

	private RequestSpecification getRequestSpecification(Request request) {
		return getRequestSpecification(request, PDP_PATH_SINGLE_REQUEST);
	}

	private RequestSpecification getRequestSpecification(MultiRequest multiRequest) {
		return getRequestSpecification(multiRequest, PDP_PATH_MULTI_REQUEST);
	}

	private RequestSpecification getRequestSpecification(Object request, String urlPath) {
		final String url = HTTPS_SCHEME + "://" + host + ":" + port + urlPath;
		final RequestSpecification saplRequest = new RequestSpecification();
		saplRequest.setUrl(JSON.textNode(url));
		saplRequest.setBody(MAPPER.convertValue(request, JsonNode.class));
		return saplRequest;
	}

	@Override
	public void dispose() {
		// ignored
	}
}
