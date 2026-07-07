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
package io.sapl.node.rsocket.pdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.RejectedException;
import io.rsocket.exceptions.RejectedSetupException;
import io.rsocket.util.RSocketProxy;
import io.sapl.node.limits.ConcurrencyLimit;
import io.sapl.node.limits.RateLimit;
import io.sapl.node.limits.RejectionReporter;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Optional global admission limits for the RSocket transport: a ceiling on
 * concurrent connections, a per-connection ceiling on concurrent decision
 * streams, and a global rate limit on unary requests. Created only when at
 * least one limit is configured; an unlimited server binds the raw acceptor
 * unchanged, so its request paths carry no admission code.
 * <p>
 * All rejections fail closed with the RSocket error codes the protocol
 * defines as retriable rejections: {@link RejectedSetupException} for
 * over-cap connections and {@link RejectedException} for over-cap streams
 * and over-rate requests, never a policy decision.
 */
final class RSocketLimiter {

    private static final String ERROR_CONNECTION_LIMIT = "Connection limit reached";
    private static final String ERROR_RATE_LIMIT       = "Request rate limit reached";
    private static final String ERROR_STREAM_LIMIT     = "Stream limit reached";

    private record ConnectionGuard(ConcurrencyLimit limit, RejectionReporter reporter) {}

    private record StreamGuard(int maxStreamsPerConnection, RejectionReporter reporter) {}

    private record RateGuard(RateLimit limit, RejectionReporter reporter) {}

    private final @Nullable ConnectionGuard connectionGuard;
    private final @Nullable StreamGuard     streamGuard;
    private final @Nullable RateGuard       rateGuard;

    private RSocketLimiter(@Nullable ConnectionGuard connectionGuard,
            @Nullable StreamGuard streamGuard,
            @Nullable RateGuard rateGuard) {
        this.connectionGuard = connectionGuard;
        this.streamGuard     = streamGuard;
        this.rateGuard       = rateGuard;
    }

    /**
     * Creates a limiter when at least one limit is configured.
     *
     * @param maxConnections concurrent connection ceiling, non-positive means
     * unbounded
     * @param maxStreamsPerConnection per-connection concurrent stream ceiling,
     * non-positive means unbounded
     * @param requestsPerSecond global unary request rate, non-positive means
     * unbounded
     * @param meterRegistry the meter registry, or null when metrics are
     * unavailable
     * @return the limiter, or null when every limit is unbounded
     */
    static @Nullable RSocketLimiter of(int maxConnections, int maxStreamsPerConnection, int requestsPerSecond,
            @Nullable MeterRegistry meterRegistry) {
        if (maxConnections <= 0 && maxStreamsPerConnection <= 0 && requestsPerSecond <= 0) {
            return null;
        }
        val connectionGuard = maxConnections <= 0 ? null
                : new ConnectionGuard(new ConcurrencyLimit(maxConnections), new RejectionReporter("rsocket-connections",
                        "concurrent RSocket connections limited to " + maxConnections, meterRegistry));
        val streamGuard     = maxStreamsPerConnection <= 0 ? null
                : new StreamGuard(maxStreamsPerConnection, new RejectionReporter("rsocket-streams",
                        "concurrent decision streams per RSocket connection limited to " + maxStreamsPerConnection,
                        meterRegistry));
        val rateGuard       = requestsPerSecond <= 0 ? null
                : new RateGuard(new RateLimit(requestsPerSecond), new RejectionReporter("rsocket-requests",
                        "unary RSocket requests limited to " + requestsPerSecond + " per second", meterRegistry));
        return new RSocketLimiter(connectionGuard, streamGuard, rateGuard);
    }

    /**
     * Wraps the acceptor with the configured admission checks.
     *
     * @param delegate the raw acceptor
     * @return the limiting acceptor
     */
    SocketAcceptor wrap(SocketAcceptor delegate) {
        return (setup, sendingSocket) -> {
            ConcurrencyLimit.Permit permit = null;
            if (connectionGuard != null) {
                permit = connectionGuard.limit().tryAcquire();
                if (permit == null) {
                    connectionGuard.reporter().onRejection();
                    return Mono.error(new RejectedSetupException(ERROR_CONNECTION_LIMIT));
                }
            }
            val connectionPermit = permit;
            var accepted         = delegate.accept(setup, sendingSocket).map(this::limitPerConnection);
            if (connectionPermit != null) {
                // The permit is released when the connection closes. The error and
                // cancel paths cover setups that never establish (failed
                // authentication, client abort); release is idempotent, so the
                // overlap with onClose cannot double-free the slot.
                accepted = accepted.doOnNext(
                        socket -> sendingSocket.onClose().doFinally(signal -> connectionPermit.close()).subscribe())
                        .doOnError(error -> connectionPermit.close()).doOnCancel(connectionPermit::close);
            }
            return accepted;
        };
    }

    private RSocket limitPerConnection(RSocket socket) {
        if (streamGuard == null && rateGuard == null) {
            return socket;
        }
        return new LimitingRSocket(socket, streamGuard, rateGuard);
    }

    private static final class LimitingRSocket extends RSocketProxy {

        private final @Nullable ConcurrencyLimit  streamLimit;
        private final @Nullable RejectionReporter streamReporter;
        private final @Nullable RateGuard         rateGuard;

        LimitingRSocket(RSocket source, @Nullable StreamGuard streamGuard, @Nullable RateGuard rateGuard) {
            super(source);
            if (streamGuard == null) {
                this.streamLimit    = null;
                this.streamReporter = null;
            } else {
                this.streamLimit    = new ConcurrencyLimit(streamGuard.maxStreamsPerConnection());
                this.streamReporter = streamGuard.reporter();
            }
            this.rateGuard = rateGuard;
        }

        @Override
        public @NonNull Mono<Payload> requestResponse(@NonNull Payload payload) {
            if (rateGuard != null && !rateGuard.limit().tryAcquire()) {
                rateGuard.reporter().onRejection();
                payload.release();
                return Mono.error(new RejectedException(ERROR_RATE_LIMIT));
            }
            return super.requestResponse(payload);
        }

        @Override
        public @NonNull Flux<Payload> requestStream(@NonNull Payload payload) {
            if (streamLimit == null || streamReporter == null) {
                return super.requestStream(payload);
            }
            val permit = streamLimit.tryAcquire();
            if (permit == null) {
                streamReporter.onRejection();
                payload.release();
                return Flux.error(new RejectedException(ERROR_STREAM_LIMIT));
            }
            return super.requestStream(payload).doFinally(signal -> permit.close());
        }
    }
}
