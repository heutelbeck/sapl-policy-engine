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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.time.Duration;
import org.mockito.junit.jupiter.MockitoExtension;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.RejectedException;
import io.rsocket.exceptions.RejectedSetupException;
import io.rsocket.util.ByteBufPayload;
import io.rsocket.util.DefaultPayload;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@DisplayName("RSocketLimiter")
@ExtendWith(MockitoExtension.class)
class RSocketLimiterTests {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    @Mock
    private SocketAcceptor delegate;

    @Mock
    private RSocket handler;

    @Test
    @DisplayName("no configured limit means no limiter, so the raw acceptor is bound unchanged")
    void whenAllLimitsUnboundedThenNoLimiterCreated() {
        assertThat(RSocketLimiter.of(0, 0, 0, null)).isNull();
        assertThat(RSocketLimiter.of(-1, -1, -1, null)).isNull();
    }

    @Nested
    @DisplayName("connection ceiling")
    class ConnectionCeiling {

        @Test
        @DisplayName("connections beyond the ceiling are rejected at setup and admitted again after a close")
        void whenCeilingReachedThenSetupRejectedUntilSlotFrees() {
            val limiter  = RSocketLimiter.of(1, 0, 0, null);
            val acceptor = limiter.wrap(delegate);
            val onClose  = Sinks.<Void>empty();
            val sending  = mock(RSocket.class);
            lenient().when(sending.onClose()).thenReturn(onClose.asMono());
            when(delegate.accept(any(), any())).thenReturn(Mono.just(handler));

            val setup = mock(ConnectionSetupPayload.class);
            StepVerifier.create(acceptor.accept(setup, sending)).expectNextCount(1).verifyComplete();
            StepVerifier.create(acceptor.accept(setup, sending)).expectError(RejectedSetupException.class)
                    .verify(VERIFY_TIMEOUT);

            onClose.tryEmitEmpty();
            StepVerifier.create(acceptor.accept(setup, sending)).expectNextCount(1).verifyComplete();
        }

        @Test
        @DisplayName("a setup that fails authentication frees its slot immediately")
        void whenSetupFailsThenSlotFreed() {
            val limiter  = RSocketLimiter.of(1, 0, 0, null);
            val acceptor = limiter.wrap(delegate);
            val onClose  = Sinks.<Void>empty();
            val sending  = mock(RSocket.class);
            lenient().when(sending.onClose()).thenReturn(onClose.asMono());
            when(delegate.accept(any(), any())).thenReturn(Mono.error(new RejectedSetupException("auth failed")))
                    .thenReturn(Mono.just(handler));

            val setup = mock(ConnectionSetupPayload.class);
            StepVerifier.create(acceptor.accept(setup, sending)).expectError(RejectedSetupException.class)
                    .verify(VERIFY_TIMEOUT);
            StepVerifier.create(acceptor.accept(setup, sending)).expectNextCount(1).verifyComplete();
        }
    }

    @Nested
    @DisplayName("per-connection stream ceiling")
    class StreamCeiling {

        @Test
        @DisplayName("streams beyond the per-connection ceiling are rejected and admitted again after one ends")
        void whenStreamCeilingReachedThenStreamRejectedUntilOneEnds() {
            val limited = acceptWithLimits(0, 1, 0);
            when(handler.requestStream(any())).thenReturn(Flux.never());

            val open = limited.requestStream(DefaultPayload.create("first")).subscribe();

            val rejectedPayload = ByteBufPayload.create("second");
            StepVerifier.create(limited.requestStream(rejectedPayload)).expectError(RejectedException.class)
                    .verify(VERIFY_TIMEOUT);
            assertThat(rejectedPayload.refCnt()).as("rejected stream payload is released").isZero();

            open.dispose();
            StepVerifier.create(limited.requestStream(DefaultPayload.create("third"))).expectSubscription().thenCancel()
                    .verify(VERIFY_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("unary request rate")
    class UnaryRate {

        @Test
        @DisplayName("unary requests beyond the configured rate are rejected as retriable")
        void whenOverRateThenRequestRejected() {
            val limited = acceptWithLimits(0, 0, 1);
            when(handler.requestResponse(any())).thenReturn(Mono.never());

            val first = limited.requestResponse(DefaultPayload.create("first")).subscribe();

            val rejectedPayload = ByteBufPayload.create("second");
            StepVerifier.create(limited.requestResponse(rejectedPayload)).expectError(RejectedException.class)
                    .verify(VERIFY_TIMEOUT);
            assertThat(rejectedPayload.refCnt()).as("rejected unary payload is released").isZero();

            first.dispose();
        }
    }

    private RSocket acceptWithLimits(int maxConnections, int maxStreamsPerConnection, int requestsPerSecond) {
        val limiter = RSocketLimiter.of(maxConnections, maxStreamsPerConnection, requestsPerSecond, null);
        val sending = mock(RSocket.class);
        lenient().when(sending.onClose()).thenReturn(Sinks.<Void>empty().asMono());
        when(delegate.accept(any(), any())).thenReturn(Mono.just(handler));
        return limiter.wrap(delegate).accept(mock(ConnectionSetupPayload.class), sending).block();
    }
}
