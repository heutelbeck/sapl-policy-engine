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
package io.sapl.reactive.pdp;

import io.sapl.api.model.Poll;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

@DisplayName("adapt bridge stream lifecycle")
class AdaptBridgeLifecycleTests {

    /**
     * Emits one decision, then either completes, errors, or stays open until
     * closed. Records whether the bridge closed it.
     */
    private static final class TrackingStream implements Stream<AuthorizationDecision> {

        enum Mode {
            COMPLETE,
            ERROR,
            BLOCK
        }

        final AtomicBoolean          closed   = new AtomicBoolean(false);
        private final AtomicBoolean  emitted  = new AtomicBoolean(false);
        private final CountDownLatch released = new CountDownLatch(1);
        private final Mode           mode;

        TrackingStream(Mode mode) {
            this.mode = mode;
        }

        @Override
        public AuthorizationDecision awaitNext() throws InterruptedException {
            if (emitted.compareAndSet(false, true)) {
                return AuthorizationDecision.PERMIT;
            }
            return switch (mode) {
            case COMPLETE -> null;
            case ERROR    -> throw new IllegalStateException("source failed");
            case BLOCK    -> {
                released.await();
                yield null;
            }
            };
        }

        @Override
        public Poll<AuthorizationDecision> tryNext() {
            throw new UnsupportedOperationException("not used by the adapt bridge");
        }

        @Override
        public void close() {
            closed.set(true);
            released.countDown();
        }
    }

    @Test
    @DisplayName("closes the source stream when the source completes")
    void whenSourceCompletesThenStreamClosed() {
        val stream = new TrackingStream(TrackingStream.Mode.COMPLETE);

        StepVerifier.create(DelegatingReactivePolicyDecisionPoint.adapt(() -> stream))
                .expectNext(AuthorizationDecision.PERMIT).verifyComplete();

        await().atMost(Duration.ofSeconds(2)).untilTrue(stream.closed);
    }

    @Test
    @DisplayName("closes the source stream when the source errors")
    void whenSourceErrorsThenStreamClosed() {
        val stream = new TrackingStream(TrackingStream.Mode.ERROR);

        StepVerifier.create(DelegatingReactivePolicyDecisionPoint.adapt(() -> stream))
                .expectNext(AuthorizationDecision.PERMIT).expectError(IllegalStateException.class).verify();

        await().atMost(Duration.ofSeconds(2)).untilTrue(stream.closed);
    }

    @Test
    @DisplayName("closes the source stream when the downstream cancels")
    void whenDownstreamCancelsThenStreamClosed() {
        val stream = new TrackingStream(TrackingStream.Mode.BLOCK);

        StepVerifier.create(DelegatingReactivePolicyDecisionPoint.adapt(() -> stream))
                .expectNext(AuthorizationDecision.PERMIT).thenCancel().verify();

        await().atMost(Duration.ofSeconds(2)).untilTrue(stream.closed);
    }
}
