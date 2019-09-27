package io.sapl.pdp.remote;

import static java.nio.charset.StandardCharsets.UTF_8;
import static io.sapl.webclient.URLSpecification.HTTPS_SCHEME;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Base64Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import reactor.core.publisher.Flux;

public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String PDP_PATH_SINGLE_REQUEST = "/api/pdp/decide";

	private static final String PDP_PATH_MULTI_REQUEST = "/api/pdp/multi-decide";

	private static final String PDP_PATH_MULTI_REQUEST_ALL = "/api/pdp/multi-decide-all";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private ObjectMapper mapper;

	private String host;

	private int port;

	private String basicAuthHeader;

	public RemotePolicyDecisionPoint(String host, int port, String clientKey, String clientSecret) {
		this(host, port, clientKey, clientSecret, new ObjectMapper().registerModule(new Jdk8Module()));
	}

	public RemotePolicyDecisionPoint(String host, int port, String clientKey, String clientSecret,
			ObjectMapper mapper) {
		this.host = host;
		this.port = port;
		this.basicAuthHeader = "Basic " + Base64Utils.encodeToString((clientKey + ":" + clientSecret).getBytes(UTF_8));
		this.mapper = mapper;
	}

	@Override
	public Flux<Response> decide(Request request) {
		final RequestSpecification saplRequest = getRequestSpecification(request);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, Response.class))
				.onErrorResume(error -> Flux.just(Response.INDETERMINATE));
	}

	@Override
	public Flux<IdentifiableResponse> decide(MultiRequest multiRequest) {
		final RequestSpecification saplRequest = getRequestSpecification(multiRequest, false);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, IdentifiableResponse.class))
				.onErrorResume(error -> Flux.just(IdentifiableResponse.INDETERMINATE));
	}

	@Override
	public Flux<MultiResponse> decideAll(MultiRequest multiRequest) {
		final RequestSpecification saplRequest = getRequestSpecification(multiRequest, true);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, MultiResponse.class))
				.onErrorResume(error -> Flux.just(MultiResponse.indeterminate()));
	}

	private RequestSpecification getRequestSpecification(Request request) {
		return getRequestSpecification(request, PDP_PATH_SINGLE_REQUEST);
	}

	private RequestSpecification getRequestSpecification(MultiRequest multiRequest, boolean responsesForAllRequests) {
		return getRequestSpecification(multiRequest,
				responsesForAllRequests ? PDP_PATH_MULTI_REQUEST_ALL : PDP_PATH_MULTI_REQUEST);
	}

	private RequestSpecification getRequestSpecification(Object request, String urlPath) {
		final String url = HTTPS_SCHEME + "://" + host + ":" + port + urlPath;
		final RequestSpecification saplRequest = new RequestSpecification();
		saplRequest.setUrl(JSON.textNode(url));
		saplRequest.addHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader);
		saplRequest.setBody(mapper.convertValue(request, JsonNode.class));
		return saplRequest;
	}

}
