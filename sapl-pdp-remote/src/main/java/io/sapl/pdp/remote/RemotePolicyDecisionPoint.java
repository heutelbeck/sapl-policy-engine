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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
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
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(authzSubscription);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, AuthorizationDecision.class))
				.onErrorResume(error -> Flux.just(AuthorizationDecision.INDETERMINATE));
	}

	@Override
	public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(multiAuthzSubscription, false);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, IdentifiableAuthorizationDecision.class))
				.onErrorResume(error -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE));
	}

	@Override
	public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		final RequestSpecification requestSpec = getRequestSpecification(multiAuthzSubscription, true);
		return new WebClientRequestExecutor().executeReactiveRequest(requestSpec, POST)
				.map(jsonNode -> mapper.convertValue(jsonNode, MultiAuthorizationDecision.class))
				.onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate()));
	}

	private RequestSpecification getRequestSpecification(AuthorizationSubscription authzSubscription) {
		return getRequestSpecification(authzSubscription, PDP_PATH_SINGLE_DECIDE);
	}

	private RequestSpecification getRequestSpecification(MultiAuthorizationSubscription multiAuthzSubscription,
			boolean decisionsForAllSubscriptions) {
		return getRequestSpecification(multiAuthzSubscription,
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
