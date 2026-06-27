/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import java.io.Serial;
import java.util.function.Supplier;

import io.sapl.api.SaplVersion;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import tools.jackson.databind.json.JsonMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * HTTP/HTTPS client for connecting to a remote SAPL Policy Decision Point.
 * <p>
 * This class implements the {@link ReactivePolicyDecisionPoint} interface,
 * allowing
 * seamless integration with applications that use SAPL for authorization.
 * <p>
 * Supports multiple authentication methods:
 * <ul>
 * <li>Basic authentication</li>
 * <li>API key authentication</li>
 * <li>OAuth2 client credentials</li>
 * </ul>
 * <p>
 * The client automatically handles connection failures with exponential backoff
 * (with jitter) and returns {@link AuthorizationDecision#INDETERMINATE} during
 * reconnection. Log severity escalates from WARN to ERROR after
 * {@value #RETRY_ESCALATION_THRESHOLD} consecutive failures.
 *
 * @see RemotePolicyDecisionPoint#builder()
 */
@Slf4j
public final class RemoteHttpReactivePolicyDecisionPoint implements ReactivePolicyDecisionPoint {

    private static final String DECIDE                = "/api/pdp/decide";
    private static final String DECIDE_ONCE           = "/api/pdp/decide-once";
    private static final String MULTI_DECIDE          = "/api/pdp/multi-decide";
    private static final String MULTI_DECIDE_ALL      = "/api/pdp/multi-decide-all";
    private static final String MULTI_DECIDE_ALL_ONCE = "/api/pdp/multi-decide-all-once";

    private static final String ERROR_AUTH_FAILED                   = "PDP authentication failed (HTTP {}). Check credentials configuration.";
    private static final String ERROR_HTTP_STATUS                   = "PDP returned HTTP {} ({})";
    private static final String ERROR_INSECURE_CREDENTIAL_TRANSPORT = "Refusing to send remote PDP credentials over a plaintext http connection. Use an https baseUrl, or explicitly accept the risk with allowInsecureTransport().";
    private static final String ERROR_STREAM_FAILED                 = "PDP streaming communication error: {}";
    private static final String WARN_INSECURE_CREDENTIAL_TRANSPORT  = "Sending remote PDP credentials over a plaintext http connection because allowInsecureTransport() is set. A network attacker can capture the credential. Do not use in production.";
    static final int            RETRY_ESCALATION_THRESHOLD          = RemotePdpRetry.RETRY_ESCALATION_THRESHOLD;

    private final WebClient client;

    @Setter
    @Getter
    private volatile int firstBackoffMillis = 1000;

    // Sustained failure backs off to a 60s heartbeat, not a tight reconnect storm.
    @Setter
    @Getter
    private volatile int maxBackOffMillis = 60000;

    @Setter
    @Getter
    private volatile long maxRetries = Long.MAX_VALUE;

    @Setter
    @Getter
    private volatile int timeoutMillis = 5000;

    @Setter
    @Getter
    private volatile int inactivityTimeoutMillis = 60_000;

    /**
     * Creates a Basic-auth HTTP PDP client using the supplied TLS context.
     *
     * @param baseUrl the remote PDP base URL
     * @param clientKey Basic-auth username
     * @param clientSecret Basic-auth password
     * @param sslContext TLS context for the underlying HTTP client
     * @throws IllegalStateException if credentials would be sent over plaintext
     * HTTP
     */
    public RemoteHttpReactivePolicyDecisionPoint(String baseUrl,
            String clientKey,
            String clientSecret,
            SslContext sslContext) {
        this(baseUrl, clientKey, clientSecret, HttpClient.create().secure(spec -> spec.sslContext(sslContext)));
    }

    /**
     * Creates a Basic-auth HTTPS PDP client using Reactor Netty defaults.
     *
     * @param baseUrl the remote PDP base URL
     * @param clientKey Basic-auth username
     * @param clientSecret Basic-auth password
     * @throws IllegalStateException if credentials would be sent over plaintext
     * HTTP
     */
    public RemoteHttpReactivePolicyDecisionPoint(String baseUrl, String clientKey, String clientSecret) {
        this(baseUrl, clientKey, clientSecret, HttpClient.create().secure());
    }

    /**
     * Creates a Basic-auth PDP client using a caller-supplied HTTP client.
     *
     * @param baseUrl the remote PDP base URL
     * @param clientKey Basic-auth username
     * @param clientSecret Basic-auth password
     * @param httpClient Reactor Netty HTTP client to use
     * @throws IllegalStateException if credentials would be sent over plaintext
     * HTTP
     */
    public RemoteHttpReactivePolicyDecisionPoint(String baseUrl,
            String clientKey,
            String clientSecret,
            HttpClient httpClient) {
        enforceCredentialTransportSecurity(baseUrl, false);
        client = WebClient.builder().exchangeStrategies(saplExchangeStrategies())
                .clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(baseUrl)
                .defaultHeaders(header -> header.setBasicAuth(clientKey, clientSecret)).build();
    }

    private RemoteHttpReactivePolicyDecisionPoint(WebClient client) {
        this.client = client;
    }

    /**
     * Exchange strategies whose JSON codecs register {@link SaplJacksonModule}
     * so SAPL {@link Value} types in subscriptions and decisions serialize and
     * deserialize correctly. Used by every WebClient this class builds.
     */
    private static ExchangeStrategies saplExchangeStrategies() {
        val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        return ExchangeStrategies.builder().codecs(configurer -> {
            configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
            configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
        }).build();
    }

    private static void enforceCredentialTransportSecurity(String baseUrl, boolean allowInsecureTransport) {
        if (isEncryptedBaseUrl(baseUrl)) {
            return;
        }
        if (!allowInsecureTransport) {
            throw new IllegalStateException(ERROR_INSECURE_CREDENTIAL_TRANSPORT);
        }
        log.warn(WARN_INSECURE_CREDENTIAL_TRANSPORT);
    }

    private static boolean isEncryptedBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        return baseUrl.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        val type                = new ParameterizedTypeReference<ServerSentEvent<AuthorizationDecision>>() {};
        return Flux
                .defer(() -> streamSse(DECIDE, type, authzSubscription).doOnNext(d -> consecutiveFailures.set(0))
                        .doOnError(this::logStreamError).concatWith(Flux.error(new StreamEndedException())))
                .onErrorResume(error -> Flux.concat(Flux.just(AuthorizationDecision.INDETERMINATE), Flux.error(error)))
                .retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val type = new ParameterizedTypeReference<AuthorizationDecision>() {};
        return client.post().uri(DECIDE_ONCE).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authzSubscription).retrieve().bodyToMono(type).timeout(Duration.ofMillis(timeoutMillis))
                .doOnError(this::logStreamError).onErrorReturn(AuthorizationDecision.INDETERMINATE)
                .defaultIfEmpty(AuthorizationDecision.INDETERMINATE);
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        val type                = new ParameterizedTypeReference<ServerSentEvent<IdentifiableAuthorizationDecision>>() {};
        return Flux
                .defer(() -> streamSse(MULTI_DECIDE, type, multiAuthzSubscription)
                        .doOnNext(d -> consecutiveFailures.set(0)).doOnError(this::logStreamError)
                        .concatWith(Flux.error(new StreamEndedException())))
                .onErrorResume(error -> Flux.concat(Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE),
                        Flux.error(error)))
                .retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        val type                = new ParameterizedTypeReference<ServerSentEvent<MultiAuthorizationDecision>>() {};
        return Flux
                .defer(() -> streamSse(MULTI_DECIDE_ALL, type, multiAuthzSubscription)
                        .doOnNext(d -> consecutiveFailures.set(0)).doOnError(this::logStreamError)
                        .concatWith(Flux.error(new StreamEndedException())))
                .onErrorResume(
                        error -> Flux.concat(Flux.just(MultiAuthorizationDecision.indeterminate()), Flux.error(error)))
                .retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
    }

    /**
     * Evaluates multiple authorization subscriptions and returns all decisions
     * in a single bundled response. This is a one-shot operation that completes
     * after the first bundled result.
     * <p>
     * Not part of the {@link ReactivePolicyDecisionPoint} interface.
     *
     * @param multiAuthzSubscription the multi-subscription to evaluate
     * @return the bundled authorization decisions
     */
    public Mono<MultiAuthorizationDecision> decideAllOnce(MultiAuthorizationSubscription multiAuthzSubscription) {
        val type = new ParameterizedTypeReference<MultiAuthorizationDecision>() {};
        return client.post().uri(MULTI_DECIDE_ALL_ONCE).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(multiAuthzSubscription).retrieve().bodyToMono(type)
                .timeout(Duration.ofMillis(timeoutMillis)).doOnError(this::logStreamError)
                .onErrorReturn(MultiAuthorizationDecision.indeterminate())
                .defaultIfEmpty(MultiAuthorizationDecision.indeterminate());
    }

    private <T> Flux<T> streamSse(String path, ParameterizedTypeReference<ServerSentEvent<T>> type,
            Object authzSubscription) {
        // Liveness runs before mapNotNull drops keep-alive frames, so total silence
        // past inactivityTimeoutMillis fails closed and reconnects, while keep-alives
        // keep a quiet stream alive.
        return client.post().uri(path).accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authzSubscription).retrieve().bodyToFlux(type)
                .timeout(Mono.delay(Duration.ofMillis(timeoutMillis)),
                        ignored -> Mono.delay(Duration.ofMillis(inactivityTimeoutMillis)))
                .mapNotNull(ServerSentEvent::data);
    }

    private Retry createRetrySpec(AtomicLong consecutiveFailures) {
        return RemotePdpRetry.createRetrySpec(consecutiveFailures, maxRetries, firstBackoffMillis, maxBackOffMillis);
    }

    private void logStreamError(Throwable error) {
        if (error instanceof StreamEndedException) {
            return;
        }
        if (error instanceof WebClientResponseException wcre) {
            val statusCode = wcre.getStatusCode().value();
            log.error(ERROR_HTTP_STATUS, statusCode, wcre.getStatusText());
            if (statusCode == 401 || statusCode == 403) {
                log.error(ERROR_AUTH_FAILED, statusCode);
            }
        } else {
            log.error(ERROR_STREAM_FAILED, error.getMessage());
        }
    }

    public static RemoteHttpPolicyDecisionPointBuilder builder() {
        return new RemoteHttpPolicyDecisionPointBuilder();
    }

    @NoArgsConstructor
    public static class RemoteHttpPolicyDecisionPointBuilder {
        private String                          baseUrl                     = "https://localhost:8443";
        private static final ConnectionProvider DEFAULT_CONNECTION_PROVIDER = ConnectionProvider.builder("sapl-pdp")
                .maxConnections(64).pendingAcquireMaxCount(10_000).build();

        private HttpClient                                     httpClient = HttpClient
                .create(DEFAULT_CONNECTION_PROVIDER).protocol(HttpProtocol.HTTP11, HttpProtocol.H2);
        private Function<WebClient.Builder, WebClient.Builder> authenticationCustomizer;
        private boolean                                        allowInsecureTransport;

        /**
         * Accept sending credentials over a plaintext (non-https) connection.
         * Insecure, opt-in only.
         *
         * @return this builder
         */
        public RemoteHttpPolicyDecisionPointBuilder allowInsecureTransport() {
            this.allowInsecureTransport = true;
            return this;
        }

        public RemoteHttpPolicyDecisionPointBuilder withUnsecureSSL() throws SSLException {
            RemotePdpRetry.logInsecureSslWarning();
            val sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            return this.secure(sslContext);
        }

        public RemoteHttpPolicyDecisionPointBuilder secure(SslContext sslContext) {
            this.httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
            return this;
        }

        public RemoteHttpPolicyDecisionPointBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public RemoteHttpPolicyDecisionPointBuilder withHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public <O> RemoteHttpPolicyDecisionPointBuilder option(ChannelOption<O> key, @Nullable O value) {
            this.httpClient = this.httpClient.option(key, value);
            return this;
        }

        private void setApplyAuthenticationFunction(UnaryOperator<WebClient.Builder> applyFunction) {
            if (this.authenticationCustomizer == null) {
                this.authenticationCustomizer = applyFunction;
            } else {
                throw new IllegalStateException(this.getClass().getName() + ": authentication method already defined");
            }
        }

        public RemoteHttpPolicyDecisionPointBuilder basicAuth(String clientKey, String clientSecret) {
            setApplyAuthenticationFunction(
                    builder -> builder.defaultHeaders(header -> header.setBasicAuth(clientKey, clientSecret)));
            return this;
        }

        public RemoteHttpPolicyDecisionPointBuilder apiKey(String apikey) {
            setApplyAuthenticationFunction(
                    builder -> builder.defaultHeaders(header -> header.add("Authorization", "Bearer " + apikey)));
            return this;
        }

        /**
         * Configure per-request bearer token relay. The supplier is called on
         * each request to obtain the current token (e.g., from a security
         * context). If the supplier returns null, no Authorization header is
         * added.
         *
         * @param tokenSupplier supplies the bearer token for the current
         * request
         * @return this builder
         */
        public RemoteHttpPolicyDecisionPointBuilder tokenRelay(Supplier<String> tokenSupplier) {
            setApplyAuthenticationFunction(builder -> builder.filter((request, next) -> {
                val token = tokenSupplier.get();
                if (token != null) {
                    return next.exchange(ClientRequest.from(request).headers(h -> h.setBearerAuth(token)).build());
                }
                return next.exchange(request);
            }));
            return this;
        }

        public RemoteHttpPolicyDecisionPointBuilder oauth2(
                ReactiveClientRegistrationRepository clientRegistrationRepository, String registrationId) {
            val clientService           = new InMemoryReactiveOAuth2AuthorizedClientService(
                    clientRegistrationRepository);
            val authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository, clientService);
            val oauth2FilterFunction    = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                    authorizedClientManager);
            oauth2FilterFunction.setDefaultClientRegistrationId(registrationId);
            setApplyAuthenticationFunction(builder -> builder.filter(oauth2FilterFunction));
            return this;
        }

        public RemoteHttpReactivePolicyDecisionPoint build() {
            enforceCredentialTransportSecurity();
            WebClient.Builder builder = WebClient.builder().exchangeStrategies(saplExchangeStrategies())
                    .clientConnector(new ReactorClientHttpConnector(this.httpClient)).baseUrl(this.baseUrl);

            if (this.authenticationCustomizer != null) {
                builder = authenticationCustomizer.apply(builder);
            }
            return new RemoteHttpReactivePolicyDecisionPoint(builder.build());
        }

        private void enforceCredentialTransportSecurity() {
            if (authenticationCustomizer == null) {
                return;
            }
            RemoteHttpReactivePolicyDecisionPoint.enforceCredentialTransportSecurity(baseUrl, allowInsecureTransport);
        }
    }

    private static final class StreamEndedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        StreamEndedException() {
            super("PDP decision stream ended");
        }
    }
}
