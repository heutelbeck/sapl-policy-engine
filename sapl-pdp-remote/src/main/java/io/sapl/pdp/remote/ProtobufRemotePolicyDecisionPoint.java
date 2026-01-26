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

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
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
import reactor.retry.Backoff;
import reactor.retry.Repeat;

/**
 * High-performance remote PDP client using direct RSocket API with protobuf
 * serialization. Bypasses Spring Messaging layer for reduced overhead.
 */
@Slf4j
public class ProtobufRemotePolicyDecisionPoint implements PolicyDecisionPoint {

    private static final String ROUTE_DECIDE           = "decide";
    private static final String ROUTE_DECIDE_ONCE      = "decide-once";
    private static final String ROUTE_MULTI_DECIDE     = "multi-decide";
    private static final String ROUTE_MULTI_DECIDE_ALL = "multi-decide-all";

    private static final String LOG_DECODE_AUTHORIZATION_DECISION              = "Failed to decode authorization decision: {}";
    private static final String LOG_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION = "Failed to decode identifiable authorization decision: {}";
    private static final String LOG_DECODE_MULTI_AUTHORIZATION_DECISION        = "Failed to decode multi authorization decision: {}";
    private static final String LOG_ENCODE_MULTI_SUBSCRIPTION                  = "Failed to encode multi-subscription: {}";
    private static final String LOG_ENCODE_SUBSCRIPTION                        = "Failed to encode subscription: {}";
    private static final String LOG_RECONNECT                                  = "No connection to remote PDP. Reconnect: {}";
    private static final String LOG_RSOCKET_CONNECTION_ERROR                   = "RSocket connection error: {}";
    private static final String LOG_WARN_INSECURE_SSL                          = "!!! ATTENTION: do not use insecure sslContext in production !!!";
    private static final String LOG_WARN_SEPARATOR                             = "------------------------------------------------------------------";

    private final Mono<RSocket> rSocketMono;

    @Setter
    @Getter
    private int firstBackoffMillis = 500;

    @Setter
    @Getter
    private int maxBackOffMillis = 5000;

    @Setter
    @Getter
    private int backoffFactor = 2;

    ProtobufRemotePolicyDecisionPoint(Mono<RSocket> rSocketMono) {
        this.rSocketMono = rSocketMono.cache();
    }

    private Repeat<?> repeat() {
        return Repeat
                .onlyIf(repeatContext -> true).backoff(Backoff.exponential(Duration.ofMillis(firstBackoffMillis),
                        Duration.ofMillis(maxBackOffMillis), backoffFactor, false))
                .doOnRepeat(o -> log.debug(LOG_RECONNECT, o));
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestStream(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(LOG_ENCODE_SUBSCRIPTION, e.getMessage());
                return Flux.just(AuthorizationDecision.INDETERMINATE);
            }
        }).onErrorResume(error -> {
            log.error(LOG_RSOCKET_CONNECTION_ERROR, error.getMessage());
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }).repeatWhen(repeat()).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription) {
        return rSocketMono.flatMap(rSocket -> {
            try {
                val payload = createPayload(ROUTE_DECIDE_ONCE,
                        SaplProtobufCodec.writeAuthorizationSubscription(authzSubscription));
                return rSocket.requestResponse(payload).map(this::decodeAuthorizationDecision);
            } catch (IOException e) {
                log.error(LOG_ENCODE_SUBSCRIPTION, e.getMessage());
                return Mono.just(AuthorizationDecision.INDETERMINATE);
            }
        }).doOnError(error -> log.error(LOG_RSOCKET_CONNECTION_ERROR, error.getMessage()));
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeIdentifiableAuthorizationDecision);
            } catch (IOException e) {
                log.error(LOG_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
            }
        }).onErrorResume(error -> {
            log.error(LOG_RSOCKET_CONNECTION_ERROR, error.getMessage());
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }).repeatWhen(repeat()).distinctUntilChanged();
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
        return rSocketMono.flatMapMany(rSocket -> {
            try {
                val payload = createPayload(ROUTE_MULTI_DECIDE_ALL,
                        SaplProtobufCodec.writeMultiAuthorizationSubscription(multiAuthzSubscription));
                return rSocket.requestStream(payload).map(this::decodeMultiAuthorizationDecision);
            } catch (IOException e) {
                log.error(LOG_ENCODE_MULTI_SUBSCRIPTION, e.getMessage());
                return Flux.just(MultiAuthorizationDecision.indeterminate());
            }
        }).onErrorResume(error -> {
            log.error(LOG_RSOCKET_CONNECTION_ERROR, error.getMessage());
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }).repeatWhen(repeat()).distinctUntilChanged();
    }

    private Payload createPayload(String route, byte[] data) {
        return DefaultPayload.create(data, route.getBytes(StandardCharsets.UTF_8));
    }

    private AuthorizationDecision decodeAuthorizationDecision(Payload payload) {
        try {
            val data = extractData(payload);
            return SaplProtobufCodec.readAuthorizationDecision(data);
        } catch (IOException e) {
            log.error(LOG_DECODE_AUTHORIZATION_DECISION, e.getMessage());
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
            log.error(LOG_DECODE_IDENTIFIABLE_AUTHORIZATION_DECISION, e.getMessage());
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
            log.error(LOG_DECODE_MULTI_AUTHORIZATION_DECISION, e.getMessage());
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
     * Create a new builder for ProtobufRemotePolicyDecisionPoint.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ProtobufRemotePolicyDecisionPoint.
     */
    public static class Builder {

        private TcpClient tcpClient;
        private Duration  keepAlive   = Duration.ofSeconds(20);
        private Duration  maxLifeTime = Duration.ofSeconds(90);

        public Builder() {
            tcpClient = TcpClient.create();
        }

        /**
         * Configure insecure SSL (for testing only).
         *
         * @return this builder
         * @throws SSLException if SSL configuration fails
         */
        public Builder withUnsecureSSL() throws SSLException {
            log.warn(LOG_WARN_SEPARATOR);
            log.warn(LOG_WARN_INSECURE_SSL);
            log.warn(LOG_WARN_SEPARATOR);
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
        public Builder port(Integer port) {
            tcpClient = tcpClient.port(port);
            return this;
        }

        /**
         * Set the keepalive and max lifetime durations.
         *
         * @param keepAlive how frequently to emit KEEPALIVE frames
         * @param maxLifeTime how long to allow between KEEPALIVE frames from the remote
         * end before assuming connectivity is lost
         * @return this builder
         */
        public Builder keepAlive(Duration keepAlive, Duration maxLifeTime) {
            this.keepAlive   = keepAlive;
            this.maxLifeTime = maxLifeTime;
            return this;
        }

        /**
         * Build the ProtobufRemotePolicyDecisionPoint.
         *
         * @return a new ProtobufRemotePolicyDecisionPoint instance
         */
        public ProtobufRemotePolicyDecisionPoint build() {
            val rSocketMono = RSocketConnector.create().keepAlive(keepAlive, maxLifeTime)
                    .connect(TcpClientTransport.create(tcpClient));
            return new ProtobufRemotePolicyDecisionPoint(rSocketMono);
        }
    }
}
