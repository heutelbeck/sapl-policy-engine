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
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

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
import javax.net.ssl.SSLException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
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
 * serialization, or the SAPL binary format if asked for with
 * {@link Builder#binaryFormat()}. Bypasses Spring Messaging layer for minimal
 * overhead.
 * <p>
 * A connection in the binary format to a node that does not speak it falls
 * back to protobuf, and the next connection tries the format again.
 * <p>
 * Supports basic authentication, API key / static bearer token, and OAuth2
 * client_credentials grant via RSocket setup frame metadata. For OAuth2 the
 * setup payload is recomputed on every connect, so the server's connection
 * disposal at JWT expiry triggers a reconnect with a freshly minted token.
 *
 * @see RemotePolicyDecisionPoint#builder()
 */
@Slf4j
public class ProtobufRemoteReactivePolicyDecisionPoint implements ReactivePolicyDecisionPoint, AutoCloseable {

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
    private static final String ERROR_INSECURE_CREDENTIAL_TRANSPORT              = "Refusing to send remote PDP credentials over a plaintext RSocket connection. Enable TLS with secure(...), or explicitly accept the risk with allowInsecureTransport().";
    private static final String ERROR_INVALID_CONNECTIONS                        = "The number of connections must be at least 1.";
    private static final String ERROR_RSOCKET_CONNECTION                         = "RSocket connection error: {} ({})";
    private static final String WARN_INSECURE_CREDENTIAL_TRANSPORT               = "Sending remote PDP credentials over a plaintext RSocket connection because allowInsecureTransport() is set. A network attacker can capture the credential. Do not use in production.";

    static final int RETRY_ESCALATION_THRESHOLD = RemotePdpRetry.RETRY_ESCALATION_THRESHOLD;

    private final @Nullable RSocketClientResources clientResources;
    private final RSocketPool                      pool;

    @Getter
    private final int connections;

    // Whether the client asks for the SAPL binary format rather than protobuf.
    @Getter
    private final boolean binaryFormat;

    @Getter
    private final int firstBackoffMillis;

    @Getter
    private final int maxBackOffMillis;

    // Bounds a fresh connect so a dead PDP cannot hang the client. Cached sockets
    // are reused untimed. Mirrors the HTTP client.
    @Getter
    private final int timeoutMillis;

    // A pool of reconnecting connections: each is reused while alive and opened
    // anew after it is lost. A request leases a connection at each subscription,
    // so retryWhen in decide() naturally resubscribes on another or a reopened
    // connection when its connection is lost.
    ProtobufRemoteReactivePolicyDecisionPoint(Mono<RSocket> rSocketMono, int firstBackoffMillis, int maxBackOffMillis) {
        this(rSocketMono, firstBackoffMillis, maxBackOffMillis, 5000);
    }

    ProtobufRemoteReactivePolicyDecisionPoint(Mono<RSocket> rSocketMono,
            int firstBackoffMillis,
            int maxBackOffMillis,
            int timeoutMillis) {
        this(rSocketMono, firstBackoffMillis, maxBackOffMillis, timeoutMillis, 1);
    }

    ProtobufRemoteReactivePolicyDecisionPoint(Mono<RSocket> rSocketMono,
            int firstBackoffMillis,
            int maxBackOffMillis,
            int timeoutMillis,
            int connections) {
        this(format -> rSocketMono, PayloadFormat.PROTOBUF, firstBackoffMillis, maxBackOffMillis, timeoutMillis,
                connections, null);
    }

    private ProtobufRemoteReactivePolicyDecisionPoint(Function<PayloadFormat, Mono<RSocket>> connect,
            PayloadFormat format,
            int firstBackoffMillis,
            int maxBackOffMillis,
            int timeoutMillis,
            int connections,
            @Nullable RSocketClientResources clientResources) {
        this.clientResources    = clientResources;
        this.pool               = new RSocketPool(
                connectionFormat -> connect.apply(connectionFormat).timeout(Duration.ofMillis(timeoutMillis)), format,
                connections, firstBackoffMillis, maxBackOffMillis);
        this.connections        = connections;
        this.binaryFormat       = format == PayloadFormat.BINARY;
        this.firstBackoffMillis = firstBackoffMillis;
        this.maxBackOffMillis   = maxBackOffMillis;
        this.timeoutMillis      = timeoutMillis;
    }

    private Retry createRetrySpec(AtomicLong consecutiveFailures) {
        return RemotePdpRetry.createRetrySpec(consecutiveFailures, Long.MAX_VALUE, firstBackoffMillis,
                maxBackOffMillis);
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        return pool.stream(connection -> {
            try {
                val payload = createPayload(ROUTE_DECIDE, connection.format().write(authzSubscription));
                return connection.socket().requestStream(payload)
                        .map(answer -> decodeAuthorizationDecision(connection.format(), answer))
                        .doOnNext(d -> consecutiveFailures.set(0));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Flux.just(AuthorizationDecision.INDETERMINATE);
            }
        })
                // Bound the wait for the first decision so a silent server retries. Later items
                // are not timed, keeping a healthy stream open. Mirrors the HTTP client.
                .timeout(Mono.delay(Duration.ofMillis(timeoutMillis)), item -> Mono.never())
                .concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
                    log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
                    return Flux.concat(Flux.just(AuthorizationDecision.INDETERMINATE), Flux.error(error));
                }).retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription, String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        return pool.request(connection -> {
            try {
                val payload = createPayload(ROUTE_DECIDE_ONCE, connection.format().write(authzSubscription));
                return connection.socket().requestResponse(payload)
                        .map(answer -> decodeAuthorizationDecision(connection.format(), answer));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Mono.just(AuthorizationDecision.INDETERMINATE);
            }
        })
                // Bound the wait for the response so a silent live server fails closed instead
                // of
                // hanging the caller. Mirrors the HTTP client.
                .timeout(Duration.ofMillis(timeoutMillis))
                .doOnError(error -> log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(),
                        error.getMessage()))
                .onErrorReturn(AuthorizationDecision.INDETERMINATE).defaultIfEmpty(AuthorizationDecision.INDETERMINATE);
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        return pool.stream(connection -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE, connection.format().write(multiAuthzSubscription));
                return connection.socket().requestStream(payload)
                        .map(answer -> decodeIdentifiableAuthorizationDecision(connection.format(), answer))
                        .doOnNext(d -> consecutiveFailures.set(0));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
            }
        })
                // Bound the wait for the first decision so a silent server retries. Later items
                // are not timed, keeping a healthy stream open. Mirrors the single decide().
                .timeout(Mono.delay(Duration.ofMillis(timeoutMillis)), item -> Mono.never())
                .concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
                    log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
                    return Flux.concat(Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE), Flux.error(error));
                }).retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription,
            String pdpId) {
        // The pdpId argument is intentionally unused. The remote SAPL server
        // derives the tenant from the access token (JWT claim, API-key user
        // record), so multi-tenant routing is determined entirely by the
        // configured credentials of this client.
        val consecutiveFailures = new AtomicLong();
        return pool.stream(connection -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL, connection.format().write(multiAuthzSubscription));
                return connection.socket().requestStream(payload)
                        .map(answer -> decodeMultiAuthorizationDecision(connection.format(), answer))
                        .doOnNext(d -> consecutiveFailures.set(0));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(MultiAuthorizationDecision.indeterminate());
            }
        })
                // Bound the wait for the first decision so a silent server retries. Later items
                // are not timed, keeping a healthy stream open. Mirrors the single decide().
                .timeout(Mono.delay(Duration.ofMillis(timeoutMillis)), item -> Mono.never())
                .concatWith(Flux.error(new StreamEndedException())).onErrorResume(error -> {
                    log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(), error.getMessage());
                    return Flux.concat(Flux.just(MultiAuthorizationDecision.indeterminate()), Flux.error(error));
                }).retryWhen(createRetrySpec(consecutiveFailures)).distinctUntilChanged();
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
        return pool.request(connection -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL_ONCE,
                        connection.format().write(multiAuthzSubscription));
                return connection.socket().requestResponse(payload)
                        .map(answer -> decodeMultiAuthorizationDecision(connection.format(), answer));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Mono.just(MultiAuthorizationDecision.indeterminate());
            }
        })
                // Bound the wait so a silent server fails closed. Mirrors the HTTP client.
                .timeout(Duration.ofMillis(timeoutMillis))
                .doOnError(error -> log.debug(ERROR_RSOCKET_CONNECTION, error.getClass().getSimpleName(),
                        error.getMessage()))
                .onErrorReturn(MultiAuthorizationDecision.indeterminate())
                .defaultIfEmpty(MultiAuthorizationDecision.indeterminate());
    }

    private Payload createPayload(String route, byte[] data) {
        return DefaultPayload.create(data, route.getBytes(StandardCharsets.UTF_8));
    }

    private AuthorizationDecision decodeAuthorizationDecision(PayloadFormat format, Payload payload) {
        try {
            return format.readDecision(extractData(payload));
        } catch (IOException e) {
            log.error(ERROR_DECODE_AUTHORIZATION_DECISION, e.getMessage());
            return AuthorizationDecision.INDETERMINATE;
        } finally {
            payload.release();
        }
    }

    private IdentifiableAuthorizationDecision decodeIdentifiableAuthorizationDecision(PayloadFormat format,
            Payload payload) {
        try {
            return format.readIdentifiableDecision(extractData(payload));
        } catch (IOException e) {
            log.error(ERROR_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION, e.getMessage());
            return IdentifiableAuthorizationDecision.INDETERMINATE;
        } finally {
            payload.release();
        }
    }

    private MultiAuthorizationDecision decodeMultiAuthorizationDecision(PayloadFormat format, Payload payload) {
        try {
            return format.readMultiDecision(extractData(payload));
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
     * Closes the RSocket connections and releases their shared event loops.
     */
    @Override
    public void close() {
        dispose();
    }

    /**
     * Dispose the RSocket connections and release their shared event loops.
     */
    public void dispose() {
        pool.dispose();
        if (clientResources != null) {
            clientResources.close();
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
     * <p>
     * All authentication here is connection-scoped, carried once on the setup
     * frame. There is no per-request bearer relay equivalent to the HTTP
     * client's {@code tokenRelay}. To propagate an end-user token per request,
     * use the HTTP transport or open one RSocket connection per principal.
     */
    public static class Builder {

        private TcpClient     tcpClient   = TcpClient.create();
        private Duration      keepAlive   = Duration.ofSeconds(20);
        private Duration      maxLifeTime = Duration.ofSeconds(60);
        private Mono<Payload> setupPayloadMono;
        private boolean       secure;
        private boolean       allowInsecureTransport;
        private int           connections = 1;
        private PayloadFormat format      = PayloadFormat.PROTOBUF;

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
            secure    = true;
            return this;
        }

        /**
         * Accept sending credentials over a plaintext (non-TLS) connection. Insecure,
         * opt-in only.
         *
         * @return this builder
         */
        public Builder allowInsecureTransport() {
            allowInsecureTransport = true;
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
            secure    = true;
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
         * metadata. The token is sent verbatim and is not refreshed. If it
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
         * queried on every connect attempt. The cached token is reused while
         * valid and refreshed automatically as it approaches its expiry
         * (Spring's default 60 s clock skew). When the SAPL Node server
         * disposes the RSocket connection on JWT {@code exp}, the client's
         * reconnect path re-evaluates the supplier and obtains a fresh token.
         * <p>
         * The token is fetched lazily inside the connect path. Identity
         * provider outages at connect time propagate as connection errors and
         * are retried by the streaming decision path. One-shot calls
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
         * Set how many connections the client keeps to the node, so that the node
         * can serve its requests on as many of its cores. A one-shot request goes
         * to the connection with the fewest requests in flight, a stream stays on
         * the connection it was opened on. A connection that is lost is opened
         * anew in the background while the others serve on. One connection unless
         * set.
         *
         * @param connections the number of connections, at least 1
         * @return this builder
         * @throws IllegalArgumentException if the number is less than 1
         */
        public Builder connections(int connections) {
            if (connections < 1) {
                throw new IllegalArgumentException(ERROR_INVALID_CONNECTIONS);
            }
            this.connections = connections;
            return this;
        }

        /**
         * Speak the SAPL binary format instead of protobuf: smaller payloads that
         * the node reads and writes faster. A connection to a node that does not
         * speak the format falls back to protobuf, and the next connection tries
         * the format again.
         *
         * @return this builder
         */
        public Builder binaryFormat() {
            this.format = PayloadFormat.BINARY;
            return this;
        }

        /**
         * Build the {@link ProtobufRemoteReactivePolicyDecisionPoint}.
         *
         * @return a new instance
         */
        public ProtobufRemoteReactivePolicyDecisionPoint build() {
            if (setupPayloadMono != null && !secure) {
                if (!allowInsecureTransport) {
                    throw new IllegalStateException(ERROR_INSECURE_CREDENTIAL_TRANSPORT);
                }
                log.warn(WARN_INSECURE_CREDENTIAL_TRANSPORT);
            }
            val connectors = new EnumMap<PayloadFormat, RSocketConnector>(PayloadFormat.class);
            for (val payloadFormat : PayloadFormat.values()) {
                val connector = RSocketConnector.create().keepAlive(keepAlive, maxLifeTime)
                        .dataMimeType(payloadFormat.mimeType());
                if (setupPayloadMono != null) {
                    connector.setupPayload(setupPayloadMono);
                }
                connectors.put(payloadFormat, connector);
            }
            val                                          clientResources = RSocketClientResources.acquire();
            val                                          client          = tcpClient;
            final Function<PayloadFormat, Mono<RSocket>> connect         = connectionFormat -> Mono
                    .defer(() -> connectors.get(connectionFormat)
                            .connect(TcpClientTransport.create(clientResources.configure(client))));
            // Sustained failure backs off to a 60s heartbeat, not a tight reconnect storm.
            return new ProtobufRemoteReactivePolicyDecisionPoint(connect, format, 500, 60000, 5000, connections,
                    clientResources);
        }
    }
}
