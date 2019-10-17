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

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import reactor.core.publisher.Flux;

public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String PDP_PATH_SINGLE_DECIDE = "/api/pdp/decide";

	private static final String PDP_PATH_MULTI_DECIDE = "/api/pdp/multi-decide";

	private static final String PDP_PATH_MULTI_DECIDE_ALL = "/api/pdp/multi-decide-all";

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
	public Flux<AuthDecision> decide(AuthSubscription authSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(authSubscription);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, AuthDecision.class))
				.onErrorResume(error -> Flux.just(AuthDecision.INDETERMINATE));
	}

	@Override
	public Flux<IdentifiableAuthDecision> decide(MultiAuthSubscription multiAuthSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(multiAuthSubscription, false);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, IdentifiableAuthDecision.class))
				.onErrorResume(error -> Flux.just(IdentifiableAuthDecision.INDETERMINATE));
	}

	@Override
	public Flux<MultiAuthDecision> decideAll(MultiAuthSubscription multiAuthSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(multiAuthSubscription, true);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, MultiAuthDecision.class))
				.onErrorResume(error -> Flux.just(MultiAuthDecision.indeterminate()));
	}

	private RequestSpecification getRequestSpecification(AuthSubscription authSubscription) {
		return getRequestSpecification(authSubscription, PDP_PATH_SINGLE_DECIDE);
	}

	private RequestSpecification getRequestSpecification(MultiAuthSubscription multiAuthSubscription,
			boolean decisionsForAllSubscriptions) {
		return getRequestSpecification(multiAuthSubscription,
				decisionsForAllSubscriptions ? PDP_PATH_MULTI_DECIDE_ALL : PDP_PATH_MULTI_DECIDE);
	}

	private RequestSpecification getRequestSpecification(Object obj, String urlPath) {
		final String url = HTTPS_SCHEME + "://" + host + ":" + port + urlPath;
		final RequestSpecification requestSpec = new RequestSpecification();
		requestSpec.setUrl(JSON.textNode(url));
		requestSpec.addHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader);
		requestSpec.setBody(mapper.convertValue(obj, JsonNode.class));
		return requestSpec;
	}

}
