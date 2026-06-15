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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.model.Poll;
import io.sapl.api.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Multi-subscription decision conflation")
class ConflateBySubscriptionTests {

    private static IdentifiableAuthorizationDecision decision(String subscriptionId, AuthorizationDecision decision) {
        return new IdentifiableAuthorizationDecision(subscriptionId, decision);
    }

    /**
     * Emits a scripted sequence, then blocks (an open decision stream) until
     * interrupted. Signals {@code allBuffered} once the consumer has pulled
     * every scripted item, which guarantees the bridge has buffered them all.
     */
    private static final class ScriptedStream implements Stream<IdentifiableAuthorizationDecision> {
        private final ConcurrentLinkedQueue<IdentifiableAuthorizationDecision> scripted;
        private final CountDownLatch                                           allBuffered;
        private final CountDownLatch                                           blockForever = new CountDownLatch(1);

        ScriptedStream(List<IdentifiableAuthorizationDecision> scripted, CountDownLatch allBuffered) {
            this.scripted    = new ConcurrentLinkedQueue<>(scripted);
            this.allBuffered = allBuffered;
        }

        @Override
        public IdentifiableAuthorizationDecision awaitNext() throws InterruptedException {
            val item = scripted.poll();
            if (item != null) {
                return item;
            }
            allBuffered.countDown();
            blockForever.await();
            return null;
        }

        @Override
        public Poll<IdentifiableAuthorizationDecision> tryNext() {
            throw new UnsupportedOperationException("not used by the conflating bridge");
        }

        @Override
        public void close() {
            blockForever.countDown();
        }
    }

    @Test
    @DisplayName("a slow consumer keeps the latest decision per subscription and loses no other subscription")
    void whenSlowConsumerThenLatestPerSubscriptionPreserved() {
        val allBuffered = new CountDownLatch(1);
        // "a" updates twice, "b" once. A global latest-wins strategy would evict "a"
        // entirely.
        val script = List.of(decision("a", AuthorizationDecision.PERMIT), decision("a", AuthorizationDecision.DENY),
                decision("b", AuthorizationDecision.PERMIT));

        val flux = DelegatingReactivePolicyDecisionPoint
                .conflateBySubscription(() -> new ScriptedStream(script, allBuffered));

        StepVerifier.create(flux, 0).then(() -> awaitBuffered(allBuffered)).thenRequest(3)
                .expectNext(decision("a", AuthorizationDecision.DENY))
                .expectNext(decision("b", AuthorizationDecision.PERMIT)).thenCancel().verify(Duration.ofSeconds(5));
    }

    private static void awaitBuffered(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
