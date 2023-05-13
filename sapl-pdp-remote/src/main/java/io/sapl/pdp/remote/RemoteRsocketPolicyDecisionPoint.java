/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.rsocket.metadata.AuthMetadataCodec;
import io.rsocket.metadata.WellKnownAuthType;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.netty.client.TcpClientTransport;
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
import reactor.netty.tcp.TcpClient;
import reactor.retry.Backoff;
import reactor.retry.Repeat;

@Slf4j
public class RemoteRsocketPolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String DECIDE           = "decide";
	private static final String MULTI_DECIDE     = "multi-decide";
	private static final String MULTI_DECIDE_ALL = "multi-decide-all";

	private final RSocketRequester rSocketRequester;

	@Setter
	@Getter
	private int firstBackoffMillis = 500;

	@Setter
	@Getter
	private int maxBackOffMillis = 5000;

	@Setter
	@Getter
	private int backoffFactor = 2;

	public RemoteRsocketPolicyDecisionPoint(RSocketRequester rSocketRequester) {
		this.rSocketRequester = rSocketRequester;
	}

	private Repeat<?> repeat() {
		return Repeat.onlyIf(repeatContext -> true)
				.backoff(Backoff.exponential(Duration.ofMillis(firstBackoffMillis), Duration.ofMillis(maxBackOffMillis),
						backoffFactor, false))
				.doOnRepeat(o -> log.debug("No connection to remote PDP. Reconnect: {}", o));
	}

	@Override
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		var type = new ParameterizedTypeReference<AuthorizationDecision>() {
		};
		return decide(DECIDE, type, authzSubscription)
				.onErrorResume(__ -> Flux.just(AuthorizationDecision.INDETERMINATE)).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	@Override
	public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<IdentifiableAuthorizationDecision>() {
		};
		return decide(MULTI_DECIDE, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE)).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	@Override
	public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		var type = new ParameterizedTypeReference<MultiAuthorizationDecision>() {
		};
		return decide(MULTI_DECIDE_ALL, type, multiAuthzSubscription)
				.onErrorResume(__ -> Flux.just(MultiAuthorizationDecision.indeterminate())).repeatWhen(repeat())
				.distinctUntilChanged();
	}

	private <T> Flux<T> decide(String path, ParameterizedTypeReference<T> type, Object authzSubscription) {
		return rSocketRequester.route(path).data(authzSubscription).retrieveFlux(type)
				.doOnError(error -> log.error("RSocket Connect Error : error {}", error.getMessage(), error));
	}

	public static RemoteRsocketPolicyDecisionPointBuilder builder() {
		return new RemoteRsocketPolicyDecisionPointBuilder();
	}

	public static class RemoteRsocketPolicyDecisionPointBuilder {
		private TcpClient                                                    tcpClient;
		private Function<RSocketRequester.Builder, RSocketRequester.Builder> authenticationCustomizer;

		public RemoteRsocketPolicyDecisionPointBuilder() {
			tcpClient = TcpClient.create();
		}

		public RemoteRsocketPolicyDecisionPointBuilder withUnsecureSSL() throws SSLException {
			log.warn("------------------------------------------------------------------");
			log.warn("!!! ATTENTION: don't not use insecure sslContext in production !!!");
			log.warn("------------------------------------------------------------------");
			var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
			return this.secure(sslContext);
		}

		public RemoteRsocketPolicyDecisionPointBuilder secure() {
			tcpClient = tcpClient.secure();
			return this;
		}

		public RemoteRsocketPolicyDecisionPointBuilder secure(SslContext sslContext) {
			tcpClient = tcpClient.secure(spec -> spec.sslContext(sslContext));
			return this;
		}

		public RemoteRsocketPolicyDecisionPointBuilder host(String host) {
			tcpClient = tcpClient.host(host);
			return this;
		}

		public RemoteRsocketPolicyDecisionPointBuilder port(Integer port) {
			tcpClient = tcpClient.port(port);
			return this;
		}

		private RemoteRsocketPolicyDecisionPointBuilder setApplyAuthenticationFunction(
				Function<RSocketRequester.Builder, RSocketRequester.Builder> applyFunction) {
			if (this.authenticationCustomizer == null) {
				this.authenticationCustomizer = applyFunction;
			} else {
				throw new RuntimeException(this.getClass().getName() + ": authentication method already defined");
			}
			return this;
		}

		public RemoteRsocketPolicyDecisionPointBuilder basicAuth(String username, String password) {
			return setApplyAuthenticationFunction(builder -> {
				MimeType                 authenticationMimeType = MimeTypeUtils
						.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());
				UsernamePasswordMetadata credentials            = new UsernamePasswordMetadata(username, password);
				builder.setupMetadata(credentials, authenticationMimeType);
				return builder;
			});
		}

		public RemoteRsocketPolicyDecisionPointBuilder oauth2(
				ReactiveClientRegistrationRepository clientRegistrationRepository, String registrationId) {
			return setApplyAuthenticationFunction(builder -> {
				var      client                 = new WebClientReactiveClientCredentialsTokenResponseClient();
				var      tokenStr               = clientRegistrationRepository.findByRegistrationId(registrationId)
						.map(OAuth2ClientCredentialsGrantRequest::new).flatMap(client::getTokenResponse)
						.map(OAuth2AccessTokenResponse::getAccessToken).map(OAuth2AccessToken::getTokenValue).block();
				var      token                  = AuthMetadataCodec.encodeMetadata(ByteBufAllocator.DEFAULT,
						WellKnownAuthType.BEARER, Unpooled.copiedBuffer(tokenStr, CharsetUtil.UTF_8));
				MimeType authenticationMimeType = MimeTypeUtils
						.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());
				return builder.setupMetadata(token, authenticationMimeType);
			});
		}

		public RemoteRsocketPolicyDecisionPointBuilder apiKey(String apikey) {
			return this.apiKey("API_KEY", apikey);
		}

		public RemoteRsocketPolicyDecisionPointBuilder apiKey(String headerName, String apikey) {
			return setApplyAuthenticationFunction(
					builder -> builder.setupMetadata(apikey, MimeType.valueOf("messaging/" + headerName)));
		}

		public RemoteRsocketPolicyDecisionPoint build() {
			RSocketStrategies rSocketStrategies = RSocketStrategies.builder().encoder(new Jackson2JsonEncoder())
					.encoder(new SimpleAuthenticationEncoder()).decoder(new Jackson2JsonDecoder()).build();

			var builder = RSocketRequester.builder().rsocketStrategies(rSocketStrategies);
			if (authenticationCustomizer != null) {
				builder = authenticationCustomizer.apply(builder);
			}
			var rSocketRequester = builder.transport(TcpClientTransport.create(tcpClient));
			return new RemoteRsocketPolicyDecisionPoint(rSocketRequester);
		}
	}
}
