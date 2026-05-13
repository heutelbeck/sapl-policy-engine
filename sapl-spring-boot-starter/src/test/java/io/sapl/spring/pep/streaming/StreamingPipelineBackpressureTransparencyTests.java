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
package io.sapl.spring.pep.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * Contract tests for the streaming PEP's backpressure transparency.
 * <p>
 * The streaming wrapper must not change the subscriber-visible backpressure
 * characteristics of the protected stream. Downstream demand must propagate
 * to the upstream source. Items dropped under the suspended gate must be
 * topped up so that {@code request(N)} continues to mean "up to N delivered
 * items" from the subscriber's perspective.
 */
@DisplayName("StreamingPipeline backpressure transparency")
class StreamingPipelineBackpressureTransparencyTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    /** Harness with a demand-tracking RAP source. */
    private static final class TrackingHarness {
        Sinks.Many<AuthorizationDecision> pdp                 = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<Object>                rap                 = Sinks.many().unicast().onBackpressureBuffer();
        EnforcementPlan                   plan                = new EnforcementPlan(java.util.Map.of());
        AtomicLong                        upstreamRequested   = new AtomicLong();
        AtomicInteger                     supplierInvocations = new AtomicInteger();
        boolean                           pauseRapDuringSuspend;

        Flux<Object> create() {
            Supplier<Flux<Object>> supplier = () -> {
                supplierInvocations.incrementAndGet();
                return rap.asFlux().doOnRequest(upstreamRequested::addAndGet);
            };
            return StreamingPipeline.create(false, pauseRapDuringSuspend, pdp.asFlux(), d -> plan, supplier, false);
        }

        void resetUpstreamSink() {
            this.rap = Sinks.many().unicast().onBackpressureBuffer();
        }
    }

    @Test
    @DisplayName("downstream request(N) propagates to upstream as request(N)")
    void downstreamRequestNPropagatesToUpstreamAsRequestN() {
        TrackingHarness h   = new TrackingHarness();
        Flux<Object>    out = h.create();

        StepVerifier.create(out, 0L).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> sleep(Duration.ofMillis(50))).thenRequest(3L).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a").then(() -> h.rap.tryEmitNext("b")).expectNext("b")
                .then(() -> h.rap.tryEmitNext("c")).expectNext("c").thenCancel().verify(TIMEOUT);

        assertThat(h.upstreamRequested.get()).isEqualTo(3L);
    }

    @Test
    @DisplayName("downstream request(1) parks upstream at request(1)")
    void downstreamRequestOneParksUpstreamAtOne() {
        TrackingHarness h   = new TrackingHarness();
        Flux<Object>    out = h.create();

        StepVerifier.create(out, 0L).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> sleep(Duration.ofMillis(50))).thenRequest(1L).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a").thenCancel().verify(TIMEOUT);

        assertThat(h.upstreamRequested.get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("downstream demand requested before PERMIT is replayed to upstream on subscribe")
    void earlyDownstreamDemandReplaysToUpstreamOnPermit() {
        TrackingHarness h   = new TrackingHarness();
        Flux<Object>    out = h.create();

        StepVerifier.create(out, 0L).thenRequest(2L).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT)).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a").then(() -> h.rap.tryEmitNext("b")).expectNext("b")
                .thenCancel().verify(TIMEOUT);

        assertThat(h.upstreamRequested.get()).isEqualTo(2L);
    }

    @Test
    @DisplayName("gated drop under suspended state tops up the upstream demand by one")
    void gatedDropTopsUpUpstreamDemand() {
        TrackingHarness h   = new TrackingHarness();
        Flux<Object>    out = h.create();

        StepVerifier.create(out, 0L).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> sleep(Duration.ofMillis(50))).thenRequest(5L).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a")
                .then(() -> h.pdp.tryEmitNext(AuthorizationDecision.SUSPEND)).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("dropped")).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT)).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("b")).expectNext("b").thenCancel().verify(TIMEOUT);

        assertThat(h.upstreamRequested.get()).isEqualTo(6L);
    }

    @Test
    @DisplayName("pending downstream demand is replayed to a fresh subscription on resume after pause")
    void pendingDemandReplaysToFreshSubscriptionOnResumeAfterPause() {
        TrackingHarness h = new TrackingHarness();
        h.pauseRapDuringSuspend = true;
        Flux<Object> out = h.create();

        StepVerifier.create(out, 0L).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> sleep(Duration.ofMillis(50))).thenRequest(4L).then(() -> sleep(Duration.ofMillis(50)))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a")
                .then(() -> h.pdp.tryEmitNext(AuthorizationDecision.SUSPEND)).then(() -> sleep(Duration.ofMillis(50)))
                .then(h::resetUpstreamSink).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> sleep(Duration.ofMillis(50))).then(() -> h.rap.tryEmitNext("b")).expectNext("b")
                .thenCancel().verify(TIMEOUT);

        assertThat(h.supplierInvocations.get()).isEqualTo(2);
        assertThat(h.upstreamRequested.get()).isGreaterThanOrEqualTo(3L);
    }

    @Test
    @DisplayName("cancel during in-flight emission stops upstream items reaching the subscriber")
    void cancelStopsItemsReachingSubscriber() {
        TrackingHarness h   = new TrackingHarness();
        Flux<Object>    out = h.create();

        StepVerifier.create(out.take(1)).then(() -> h.pdp.tryEmitNext(AuthorizationDecision.PERMIT))
                .then(() -> h.rap.tryEmitNext("a")).expectNext("a").verifyComplete();

        // After take(1) cancels the subscription, further upstream emissions
        // must not be observable. The sink no longer has a live subscriber.
        assertThat(h.rap.currentSubscriberCount()).isZero();
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
