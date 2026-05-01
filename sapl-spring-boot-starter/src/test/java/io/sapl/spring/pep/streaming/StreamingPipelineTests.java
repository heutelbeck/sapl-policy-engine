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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.util.Maybe;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * Integration tests for {@link StreamingPipeline}: drives the full
 * Reactor adapter end-to-end with controlled PDP and RAP sources to
 * validate observable subscriber behaviour, the lazy RAP subscription
 * lifecycle, and per-item enforcement plumbing.
 * <p>
 * Two complementary nested suites:
 * <ul>
 * <li>{@code ItemFlow} (Layer C) -- validates events the pipeline
 * constructs from RAP payloads as a function of current FSM state.
 * Spec source: "Pre-classification B" diagram.</li>
 * <li>{@code Lifecycle} (Layer D) -- validates Reactor lifecycle
 * propagation: RAP completion / error, PDP error, DENY termination,
 * boundary signals under {@code signalTransitions}, RAP subscription
 * lazy / pause-and-resume.</li>
 * </ul>
 */
class StreamingPipelineTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    /** Small harness for driving the pipeline with controlled sources. */
    private static final class Harness {

        Sinks.Many<AuthorizationDecision> pdp                        = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<Object>                rap                        = Sinks.many().unicast().onBackpressureBuffer();
        EnforcementPlan                   plan                       = new EnforcementPlan(java.util.Map.of());
        AtomicInteger                     rapSupplierInvocationCount = new AtomicInteger();
        boolean                           terminateOnItemEnforcementFailure;
        boolean                           pauseRapDuringSuspend;
        boolean                           signalTransitions;

        Flux<Object> create() {
            Supplier<Flux<Object>> supplier = () -> {
                rapSupplierInvocationCount.incrementAndGet();
                return rap.asFlux();
            };
            return StreamingPipeline.create(terminateOnItemEnforcementFailure, pauseRapDuringSuspend, pdp.asFlux(),
                    d -> plan, supplier, signalTransitions);
        }

        void emitPermit() {
            pdp.tryEmitNext(AuthorizationDecision.PERMIT);
        }

        void emitSuspend() {
            pdp.tryEmitNext(AuthorizationDecision.SUSPEND);
        }

        void emitDeny() {
            pdp.tryEmitNext(AuthorizationDecision.DENY);
        }

        void completePdp() {
            pdp.tryEmitComplete();
        }

        void errorPdp(Throwable t) {
            pdp.tryEmitError(t);
        }

        void emitRap(Object v) {
            rap.tryEmitNext(v);
        }

        void completeRap() {
            rap.tryEmitComplete();
        }

        void errorRap(Throwable t) {
            rap.tryEmitError(t);
        }
    }

    @Nested
    @DisplayName("ItemFlow (Layer C): per-item routing through the pipeline")
    class ItemFlow {

        @Test
        void rapSupplierIsNotInvokedBeforeFirstPermit() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            // Subscribe and push items before any PERMIT. The supplier
            // should not be invoked because the pipeline subscribes RAP
            // only on first PERMIT.
            StepVerifier.create(out.take(Duration.ofMillis(200))).then(() -> h.emitSuspend()).verifyComplete();

            assertThat(h.rapSupplierInvocationCount.get()).isZero();
        }

        @Test
        void rapSupplierIsInvokedOnFirstPermit() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(Duration.ofMillis(200))).then(h::emitPermit).verifyComplete();

            assertThat(h.rapSupplierInvocationCount.get()).isEqualTo(1);
        }

        @Test
        void permittingItemsAreEmittedDownstream() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(2)).then(h::emitPermit).then(() -> h.emitRap("a")).expectNext("a")
                    .then(() -> h.emitRap("b")).expectNext("b").verifyComplete();
        }

        @Test
        void suspendedItemsAreSilentlyDropped() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(Duration.ofMillis(500))).then(h::emitPermit).then(() -> h.emitRap("a"))
                    .expectNext("a").then(h::emitSuspend).then(() -> h.emitRap("b")).then(() -> h.emitRap("c"))
                    .verifyComplete();
        }

        @Test
        void itemsResumeAfterSuspendThenPermit() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(2)).then(h::emitPermit).then(() -> h.emitRap("a")).expectNext("a")
                    .then(h::emitSuspend).then(() -> h.emitRap("dropped")).then(h::emitPermit)
                    .then(() -> h.emitRap("b")).expectNext("b").verifyComplete();
        }

        @Test
        void perItemFailureWithTerminateFlagTrueErrorsTheSubscription() {
            Harness h = new Harness();
            h.terminateOnItemEnforcementFailure = true;
            EnforcementPlan failingPlan = mock(EnforcementPlan.class);
            when(failingPlan.enforceDecisionConstraints(any())).thenReturn(false);
            when(failingPlan.execute(any(Signal.class), anyBoolean()))
                    .thenReturn(new EnforcementResult<>(Maybe.absent(), true));
            h.plan = failingPlan;
            Flux<Object> out = h.create();

            StepVerifier.create(out).then(h::emitPermit).then(() -> h.emitRap("doomed"))
                    .expectError(AccessDeniedException.class).verify(TIMEOUT);
        }

        @Test
        void perItemFailureWithTerminateFlagFalseDoesNotErrorTheSubscription() {
            Harness h = new Harness();
            h.terminateOnItemEnforcementFailure = false;
            EnforcementPlan failingPlan = mock(EnforcementPlan.class);
            when(failingPlan.enforceDecisionConstraints(any())).thenReturn(false);
            when(failingPlan.execute(any(Signal.class), anyBoolean()))
                    .thenReturn(new EnforcementResult<>(Maybe.absent(), true));
            h.plan = failingPlan;
            Flux<Object> out = h.create();

            // After per-item failure, the pipeline transitions to suspended.
            // The next PERMIT should resume items flowing.
            EnforcementPlan goodPlan = mock(EnforcementPlan.class);
            when(goodPlan.enforceDecisionConstraints(any())).thenReturn(false);
            when(goodPlan.execute(any(Signal.class), anyBoolean()))
                    .thenAnswer(inv -> new EnforcementResult<>(Maybe.of(extractValue(inv.getArgument(0))), false));

            StepVerifier.create(out.take(1)).then(h::emitPermit).then(() -> h.emitRap("doomed"))
                    .then(() -> h.plan = goodPlan).then(h::emitPermit).then(() -> h.emitRap("recovered"))
                    .expectNext("recovered").verifyComplete();
        }

        @Test
        void planExecuteIsInvokedOncePerRapItemWhilePermitting() {
            Harness         h   = new Harness();
            EnforcementPlan spy = mock(EnforcementPlan.class);
            when(spy.enforceDecisionConstraints(any())).thenReturn(false);
            when(spy.execute(any(Signal.class), anyBoolean()))
                    .thenAnswer(inv -> new EnforcementResult<>(Maybe.of(extractValue(inv.getArgument(0))), false));
            h.plan = spy;
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(3)).then(h::emitPermit).then(() -> h.emitRap("a")).then(() -> h.emitRap("b"))
                    .then(() -> h.emitRap("c")).expectNext("a", "b", "c").verifyComplete();

            verify(spy, times(3)).execute(any(Signal.class), anyBoolean());
        }

        @Test
        void planExecuteIsNotInvokedForSuspendedItems() {
            Harness         h   = new Harness();
            EnforcementPlan spy = mock(EnforcementPlan.class);
            when(spy.enforceDecisionConstraints(any())).thenReturn(false);
            when(spy.execute(any(Signal.class), anyBoolean()))
                    .thenAnswer(inv -> new EnforcementResult<>(Maybe.of(extractValue(inv.getArgument(0))), false));
            h.plan = spy;
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(1)).then(h::emitPermit).then(() -> h.emitRap("permitted"))
                    .expectNext("permitted").then(h::emitSuspend).then(() -> h.emitRap("suspended-a"))
                    .then(() -> h.emitRap("suspended-b")).verifyComplete();

            // Only the one permitted item should have been executed.
            verify(spy, times(1)).execute(any(Signal.class), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Lifecycle (Layer D): Reactor lifecycle propagation")
    class Lifecycle {

        @Test
        void rapCompletionPropagatesAsSubscriberCompletion() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out).then(h::emitPermit).then(h::completeRap).verifyComplete();
        }

        @Test
        void rapErrorPropagatesAsSubscriberError() {
            Harness          h    = new Harness();
            Flux<Object>     out  = h.create();
            RuntimeException boom = new RuntimeException("rap-boom");

            StepVerifier.create(out).then(h::emitPermit).then(() -> h.errorRap(boom))
                    .expectErrorSatisfies(e -> assertThat(e).isSameAs(boom)).verify(TIMEOUT);
        }

        @Test
        void pdpErrorPropagatesAsSubscriberError() {
            Harness          h    = new Harness();
            Flux<Object>     out  = h.create();
            RuntimeException boom = new RuntimeException("pdp-boom");

            StepVerifier.create(out).then(() -> h.errorPdp(boom))
                    .expectErrorSatisfies(e -> assertThat(e).isSameAs(boom)).verify(TIMEOUT);
        }

        @Test
        void pdpDenyTerminatesSubscriberWithAccessDeniedException() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out).then(h::emitDeny).expectError(AccessDeniedException.class).verify(TIMEOUT);
        }

        @Test
        void emptyPdpFluxIsTreatedAsSingleDeny() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out).then(h::completePdp).expectError(AccessDeniedException.class).verify(TIMEOUT);
        }

        @Test
        void boundarySignalsAreVisibleWhenSignalTransitionsIsTrue() {
            Harness h = new Harness();
            h.signalTransitions = true;
            Flux<Object> out = h.create();

            // Surface the suspend boundary as an AccessDeniedException on
            // the error channel, then continue (not terminating). Use the
            // TransitionSignals helper to consume the signal cleanly.
            AtomicInteger suspendCount = new AtomicInteger();
            Flux<Object>  observed     = TransitionSignals.onSuspend(out, e -> suspendCount.incrementAndGet());

            StepVerifier.create(observed.take(2)).then(h::emitPermit).then(() -> h.emitRap("a")).expectNext("a")
                    .then(h::emitSuspend).then(h::emitPermit).then(() -> h.emitRap("b")).expectNext("b")
                    .verifyComplete();

            assertThat(suspendCount.get()).isEqualTo(1);
        }

        @Test
        void boundarySignalsAreSilentWhenSignalTransitionsIsFalse() {
            Harness h = new Harness();
            h.signalTransitions = false;
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(2)).then(h::emitPermit).then(() -> h.emitRap("a")).expectNext("a")
                    .then(h::emitSuspend).then(h::emitPermit).then(() -> h.emitRap("b")).expectNext("b")
                    .verifyComplete();
        }

        @Test
        void rapSupplierIsInvokedOncePerPermitWhenPauseRapDuringSuspendIsTrue() {
            Harness h = new Harness();
            h.pauseRapDuringSuspend = true;
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(Duration.ofMillis(500))).then(h::emitPermit).then(h::emitSuspend).then(() -> {
                // Replace the RAP sink so the next PERMIT subscribes a fresh source.
                h.rap = Sinks.many().unicast().onBackpressureBuffer();
            }).then(h::emitPermit).verifyComplete();

            assertThat(h.rapSupplierInvocationCount.get()).isEqualTo(2);
        }

        @Test
        void rapSupplierIsInvokedOnceWhenPauseRapDuringSuspendIsFalse() {
            Harness h = new Harness();
            h.pauseRapDuringSuspend = false;
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(Duration.ofMillis(500))).then(h::emitPermit).then(h::emitSuspend)
                    .then(h::emitPermit).verifyComplete();

            assertThat(h.rapSupplierInvocationCount.get()).isEqualTo(1);
        }

        @Test
        void cancellationDisposesPdpAndRapSubscriptions() {
            Harness      h   = new Harness();
            Flux<Object> out = h.create();

            StepVerifier.create(out.take(1)).then(h::emitPermit).then(() -> h.emitRap("a")).expectNext("a")
                    .verifyComplete();

            // After downstream completes (via take(1)), the pipeline should
            // tear down the subscriptions. Subsequent emissions should not
            // resurrect anything; the sink can no longer push to a live
            // subscriber.
            assertThat(h.pdp.currentSubscriberCount()).isZero();
            assertThat(h.rap.currentSubscriberCount()).isZero();
        }

        @Test
        void multipleConsecutivePermitsReplanWithoutEmittingExtraBoundarySignals() {
            Harness h = new Harness();
            h.signalTransitions = true;
            Flux<Object> out = h.create();

            AtomicInteger suspendCount = new AtomicInteger();
            Flux<Object>  observed     = TransitionSignals.onSuspend(out, e -> suspendCount.incrementAndGet());

            StepVerifier.create(observed.take(2)).then(h::emitPermit).then(h::emitPermit).then(h::emitPermit)
                    .then(() -> h.emitRap("a")).expectNext("a").then(() -> h.emitRap("b")).expectNext("b")
                    .verifyComplete();

            assertThat(suspendCount.get()).isZero();
        }
    }

    private static Object extractValue(Signal signal) {
        if (signal instanceof Signal.OutputSignal<?>(var ignoredType, Maybe<?> value)
                && value instanceof Maybe.Present<?>(Object v)) {
            return v;
        }
        if (signal instanceof Signal.ValueSignal<?> vs) {
            return vs.value();
        }
        return null;
    }

}
