/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.time.Duration;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.handler.ssl.SslContext;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.retry.Backoff;
import reactor.retry.Repeat;

@Slf4j
public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String DECIDE = "/api/pdp/decide";
	private static final String MULTI_DECIDE = "/api/pdp/multi-decide";
	private static final String MULTI_DECIDE_ALL = "/api/pdp/multi-decide-all";

	private final WebClient client;

	@Setter
	@Getter
	private int firstBackoffMillis = 500;
	@Setter
	@Getter
	private int maxBackOffMillis = 5000;
	@Setter
	@Getter
	private int backoffFactor = 2;

	public RemotePolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret, SslContext sslContext) {
		this(baseUrl, clientKey, clientSecret, HttpClient.create().secure(spec -> spec.sslContext(sslContext)));
	}

	public RemotePolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret) {
		this(baseUrl, clientKey, clientSecret, HttpClient.create().secure());
	}

	public RemotePolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret, HttpClient httpClient) {
		client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(baseUrl)
				.defaultHeaders(header -> header.setBasicAuth(clientKey, clientSecret)).build();
	}

	private Repeat<?> repeat() {
		return Repeat.onlyIf(repeatContext -> true)
				.backoff(Backoff.exponential(Duration.ofMillis(firstBackoffMillis), Duration.ofMillis(maxBackOffMillis),
						backoffFactor, false))
				.doOnRepeat(o -> log.debug("No connection to remote PDP. Reconnect: {}", o));
	}

	@Override
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		var type = new ParameterizedTypeReference<ServerSentEvent<AuthorizationDecision>>() {
		};
		return decide(DECIDE, type, authzSubscription)
				.onErrorResume(__ -> Flux.just(AuthorizationDecision.INDETERMINATE)).repeatWhen(repeat());
	}

	@Override
	public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<ServerSentEvent<IdentifiableAuthorizationDecision>>() {
		};
		return decide(MULTI_DECIDE, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE)).repeatWhen(repeat());
	}

	@Override
	public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<ServerSentEvent<MultiAuthorizationDecision>>() {
		};
		return decide(MULTI_DECIDE_ALL, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(MultiAuthorizationDecision.indeterminate())).repeatWhen(repeat());
	}

	private <T> Flux<T> decide(String path, ParameterizedTypeReference<ServerSentEvent<T>> type,
			Object authzSubscription) {
		return client.post().uri(path).accept(MediaType.APPLICATION_NDJSON).contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authzSubscription).retrieve().bodyToFlux(type).map(ServerSentEvent::data)
				.doOnError(error -> {
					log.error("Error : {}", error.getMessage());
				});
	}

}
