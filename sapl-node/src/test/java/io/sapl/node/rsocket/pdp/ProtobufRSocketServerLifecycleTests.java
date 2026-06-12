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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;

/**
 * Specifications for {@link ProtobufRSocketServerLifecycle}.
 * <p>
 * The lifecycle's job is to bind the protobuf RSocket transport when
 * enabled and stay completely out of the way when disabled. The tests
 * cover the contracts that do not require an actual port bind: the
 * disabled path must be a no-op everywhere, misconfiguration of
 * {@code maxInboundPayloadSize} must fail loudly at start time, and
 * stop must be safe to call without a prior successful start. Live
 * port-bind paths are exercised by {@code RSocketTransportIT}.
 */
@DisplayName("ProtobufRSocketServerLifecycle")
@ExtendWith(MockitoExtension.class)
class ProtobufRSocketServerLifecycleTests {

    private static final String BIND_ADDRESS    = "127.0.0.1";
    private static final int    PORT            = 7000;
    private static final int    VALID_PAYLOAD   = 65_536;
    private static final int    INVALID_PAYLOAD = 0;

    @Mock
    private BlockingPolicyDecisionPoint blockingPdp;

    @Mock
    private ReactivePolicyDecisionPoint pdp;

    @Mock
    private RSocketConnectionAuthenticator authenticator;

    @Nested
    @DisplayName("disabled")
    class Disabled {

        @Test
        @DisplayName("start does nothing and isRunning stays false when enabled is false")
        void whenDisabledThenStartIsNoOp() {
            val sut = new ProtobufRSocketServerLifecycle(false, BIND_ADDRESS, PORT, null, VALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            sut.start();

            assertThat(sut.isRunning()).isFalse();
        }

        @Test
        @DisplayName("isAutoStartup mirrors the enabled flag so Spring will not try to start a disabled lifecycle")
        void whenDisabledThenIsAutoStartupFalse() {
            val sut = new ProtobufRSocketServerLifecycle(false, BIND_ADDRESS, PORT, null, VALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            assertThat(sut.isAutoStartup()).isFalse();
        }

        @Test
        @DisplayName("a misconfigured maxInboundPayloadSize is ignored when disabled (no early validation)")
        void whenDisabledThenInvalidPayloadDoesNotThrow() {
            val sut = new ProtobufRSocketServerLifecycle(false, BIND_ADDRESS, PORT, null, INVALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            assertThatCode(sut::start).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("start invoked twice on a disabled lifecycle still results in not-running, no exception")
        void whenDisabledAndStartCalledTwiceThenStillNotRunning() {
            val sut = new ProtobufRSocketServerLifecycle(false, BIND_ADDRESS, PORT, null, VALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            sut.start();
            sut.start();

            assertThat(sut.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("misconfiguration when enabled")
    class Misconfiguration {

        @Test
        @DisplayName("a non-positive maxInboundPayloadSize throws IllegalStateException so the misconfig is visible at startup")
        void whenEnabledAndPayloadSizeNonPositiveThenStartThrows() {
            val sut = new ProtobufRSocketServerLifecycle(true, BIND_ADDRESS, PORT, null, INVALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            assertThatThrownBy(sut::start).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("max-inbound-payload-size");
        }

        @Test
        @DisplayName("isAutoStartup returns true when enabled, even with misconfigured maxInboundPayloadSize")
        void whenEnabledThenIsAutoStartupTrueRegardlessOfPayloadConfig() {
            val sut = new ProtobufRSocketServerLifecycle(true, BIND_ADDRESS, PORT, null, INVALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            assertThat(sut.isAutoStartup()).isTrue();
        }
    }

    @Nested
    @DisplayName("stop without prior start")
    class StopWithoutStart {

        @Test
        @DisplayName("stop on a never-started lifecycle is a no-op and does not NPE")
        void whenStopWithoutStartThenNoException() {
            val sut = new ProtobufRSocketServerLifecycle(true, BIND_ADDRESS, PORT, null, VALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            assertThatCode(sut::stop).doesNotThrowAnyException();
            assertThat(sut.isRunning()).isFalse();
        }

        @Test
        @DisplayName("stop after a no-op disabled start is a no-op and leaves running false")
        void whenDisabledStartThenStopIsSafe() {
            val sut = new ProtobufRSocketServerLifecycle(false, BIND_ADDRESS, PORT, null, VALID_PAYLOAD, blockingPdp,
                    pdp, authenticator, null);

            sut.start();
            assertThatCode(sut::stop).doesNotThrowAnyException();
            assertThat(sut.isRunning()).isFalse();
        }
    }
}
