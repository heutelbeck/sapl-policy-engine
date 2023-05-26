/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.function.Function;

import javax.net.ssl.SSLException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.retry.Backoff;
import reactor.retry.Repeat;

@Slf4j
public class RemoteHttpPolicyDecisionPoint implements PolicyDecisionPoint {

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

	public RemoteHttpPolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret, SslContext sslContext) {
		this(baseUrl, clientKey, clientSecret, HttpClient.create().secure(spec -> spec.sslContext(sslContext)));
	}

	public RemoteHttpPolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret) {
		this(baseUrl, clientKey, clientSecret, HttpClient.create().secure());
	}

	public RemoteHttpPolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret, HttpClient httpClient) {
		client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(baseUrl)
				.defaultHeaders(header -> header.setBasicAuth(clientKey, clientSecret)).build();
	}

	private RemoteHttpPolicyDecisionPoint(WebClient client) {
		this.client = client;
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
				.onErrorResume(__ -> Flux.just(AuthorizationDecision.INDETERMINATE)).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	@Override
	public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<ServerSentEvent<IdentifiableAuthorizationDecision>>() {
		};
		return decide(MULTI_DECIDE, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE)).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	@Override
	public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<ServerSentEvent<MultiAuthorizationDecision>>() {
		};
		return decide(MULTI_DECIDE_ALL, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(MultiAuthorizationDecision.indeterminate())).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	private <T> Flux<T> decide(String path, ParameterizedTypeReference<ServerSentEvent<T>> type,
			Object authzSubscription) {
		return client.post().uri(path).accept(MediaType.APPLICATION_NDJSON).contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authzSubscription).retrieve().bodyToFlux(type).mapNotNull(ServerSentEvent::data)
				.doOnError(error -> log.error("Error : {}", error.getMessage()));
	}

	public static RemoteHttpPolicyDecisionPointBuilder builder() {
		return new RemoteHttpPolicyDecisionPointBuilder();
	}

	@NoArgsConstructor
	public static class RemoteHttpPolicyDecisionPointBuilder {
		private String                                         baseUrl    = "https://localhost:8443";
		private HttpClient                                     httpClient = HttpClient.create();
		private Function<WebClient.Builder, WebClient.Builder> authenticationCustomizer;

		public RemoteHttpPolicyDecisionPointBuilder withUnsecureSSL() throws SSLException {
			log.warn("------------------------------------------------------------------");
			log.warn("!!! ATTENTION: don't not use insecure sslContext in production !!!");
			log.warn("------------------------------------------------------------------");
			var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
			return this.secure(sslContext);
		}

		public RemoteHttpPolicyDecisionPointBuilder secure() {
			this.httpClient = httpClient.secure();
			return this;
		}

		public RemoteHttpPolicyDecisionPointBuilder secure(SslContext sslContext) {
			this.httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
			return this;
		}

		public RemoteHttpPolicyDecisionPointBuilder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		private void setApplyAuthenticationFunction(Function<WebClient.Builder, WebClient.Builder> applyFunction) {
			if (this.authenticationCustomizer == null) {
				this.authenticationCustomizer = applyFunction;
			} else {
				throw new RuntimeException(this.getClass().getName() + ": authentication method already defined");
			}
		}

		public RemoteHttpPolicyDecisionPointBuilder basicAuth(String clientKey, String clientSecret) {
			setApplyAuthenticationFunction(
					builder -> builder.defaultHeaders(header -> header.setBasicAuth(clientKey, clientSecret)));
			return this;
		}

		public RemoteHttpPolicyDecisionPointBuilder apiKey(String apikey) {
			return this.apiKey("API_KEY", apikey);
		}

		public RemoteHttpPolicyDecisionPointBuilder apiKey(String headerName, String apikey) {
			setApplyAuthenticationFunction(builder -> builder.defaultHeaders(header -> header.add(headerName, apikey)));
			return this;
		}

		public RemoteHttpPolicyDecisionPointBuilder oauth2(
				ReactiveClientRegistrationRepository clientRegistrationRepository, String registrationId) {
			InMemoryReactiveOAuth2AuthorizedClientService                clientService           = new InMemoryReactiveOAuth2AuthorizedClientService(
					clientRegistrationRepository);
			AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
					clientRegistrationRepository, clientService);
			var                                                          oauth2FilterFunction    = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
					authorizedClientManager);
			oauth2FilterFunction.setDefaultClientRegistrationId(registrationId);
			setApplyAuthenticationFunction(builder -> builder.filter(oauth2FilterFunction));
			return this;
		}

		public RemoteHttpPolicyDecisionPoint build() {
			WebClient.Builder builder = WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(this.httpClient)).baseUrl(this.baseUrl);

			if (this.authenticationCustomizer != null) {
				builder = authenticationCustomizer.apply(builder);
			}
			return new RemoteHttpPolicyDecisionPoint(builder.build());
		}
	}
}
