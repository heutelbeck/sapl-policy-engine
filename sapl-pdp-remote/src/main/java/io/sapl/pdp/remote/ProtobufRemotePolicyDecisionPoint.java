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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.proto.SaplProtobufCodec;
import javax.net.ssl.SSLException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

/**
 * High-performance remote PDP client using direct RSocket API with protobuf
 * serialization. Bypasses Spring Messaging layer for minimal overhead.
 * <p>
 * Supports basic authentication, API key, and bearer token authentication via
 * RSocket setup frame metadata.
 *
 * @see RemotePolicyDecisionPoint#builder()
 */
@Slf4j
public class ProtobufRemotePolicyDecisionPoint implements PolicyDecisionPoint {

    private static final String ROUTE_DECIDE           = "decide";
    private static final String ROUTE_DECIDE_ONCE      = "decide-once";
    private static final String ROUTE_MULTI_DECIDE     = "multi-decide";
    private static final String ROUTE_MULTI_DECIDE_ALL = "multi-decide-all";

    private static final String ERROR_DECODE_AUTHORIZATION_DECISION              = "Failed to decode authorization decision: {}";
    private static final String ERROR_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION = "Failed to decode identifiable authorization decision: {}";
    private static final String ERROR_DECODE_MULTI_AUTHORIZATION_DECISION        = "Failed to decode multi authorization decision: {}";
    private static final String ERROR_ENCODE_MULTI_SUBSCRIPTION                  = "Failed to encode multi-subscription: {}";
    private static final String ERROR_ENCODE_SUBSCRIPTION                        = "Failed to encode subscription: {}";
    private static final String ERROR_RSOCKET_CONNECTION                         = "RSocket connection error: {}";

    private static final String ERROR_STREAM_RECONNECT = "PDP streaming connection lost, reconnecting (attempt {})";

    static final int RETRY_ESCALATION_THRESHOLD = 5;

    private static final String WARN_INSECURE_SSL       = "!!! ATTENTION: do not use insecure sslContext in production !!!";
    private static final String WARN_INSECURE_SSL_DELIM = "------------------------------------------------------------------";
    private static final String WARN_STREAM_RECONNECT   = "PDP streaming connection lost, reconnecting (attempt {})";

    private final Mono<RSocket> rSocketMono;

    @Setter
    @Getter
    private int firstBackoffMillis = 500;

    @Setter
    @Getter
    private int maxBackOffMillis = 5000;

    ProtobufRemotePolicyDecisionPoint(Mono<RSocket> rSocketMono) {
        this.rSocketMono = rSocketMono.cache();
    }

    private Retry createRetrySpec() {
        return Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(firstBackoffMillis))
                .maxBackoff(Duration.ofMillis(maxBackOffMillis)).doBeforeRetry(signal -> {
                    val attempt = signal.totalRetries() + 1;
                    if (attempt >= RETRY_ESCALATION_THRESHOLD) {
                        log.error(ERROR_STREAM_RECONNECT, attempt);
                    } else {
                        log.warn(WARN_STREAM_RECONNECT, attempt);
                    }
                });
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestStream(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Flux.just(AuthorizationDecision.INDETERMINATE);
            }
        }).onErrorResume(error -> {
            log.error(ERROR_RSOCKET_CONNECTION, error.getMessage());
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }).retryWhen(createRetrySpec()).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription) {
        return rSocketMono.flatMap(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE_ONCE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestResponse(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_SUBSCRIPTION, e.getMessage());
                return Mono.just(AuthorizationDecision.INDETERMINATE);
            }
        }).doOnError(error -> log.error(ERROR_RSOCKET_CONNECTION, error.getMessage()))
                .onErrorReturn(AuthorizationDecision.INDETERMINATE);
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeIdentifiableAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
            }
        }).onErrorResume(error -> {
            log.error(ERROR_RSOCKET_CONNECTION, error.getMessage());
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }).retryWhen(createRetrySpec()).distinctUntilChanged();
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeMultiAuthorizationDecision);
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(MultiAuthorizationDecision.indeterminate());
            }
        }).onErrorResume(error -> {
            log.error(ERROR_RSOCKET_CONNECTION, error.getMessage());
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }).retryWhen(createRetrySpec()).distinctUntilChanged();
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
        rSocketMono.subscribe(RSocket::dispose);
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
     * Builder for {@link ProtobufRemotePolicyDecisionPoint}. Supports TLS,
     * basic authentication, API key, and bearer token authentication via
     * RSocket setup frame metadata.
     */
    public static class Builder {

        private TcpClient tcpClient   = TcpClient.create();
        private Duration  keepAlive   = Duration.ofSeconds(20);
        private Duration  maxLifeTime = Duration.ofSeconds(90);
        private Payload   setupPayload;

        /**
         * Configure insecure SSL (development only).
         *
         * @return this builder
         * @throws SSLException if SSL configuration fails
         */
        public Builder withUnsecureSSL() throws SSLException {
            log.warn(WARN_INSECURE_SSL_DELIM);
            log.warn(WARN_INSECURE_SSL);
            log.warn(WARN_INSECURE_SSL_DELIM);
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
         * Configure basic authentication via RSocket setup frame metadata.
         *
         * @param username the username
         * @param password the password
         * @return this builder
         */
        public Builder basicAuth(String username, String password) {
            val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, username.toCharArray(),
                    password.toCharArray());
            this.setupPayload = DefaultPayload.create(Unpooled.EMPTY_BUFFER, metadata);
            return this;
        }

        /**
         * Configure bearer token authentication via RSocket setup frame
         * metadata.
         *
         * @param token the bearer token (API key or JWT)
         * @return this builder
         */
        public Builder apiKey(String token) {
            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, token.toCharArray());
            this.setupPayload = DefaultPayload.create(Unpooled.EMPTY_BUFFER, metadata);
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
         * Build the {@link ProtobufRemotePolicyDecisionPoint}.
         *
         * @return a new instance
         */
        public ProtobufRemotePolicyDecisionPoint build() {
            val connector = RSocketConnector.create().keepAlive(keepAlive, maxLifeTime);
            if (setupPayload != null) {
                connector.setupPayload(setupPayload);
            }
            val rSocketMono = connector.connect(TcpClientTransport.create(tcpClient));
            return new ProtobufRemotePolicyDecisionPoint(rSocketMono);
        }
    }
}
