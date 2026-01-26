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

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

/**
 * Direct RSocket acceptor using protobuf serialization for high-performance PDP
 * communication. Bypasses Spring Messaging layer to reduce overhead.
 */
@Slf4j
@RequiredArgsConstructor
public class ProtobufRSocketAcceptor implements SocketAcceptor {

    private static final String ROUTE_DECIDE                = "decide";
    private static final String ROUTE_DECIDE_ONCE           = "decide-once";
    private static final String ROUTE_MULTI_DECIDE          = "multi-decide";
    private static final String ROUTE_MULTI_DECIDE_ALL      = "multi-decide-all";
    private static final String ROUTE_MULTI_DECIDE_ALL_ONCE = "multi-decide-all-once";

    private static final String LOG_ENCODE_DECISION_FAILED              = "Failed to encode decision: {}";
    private static final String LOG_ENCODE_IDENTIFIABLE_DECISION_FAILED = "Failed to encode identifiable decision: {}";
    private static final String LOG_ENCODE_MULTI_DECISION_FAILED        = "Failed to encode multi-decision: {}";
    private static final String LOG_ERROR_IN_ROUTE                      = "Error in {}: {}";
    private static final String LOG_PARSE_MULTI_SUBSCRIPTION_FAILED     = "Failed to parse multi-subscription: {}";
    private static final String LOG_PARSE_SUBSCRIPTION_FAILED           = "Failed to parse subscription: {}";

    private final PolicyDecisionPoint pdp;

    @NonNull
    @Override
    public Mono<RSocket> accept(@NonNull ConnectionSetupPayload setup, @NonNull RSocket sendingSocket) {
        return Mono.just(new ProtobufRSocket());
    }

    private final class ProtobufRSocket implements RSocket {

        @NonNull
        @Override
        public Mono<Payload> requestResponse(@NonNull Payload payload) {
            var route = extractRoute(payload);
            var data  = extractData(payload);
            payload.release();

            return switch (route) {
            case ROUTE_DECIDE_ONCE           -> handleDecideOnce(data);
            case ROUTE_MULTI_DECIDE_ALL_ONCE -> handleMultiDecideAllOnce(data);
            default                          ->
                Mono.error(new IllegalArgumentException("Unknown route for request-response: " + route));
            };
        }

        @NonNull
        @Override
        public Flux<Payload> requestStream(@NonNull Payload payload) {
            var route = extractRoute(payload);
            var data  = extractData(payload);
            payload.release();

            return switch (route) {
            case ROUTE_DECIDE           -> handleDecide(data);
            case ROUTE_MULTI_DECIDE     -> handleMultiDecide(data);
            case ROUTE_MULTI_DECIDE_ALL -> handleMultiDecideAll(data);
            default                     ->
                Flux.error(new IllegalArgumentException("Unknown route for request-stream: " + route));
            };
        }

        private String extractRoute(Payload payload) {
            var metadata = payload.metadata();
            if (metadata.readableBytes() == 0) {
                return "";
            }
            // Simple routing: metadata contains route as UTF-8 string
            // For composite metadata, additional parsing would be needed
            var routeBytes = new byte[metadata.readableBytes()];
            metadata.readBytes(routeBytes);
            return new String(routeBytes, StandardCharsets.UTF_8);
        }

        private byte[] extractData(Payload payload) {
            var data      = payload.data();
            var dataBytes = new byte[data.readableBytes()];
            data.readBytes(dataBytes);
            return dataBytes;
        }

        private Mono<Payload> handleDecideOnce(byte[] data) {
            try {
                var subscription = SaplProtobufCodec.readAuthorizationSubscription(data);
                return pdp.decide(subscription).onErrorResume(error -> {
                    log.debug(LOG_ERROR_IN_ROUTE, ROUTE_DECIDE_ONCE, error.getMessage());
                    return Flux.just(AuthorizationDecision.INDETERMINATE);
                }).next().map(this::encodeDecision);
            } catch (IOException e) {
                log.debug(LOG_PARSE_SUBSCRIPTION_FAILED, e.getMessage());
                return Mono.just(encodeDecision(AuthorizationDecision.INDETERMINATE));
            }
        }

        private Flux<Payload> handleDecide(byte[] data) {
            try {
                var subscription = SaplProtobufCodec.readAuthorizationSubscription(data);
                return pdp.decide(subscription).onErrorResume(error -> {
                    log.debug(LOG_ERROR_IN_ROUTE, ROUTE_DECIDE, error.getMessage());
                    return Flux.just(AuthorizationDecision.INDETERMINATE);
                }).map(this::encodeDecision);
            } catch (IOException e) {
                log.debug(LOG_PARSE_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeDecision(AuthorizationDecision.INDETERMINATE));
            }
        }

        private Flux<Payload> handleMultiDecide(byte[] data) {
            try {
                var subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                return pdp.decide(subscription).onErrorResume(error -> {
                    log.debug(LOG_ERROR_IN_ROUTE, ROUTE_MULTI_DECIDE, error.getMessage());
                    return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
                }).map(this::encodeIdentifiableDecision);
            } catch (IOException e) {
                log.debug(LOG_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeIdentifiableDecision(IdentifiableAuthorizationDecision.INDETERMINATE));
            }
        }

        private Flux<Payload> handleMultiDecideAll(byte[] data) {
            try {
                var subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                return pdp.decideAll(subscription).onErrorResume(error -> {
                    log.debug(LOG_ERROR_IN_ROUTE, ROUTE_MULTI_DECIDE_ALL, error.getMessage());
                    return Flux.just(MultiAuthorizationDecision.indeterminate());
                }).map(this::encodeMultiDecision);
            } catch (IOException e) {
                log.debug(LOG_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return Flux.just(encodeMultiDecision(MultiAuthorizationDecision.indeterminate()));
            }
        }

        private Mono<Payload> handleMultiDecideAllOnce(byte[] data) {
            try {
                var subscription = SaplProtobufCodec.readMultiAuthorizationSubscription(data);
                return pdp.decideAll(subscription).onErrorResume(error -> {
                    log.debug(LOG_ERROR_IN_ROUTE, ROUTE_MULTI_DECIDE_ALL_ONCE, error.getMessage());
                    return Flux.just(MultiAuthorizationDecision.indeterminate());
                }).next().map(this::encodeMultiDecision);
            } catch (IOException e) {
                log.debug(LOG_PARSE_MULTI_SUBSCRIPTION_FAILED, e.getMessage());
                return Mono.just(encodeMultiDecision(MultiAuthorizationDecision.indeterminate()));
            }
        }

        private Payload encodeDecision(AuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(LOG_ENCODE_DECISION_FAILED, e.getMessage());
                return DefaultPayload.create(new byte[0]);
            }
        }

        private Payload encodeIdentifiableDecision(IdentifiableAuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeIdentifiableAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(LOG_ENCODE_IDENTIFIABLE_DECISION_FAILED, e.getMessage());
                return DefaultPayload.create(new byte[0]);
            }
        }

        private Payload encodeMultiDecision(MultiAuthorizationDecision decision) {
            try {
                return DefaultPayload.create(SaplProtobufCodec.writeMultiAuthorizationDecision(decision));
            } catch (IOException e) {
                log.error(LOG_ENCODE_MULTI_DECISION_FAILED, e.getMessage());
                return DefaultPayload.create(new byte[0]);
            }
        }
    }
}
