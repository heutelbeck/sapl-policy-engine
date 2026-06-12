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

import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.metadata.AuthMetadataCodec;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.api.proto.SaplProtobufCodec;
import javax.net.ssl.SSLException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

/**
 * High-performance remote PDP client using direct RSocket API with protobuf
 * serialization. Bypasses Spring Messaging layer for minimal overhead.
 * <p>
 * Supports basic authentication, API key / static bearer token, and OAuth2
 * client_credentials grant via RSocket setup frame metadata. For OAuth2 the
 * setup payload is recomputed on every connect, so the server's connection
 * disposal at JWT expiry triggers a reconnect with a freshly minted token.
 *
 * @see RemotePolicyDecisionPoint#builder()
 */
@Slf4j
public class ProtobufRemoteReactivePolicyDecisionPoint implements ReactivePolicyDecisionPoint {

    private static final String ROUTE_DECIDE                = "decide";
    private static final String ROUTE_DECIDE_ONCE           = "decide-once";
    private static final String ROUTE_MULTI_DECIDE          = "multi-decide";
    private static final String ROUTE_MULTI_DECIDE_ALL      = "multi-decide-all";
    private static final String ROUTE_MULTI_DECIDE_ALL_ONCE = "multi-decide-all-once";

    private static final String ERROR_DECODE_AUTHORIZATION_DECISION              = "Failed to decode authorization decision: {}";
    private static final String ERROR_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION = "Failed to decode identifiable authorization decision: {}";
    private static final String ERROR_DECODE_MULTI_AUTHORIZATION_DECISION        = "Failed to decode multi authorization decision: {}";
    private static final String ERROR_ENCODE_MULTI_SUBSCRIPTION                  = "Failed to encode multi-subscription: {}";
    private static final String ERROR_ENCODE_SUBSCRIPTION                        = "Failed to encode subscription: {}";
    private static final String ERROR_RSOCKET_CONNECTION                         = "RSocket connection error: {} ({})";

    static final int RETRY_ESCALATION_THRESHOLD = RemotePdpRetry.RETRY_ESCALATION_THRESHOLD;

    private final Mono<RSocket>                  rSocketMono;
    private final AtomicReference<RSocket>       cachedSocket = new AtomicReference<>();
    private final AtomicReference<Mono<RSocket>> connecting   = new AtomicReference<>();

    @Getter
    private final int firstBackoffMillis;

    @Getter
    private final int maxBackOffMillis;

    // Bounds a connection attempt so a dead or unreachable PDP cannot hang the
    // client. Mirrors the HTTP client's timeout. A live cached socket is reused
    // without re-timing; only a fresh connect is bounded.
    @Setter
    @Getter
    private int timeoutMillis = 5000;

    // Reconnecting cache: reuses the RSocket while alive, reconnects after
    // connection drop. Mono.defer() re-evaluates on each subscription, so
    // retryWhen in decide() naturally triggers reconnection when the cached
    // socket is disposed.
    ProtobufRemoteReactivePolicyDecisionPoint(Mono<RSocket> rSocketMono, int firstBackoffMillis, int maxBackOffMillis) {
        val connectMono = rSocketMono;
        this.rSocketMono        = Mono.defer(() -> {
                                    val existing = cachedSocket.get();
                                    if (existing != null && !existing.isDisposed()) {
                                        return Mono.just(existing);
                                    }
                                    // Single-flight the connect: concurrent first
                                    // subscriptions share one connection attempt
                                    // instead of each opening (and leaking) a socket.
                                    return connecting.updateAndGet(current -> current != null ? current
                                            : connectMono.timeout(Duration.ofMillis(timeoutMillis))
                                                    .doOnNext(cachedSocket::set)
                                                    .doFinally(signal -> connecting.set(null)).cache());
                                });
        this.firstBackoffMillis = firstBackoffMillis;
        this.maxBackOffMillis   = maxBackOffMillis;
    }

    private Retry createRetrySpec() {
        return RemotePdpRetry.createRetrySpec(Long.MAX_VALUE, firstBackoffMillis, maxBackOffMillis);
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestStream(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Flux.just(AuthorizationDecision.INDETERMINATE);
            }
        })
                // Bound the wait for the first decision so a connected but silent
                // server fails over to a retry; later items are not time-limited so
                // a healthy long-lived stream stays open. Mirrors the HTTP client.
                .timeout(Mono.delay(Duration.ofMillis(timeoutMillis)), item -> Mono.never())
                .concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
                    log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
                    return Flux.concat(Flux.just(AuthorizationDecision.INDETERMINATE), Flux.error(error));
                }).retryWhen(createRetrySpec()).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        return rSocketMono.flatMap(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE_ONCE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestResponse(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Mono.just(AuthorizationDecision.INDETERMINATE);
            }
        }).doOnError(error -> log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage()))
                .onErrorReturn(AuthorizationDecision.INDETERMINATE).defaultIfEmpty(AuthorizationDecision.INDETERMINATE);
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeIdentifiableAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
            }
        }).concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
            log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
            return Flux.concat(Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE), Flux.error(error));
        }).retryWhen(createRetrySpec()).distinctUntilChanged();
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeMultiAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(MultiAuthorizationDecision.indeterminate());
            }
        }).concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
            log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
            return Flux.concat(Flux.just(MultiAuthorizationDecision.indeterminate()), Flux.error(error));
        }).retryWhen(createRetrySpec()).distinctUntilChanged();
    }

    /**
     * Evaluates multiple authorization subscriptions and returns all decisions
     * in a single bundled response. This is a one-shot operation that completes
     * after the first bundled result.
     * <p>
     * Mirrors the {@code MultiDecideAllOnce} RPC in the proto service definition.
     * Not part of the {@link ReactivePolicyDecisionPoint} interface.
     *
     * @param multiAuthzSubscription the multi-subscription to evaluate
     * @return the bundled authorization decisions
     */
    public Mono<MultiAuthorizationDecision> decideAllOnce(MultiAuthorizationSubscription multiAuthzSubscription) {
        return rSocketMono.flatMap(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL_ONCE,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestResponse(payload).map(this::decodeMultiAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Mono.just(MultiAuthorizationDecision.indeterminate());
            }
        }).doOnError(error -> log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage()))
                .onErrorReturn(MultiAuthorizationDecision.indeterminate());
    }

    private Payload createPayload(String route, byte[] data) {
        return DefaultPayload.create(data, route.getBytes(StandardCharsets.UTF_8));
    }

    private AuthorizationDecision decodeAuthorizationDecision(Payload payload) {
        try {
            val data = extractData(payload);
            return SaplProtobufCodec.readAuthorizationDecision(data);
        } catch (IOException e) {
            log.error(ERROR_DECODE_AUTHORIZATION_DECISION, e.getMessage());
            return AuthorizationDecision.INDETERMINATE;
        } finally {
            payload.release();
        }
    }

    private IdentifiableAuthorizationDecision decodeIdentifiableAuthorizationDecision(Payload payload) {
        try {
            val data = extractData(payload);
            return SaplProtobufCodec.readIdentifiableAuthorizationDecision(data);
        } catch (IOException e) {
            log.error(ERROR_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION, e.getMessage());
            return IdentifiableAuthorizationDecision.INDETERMINATE;
        } finally {
            payload.release();
        }
    }

    private MultiAuthorizationDecision decodeMultiAuthorizationDecision(Payload payload) {
        try {
            val data = extractData(payload);
            return SaplProtobufCodec.readMultiAuthorizationDecision(data);
        } catch (IOException e) {
            log.error(ERROR_DECODE_MULTI_AUTHORIZATION_DECISION, e.getMessage());
            return MultiAuthorizationDecision.indeterminate();
        } finally {
            payload.release();
        }
    }

    private byte[] extractData(Payload payload) {
        val buf   = payload.data();
        val bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /**
     * Dispose the RSocket connection.
     */
    public void dispose() {
        val socket = cachedSocket.getAndSet(null);
        if (socket != null) {
            socket.dispose();
        }
    }

    private static final class StreamEndedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        StreamEndedException() {
            super("PDP decision stream ended");
        }
    }

    /**
     * Create a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ProtobufRemoteReactivePolicyDecisionPoint}. Supports
     * TLS, basic authentication, API key / static bearer token, and OAuth2
     * client_credentials authentication via RSocket setup frame metadata.
     */
    public static class Builder {

        private TcpClient     tcpClient   = TcpClient.create();
        private Duration      keepAlive   = Duration.ofSeconds(20);
        private Duration      maxLifeTime = Duration.ofSeconds(90);
        private Mono<Payload> setupPayloadMono;

        /**
         * Configure insecure SSL (development only).
         *
         * @return this builder
         * @throws SSLException if SSL configuration fails
         */
        public Builder withUnsecureSSL() throws SSLException {
            RemotePdpRetry.logInsecureSslWarning();
            val sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            return this.secure(sslContext);
        }

        /**
         * Enable SSL with default settings.
         *
         * @return this builder
         */
        public Builder secure() {
            tcpClient = tcpClient.secure();
            return this;
        }

        /**
         * Enable SSL with custom SslContext.
         *
         * @param sslContext the SSL context to use
         * @return this builder
         */
        public Builder secure(SslContext sslContext) {
            tcpClient = tcpClient.secure(spec -> spec.sslContext(sslContext));
            return this;
        }

        /**
         * Set the host to connect to.
         *
         * @param host the hostname
         * @return this builder
         */
        public Builder host(String host) {
            tcpClient = tcpClient.host(host);
            return this;
        }

        /**
         * Set the port to connect to.
         *
         * @param port the port number
         * @return this builder
         */
        public Builder port(int port) {
            tcpClient = tcpClient.port(port);
            return this;
        }

        /**
         * Connect via Unix domain socket instead of TCP.
         * Requires Netty native transport (epoll on Linux, kqueue on macOS).
         *
         * @param path the socket file path
         * @return this builder
         */
        public Builder socketPath(String path) {
            tcpClient = tcpClient.remoteAddress(() -> new io.netty.channel.unix.DomainSocketAddress(path));
            return this;
        }

        /**
         * Configure basic authentication via RSocket setup frame metadata.
         *
         * @param username the username
         * @param password the password
         * @return this builder
         */
        public Builder basicAuth(String username, String password) {
            val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, username.toCharArray(),
                    password.toCharArray());
            this.setupPayloadMono = Mono.just(DefaultPayload.create(Unpooled.EMPTY_BUFFER, metadata));
            return this;
        }

        /**
         * Configure bearer token authentication via RSocket setup frame
         * metadata. The token is sent verbatim and is not refreshed; if it
         * expires the connection cannot recover. Use
         * {@link #oauth2(ReactiveClientRegistrationRepository, String)} for
         * managed JWT lifecycle.
         *
         * @param token the bearer token (API key or static JWT)
         * @return this builder
         */
        public Builder apiKey(String token) {
            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, token.toCharArray());
            this.setupPayloadMono = Mono.just(DefaultPayload.create(Unpooled.EMPTY_BUFFER, metadata));
            return this;
        }

        /**
         * Configure OAuth2 client_credentials grant authentication, using the
         * supplied {@code registrationId} as the principal name for the
         * authorized-client cache.
         *
         * @param clientRegistrationRepository the Spring OAuth2 client
         * registration repository
         * @param registrationId the registration ID identifying the
         * client_credentials grant configuration
         * @return this builder
         */
        public Builder oauth2(ReactiveClientRegistrationRepository clientRegistrationRepository,
                String registrationId) {
            return oauth2(clientRegistrationRepository, registrationId, registrationId);
        }

        /**
         * Configure OAuth2 client_credentials grant authentication. The
         * supplied Spring Security {@code OAuth2AuthorizedClientManager} is
         * queried on every connect attempt; the cached token is reused while
         * valid and refreshed automatically as it approaches its expiry
         * (Spring's default 60 s clock skew). When the SAPL Node server
         * disposes the RSocket connection on JWT {@code exp}, the client's
         * reconnect path re-evaluates the supplier and obtains a fresh token.
         * <p>
         * The token is fetched lazily inside the connect path. Identity
         * provider outages at connect time propagate as connection errors and
         * are retried by the streaming decision path; one-shot calls
         * (decideOnce) fail closed to {@code INDETERMINATE}.
         *
         * @param clientRegistrationRepository the Spring OAuth2 client
         * registration repository
         * @param registrationId the registration ID identifying the
         * client_credentials grant configuration
         * @param principalName the principal name used as cache key by
         * {@link AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager}
         * @return this builder
         */
        public Builder oauth2(ReactiveClientRegistrationRepository clientRegistrationRepository, String registrationId,
                String principalName) {
            val clientService           = new InMemoryReactiveOAuth2AuthorizedClientService(
                    clientRegistrationRepository);
            val authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository, clientService);
            val principal               = new AnonymousAuthenticationToken(principalName, principalName,
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
            this.setupPayloadMono = Mono.defer(() -> {
                val authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                        .principal(principal).build();
                return authorizedClientManager.authorize(authorizeRequest)
                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                "OAuth2 client manager returned no authorized client for registration: "
                                        + registrationId)))
                        .map(client -> client.getAccessToken().getTokenValue()).map(token -> {
                            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT,
                                    token.toCharArray());
                            return (Payload) DefaultPayload.create(Unpooled.EMPTY_BUFFER, metadata);
                        });
            });
            return this;
        }

        /**
         * Generic seam for callers that need full control over setup-frame
         * metadata. The supplied {@link Mono} is subscribed on every connect
         * attempt, so token refresh strategies beyond
         * {@link #oauth2(ReactiveClientRegistrationRepository, String)} can
         * plug in here.
         *
         * @param setupPayloadMono the setup payload supplier
         * @return this builder
         */
        public Builder setupPayloadMono(Mono<Payload> setupPayloadMono) {
            this.setupPayloadMono = setupPayloadMono;
            return this;
        }

        /**
         * Set the keepalive and max lifetime durations.
         *
         * @param keepAlive how frequently to emit KEEPALIVE frames
         * @param maxLifeTime how long to allow between KEEPALIVE frames from
         * the remote end before assuming connectivity is lost
         * @return this builder
         */
        public Builder keepAlive(Duration keepAlive, Duration maxLifeTime) {
            this.keepAlive   = keepAlive;
            this.maxLifeTime = maxLifeTime;
            return this;
        }

        /**
         * Build the {@link ProtobufRemoteReactivePolicyDecisionPoint}.
         *
         * @return a new instance
         */
        public ProtobufRemoteReactivePolicyDecisionPoint build() {
            val connector = RSocketConnector.create().keepAlive(keepAlive, maxLifeTime);
            if (setupPayloadMono != null) {
                connector.setupPayload(setupPayloadMono);
            }
            val rSocketMono = connector.connect(TcpClientTransport.create(tcpClient));
            return new ProtobufRemoteReactivePolicyDecisionPoint(rSocketMono, 500, 5000);
        }
    }
}
