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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String PDP_PATH_SINGLE_REQUEST = "/api/pdp/decide";
	private static final String PDP_PATH_MULTI_REQUEST = "/api/pdp/multi-decide";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private String host;
	private int port;
	private ObjectMapper mapper;

	public RemotePolicyDecisionPoint(String host, int port) {
		this.host = host;
		this.port = port;
		mapper = new ObjectMapper();
		mapper.registerModule(new Jdk8Module());
	}

	public RemotePolicyDecisionPoint(String host, int port, ObjectMapper mapper) {
		this.host = host;
		this.port = port;
		this.mapper = mapper;
	}

	@Override
	public Flux<Response> subscribe(Request request) {
		final RequestSpecification saplRequest = getRequestSpecification(request);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, Response.class));
	}

	@Override
	public Flux<IdentifiableResponse> subscribe(MultiRequest multiRequest) {
		final RequestSpecification saplRequest = getRequestSpecification(multiRequest);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, IdentifiableResponse.class));
	}

	@Override
	public Mono<Response> decide(Request request) {
		return subscribe(request).next();
	}

	@Override
	public Mono<IdentifiableResponse> decide(MultiRequest multiRequest) {
		return subscribe(multiRequest).next();
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
		saplRequest.setBody(mapper.convertValue(request, JsonNode.class));
		return saplRequest;
	}

}
