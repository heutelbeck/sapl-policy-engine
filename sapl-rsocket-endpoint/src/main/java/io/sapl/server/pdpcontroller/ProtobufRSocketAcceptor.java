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
package io.sapl.server.pdpcontroller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import io.sapl.api.pdp.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.RejectedSetupException;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.proto.SaplProtobufCodec;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Direct RSocket acceptor using protobuf serialization for high-performance PDP
 * communication. Bypasses Spring Messaging layer to reduce overhead.
 * <p>
 * Authentication is performed once per connection via the
 * {@link RSocketConnectionAuthenticator}. The authenticated PDP ID is stored on
 * the per-connection {@code RSocket} instance and propagated to the PDP via a
 * {@link ThreadLocal} for blocking calls.
 * <p>
 * Connection lifetime is bounded by the credential expiry (JWT {@code exp}
 * claim) and an optional maximum connection lifetime. When a connection exceeds
 * its lifetime, the server disposes it and the client is expected to reconnect
 * with fresh credentials.
 * <p>
 * Request-response operations ({@code decide-once}) use
 * {@link PolicyDecisionPoint#decideOnceBlocking} on virtual threads for maximum
 * throughput. Streaming operations use the reactive PDP methods directly.
 */
@Slf4j
public class ProtobufRSocketAcceptor implements SocketAcceptor {

    private static final String ROUTE_DECIDE                = "decide";
    private static final String ROUTE_DECIDE_ONCE           = "decide-once";
    private static final String ROUTE_MULTI_DECIDE          = "multi-decide";
    private static final String ROUTE_MULTI_DECIDE_ALL      = "multi-decide-all";
    private static final String ROUTE_MULTI_DECIDE_ALL_ONCE = "multi-decide-all-once";

    private static final String ERROR_ENCODE_DECISION_FAILED              = "Failed to encode decision: {}";
    private static final String ERROR_ENCODE_IDENTIFIABLE_DECISION_FAILED = "Failed to encode identifiable decision: {}";
    private static final String ERROR_ENCODE_MULTI_DECISION_FAILED        = "Failed to encode multi-decision: {}";
    private static final String ERROR_IN_ROUTE                            = "Error in {}: {}";
    private static final String ERROR_PARSE_MULTI_SUBSCRIPTION_FAILED     = "Failed to parse multi-subscription: {}";
    private static final String ERROR_PARSE_SUBSCRIPTION_FAILED           = "Failed to parse subscription: {}";

    private static final ThreadLocal<String> PDP_ID_HOLDER = new ThreadLocal<>();

    // Virtual thread executor creates threads on demand with no pool to shut down.
    // Static is safe here - no resource leak on application shutdown.
    private static final Scheduler VIRTUAL_THREAD_SCHEDULER = Schedulers
            .fromExecutorService(Executors.newVirtualThreadPerTaskExecutor());

    private final PolicyDecisionPoint                      pdp;
    private final @Nullable RSocketConnectionAuthenticator authenticator;
    private final @Nullable Duration                       maxConnectionLifetime;

    /**
     * Creates an acceptor with authentication and optional connection lifetime
     * limit.
     *
     * @param pdp the policy decision point
     * @param authenticator the connection authenticator, or null for
     * unauthenticated access
     * @param maxConnectionLifetime maximum connection lifetime, or null for
     * unlimited (JWT expiry still enforced)
     */
    public ProtobufRSocketAcceptor(PolicyDecisionPoint pdp,
            @Nullable RSocketConnectionAuthenticator authenticator,
            @Nullable Duration maxConnectionLifetime) {
        this.pdp                   = pdp;
        this.authenticator         = authenticator;
        this.maxConnectionLifetime = maxConnectionLifetime;
    }

    /**
     * Creates an acceptor with authentication and no connection lifetime limit.
     *
     * @param pdp the policy decision point
     * @param authenticator the connection authenticator, or null for
     * unauthenticated access
     */
    public ProtobufRSocketAcceptor(PolicyDecisionPoint pdp, @Nullable RSocketConnectionAuthenticator authenticator) {
        this(pdp, authenticator, null);
    }

    /**
     * Creates an acceptor without authentication (development only).
     *
     * @param pdp the policy decision point
     */
    public ProtobufRSocketAcceptor(PolicyDecisionPoint pdp) {
        this(pdp, null, null);
    }

    /**
     * Returns the PDP ID set by the current RSocket request thread. Used by
     * {@link BlockingPdpIdSource} implementations to route
     * blocking decisions to the correct tenant.
     *
     * @return the PDP ID for the current thread, or null if not in an RSocket
     * context
     */
    public static @Nullable String getCurrentPdpId() {
        return PDP_ID_HOLDER.get();
    }

    @Override
    public @NonNull Mono<RSocket> accept(@NonNull ConnectionSetupPayload setup, @NonNull RSocket sendingSocket) {
        if (authenticator == null) {
            return Mono.just(createRSocket("default", null, sendingSocket));
        }
        return authenticator.authenticate(setup)
                .<RSocket>map(result -> createRSocket(result.pdpId(), result.expiresAt(), sendingSocket))
                .onErrorMap(e -> new RejectedSetupException("Authentication failed: " + e.getMessage()));
    }

    private RSocket createRSocket(String pdpId, @Nullable Instant credentialExpiry, RSocket sendingSocket) {
        val effectiveLifetime = computeEffectiveLifetime(credentialExpiry);
        val rsocket           = new ProtobufRSocket(pdpId);
        if (effectiveLifetime != null) {
            scheduleDisposal(sendingSocket, rsocket, effectiveLifetime, pdpId);
        }
        return rsocket;
    }

    @Nullable
    private Duration computeEffectiveLifetime(@Nullable Instant credentialExpiry) {
        Duration tokenTtl  = credentialExpiry != null ? Duration.between(Instant.now(), credentialExpiry) : null;
        Duration staticCap = maxConnectionLifetime;

        if (tokenTtl != null && staticCap != null) {
            return tokenTtl.compareTo(staticCap) < 0 ? tokenTtl : staticCap;
        }
        if (tokenTtl != null) {
            return tokenTtl;
        }
        return staticCap;
    }

    private void scheduleDisposal(RSocket sendingSocket, ProtobufRSocket serverSocket, Duration lifetime,
            String pdpId) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            log.warn("Connection for pdpId={} has expired credential, disposing immediately", pdpId);
            sendingSocket.dispose();
            return;
        }
        log.debug("Connection for pdpId={} will expire in {}", pdpId, lifetime);
        Mono.delay(lifetime).subscribe(new ConnectionExpirySubscriber(sendingSocket, serverSocket, pdpId));
    }

    private static final class ConnectionExpirySubscriber implements Subscriber<Long> {

        private final RSocket         sendingSocket;
        private final ProtobufRSocket serverSocket;
        private final String          pdpId;

        ConnectionExpirySubscriber(RSocket sendingSocket, ProtobufRSocket serverSocket, String pdpId) {
            this.sendingSocket = sendingSocket;
            this.serverSocket  = serverSocket;
            this.pdpId         = pdpId;
        }

        @Override
        public void onSubscribe(@NonNull Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(@NonNull Long tick) {
            log.info("Connection expired for pdpId={}, disposing", pdpId);
            sendingSocket.dispose();
            serverSocket.dispose();
        }

        @Override
        public void onError(@NonNull Throwable t) {
            log.debug("Connection expiry timer error for pdpId={}: {}", pdpId, t.getMessage());
        }

        @Override
        public void onComplete() {
            // Timer completed after emitting
        }
    }

    private final class ProtobufRSocket implements RSocket {

        private final String pdpId;

        ProtobufRSocket(String pdpId) {
            this.pdpId = pdpId;
        }

        @Override
        public @NonNull Mono<Payload> requestResponse(@NonNull Payload payload) {
            val route = extractRoute(payload);
            val data  = extractData(payload);
            payload.release();

            return Mono.fromCallable(() -> {
                PDP_ID_HOLDER.set(pdpId);
                try {
                    return handleBlockingRequestResponse(route, data);
                } finally {
                    PDP_ID_HOLDER.remove();
                }
            }).subscribeOn(VIRTUAL_THREAD_SCHEDULER);
        }

        @Override
        public @NonNull Flux<Payload> requestStream(@NonNull Payload payload) {
            val route = extractRoute(payload);
            val data  = extractData(payload);
            payload.release();

            return switch (route) {
            case ROUTE_DECIDE           -> handleDecide(data);
            case ROUTE_MULTI_DECIDE     -> handleMultiDecide(data);
            case ROUTE_MULTI_DECIDE_ALL -> handleMultiDecideAll(data);
            default                     -> {
                log.debug(ERROR_IN_ROUTE, route, "Unknown route for request-stream");
                yield Flux.just(encodeDecision(AuthorizationDecision.INDETERMINATE));
            }
            };
        }

        private Payload handleBlockingRequestResponse(String route, byte[] data) {
            return switch (route) {
            case ROUTE_DECIDE_ONCE           -> handleDecideOnceBlocking(data);
            case ROUTE_MULTI_DECIDE_ALL_ONCE -> handleMultiDecideAllOnceBlocking(data);
            default                          -> {
                log.debug(ERROR_IN_ROUTE, route, "Unknown route for request-response");
                yield encodeDecision(AuthorizationDecision.INDETERMINATE);
            }
            };
        }

        private Payload handleDecideOnceBlocking(byte[] data) {
            try {
                val subscription = SaplProtobufCodec.readAuthorizationSubscription(data);
                val decision     = pdp.decideOnceBlocking(subscription);
                return encodeDecision(decision);
            } catch (IOException e) {
                log.debug(ERROR_PARSE_SUBSCRIPTION_FAILED, e.getMessage());
                return encodeDecision(AuthorizationDecision.INDETERMINATE);
            }
        }

        private Payload handleMultiDecideAllOnceBlocking(byte[] data) {
            try {
                val subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                val decision     = pdp.decideAll(subscription).blockFirst();
                return encodeMultiDecision(decision != null ? decision : MultiAuthorizationDecision.indeterminate());
            } catch (IOException e) {
                log.debug(ERROR_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return encodeMultiDecision(MultiAuthorizationDecision.indeterminate());
            }
        }

        private Flux<Payload> handleDecide(byte[] data) {
            try {
                val subscription = SaplProtobufCodec.readAuthorizationSubscription(data);
                return pdp.decide(subscription).onErrorResume(error -> {
                    log.debug(ERROR_IN_ROUTE, ROUTE_DECIDE, error.getMessage());
                    return Flux.just(AuthorizationDecision.INDETERMINATE);
                }).map(this::encodeDecision);
            } catch (IOException e) {
                log.debug(ERROR_PARSE_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeDecision(AuthorizationDecision.INDETERMINATE));
            }
        }

        private Flux<Payload> handleMultiDecide(byte[] data) {
            try {
                val subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                return pdp.decide(subscription).onErrorResume(error -> {
                    log.debug(ERROR_IN_ROUTE, ROUTE_MULTI_DECIDE, error.getMessage());
                    return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
                }).map(this::encodeIdentifiableDecision);
            } catch (IOException e) {
                log.debug(ERROR_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeIdentifiableDecision(IdentifiableAuthorizationDecision.INDETERMINATE));
            }
        }

        private Flux<Payload> handleMultiDecideAll(byte[] data) {
            try {
                val subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                return pdp.decideAll(subscription).onErrorResume(error -> {
                    log.debug(ERROR_IN_ROUTE, ROUTE_MULTI_DECIDE_ALL, error.getMessage());
                    return Flux.just(MultiAuthorizationDecision.indeterminate());
                }).map(this::encodeMultiDecision);
            } catch (IOException e) {
                log.debug(ERROR_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeMultiDecision(MultiAuthorizationDecision.indeterminate()));
            }
        }

        private String extractRoute(Payload payload) {
            val metadata = payload.metadata();
            if (metadata.readableBytes() == 0) {
                return "";
            }
            val routeBytes = new byte[metadata.readableBytes()];
            metadata.readBytes(routeBytes);
            return new String(routeBytes, StandardCharsets.UTF_8);
        }

        private byte[] extractData(Payload payload) {
            val data      = payload.data();
            val dataBytes = new byte[data.readableBytes()];
            data.readBytes(dataBytes);
            return dataBytes;
        }

        private Payload encodeDecision(AuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_DECISION_FAILED, e.getMessage());
                return encodeIndeterminateDecision();
            }
        }

        private Payload encodeIdentifiableDecision(IdentifiableAuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeIdentifiableAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_IDENTIFIABLE_DECISION_FAILED, e.getMessage());
                return encodeIndeterminateDecision();
            }
        }

        private Payload encodeMultiDecision(MultiAuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeMultiAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(ERROR_ENCODE_MULTI_DECISION_FAILED, e.getMessage());
                return encodeIndeterminateDecision();
            }
        }

        private Payload encodeIndeterminateDecision() {
            try {
                return DefaultPayload
                        .create(SaplProtobufCodec.writeAuthorizationDecision(AuthorizationDecision.INDETERMINATE));
            } catch (IOException fallbackError) {
                log.error(ERROR_ENCODE_DECISION_FAILED, fallbackError.getMessage());
                return DefaultPayload.create(new byte[0]);
            }
        }
    }
}
