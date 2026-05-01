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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link TransitionSignals}, the subscriber-side helper that
 * translates the streaming PEP's non-terminal boundary exceptions
 * ({@link AccessDeniedException} for suspend boundaries,
 * {@link AccessGrantedException} for grant boundaries) into ordinary
 * callbacks and optional substitute emissions.
 * <p>
 * Test pattern mirrors the streaming PEP's actual output shape: a
 * {@code flatMap} barrier that raises boundary exceptions per item.
 * That barrier is what makes {@code onErrorContinue} work; tests must
 * reproduce it for the helper to behave under test as it does in
 * production.
 */
class TransitionSignalsTests {

    private static final String SUSPEND_MESSAGE = "Stream suspended";
    private static final String OTHER_ERROR     = "Other error";

    private static Flux<String> pepLikeFlux(String... pattern) {
        return Flux.fromArray(pattern).flatMap(item -> switch (item) {
        case "SUSPEND" -> Mono.error(new AccessDeniedException(SUSPEND_MESSAGE));
        case "GRANT"   -> Mono.<String>error(new AccessGrantedException(AuthorizationDecision.PERMIT));
        case "ERROR"   -> Mono.<String>error(new RuntimeException(OTHER_ERROR));
        default        -> Mono.just(item);
        });
    }

    @Nested
    @DisplayName("onSuspend (observe-only)")
    class OnSuspendObserveOnly {

        @Test
        void invokesObserverPerSuspendAndContinues() {
            var captured = new ArrayList<String>();
            var source   = pepLikeFlux("A", "SUSPEND", "B", "SUSPEND", "C");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> captured.add(e.getMessage())))
                    .expectNext("A", "B", "C").verifyComplete();

            assertThat(captured).containsExactly(SUSPEND_MESSAGE, SUSPEND_MESSAGE);
        }

        @Test
        void doesNotInvokeObserverWhenNoSuspendSignals() {
            var count  = new AtomicInteger();
            var source = pepLikeFlux("A", "B", "C");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> count.incrementAndGet()))
                    .expectNext("A", "B", "C").verifyComplete();

            assertThat(count.get()).isZero();
        }

        @Test
        void completesWhenSourceIsEmpty() {
            StepVerifier.create(TransitionSignals.onSuspend(Flux.<String>empty(), e -> {})).verifyComplete();
        }

        @Test
        void completesWhenAllSignalsAreSuspend() {
            var source = pepLikeFlux("SUSPEND", "SUSPEND");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {})).verifyComplete();
        }

        @Test
        void propagatesNonBoundaryErrors() {
            var count  = new AtomicInteger();
            var source = pepLikeFlux("A", "SUSPEND", "ERROR");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> count.incrementAndGet())).expectNext("A")
                    .expectError(RuntimeException.class).verify();

            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        void ignoresGrantBoundary() {
            var count  = new AtomicInteger();
            var source = pepLikeFlux("A", "GRANT", "B");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> count.incrementAndGet())).expectNext("A", "B")
                    .verifyComplete();

            assertThat(count.get()).isZero();
        }
    }

    @Nested
    @DisplayName("onSuspend (observe and emit substitute)")
    class OnSuspendWithSubstitute {

        @Test
        void emitsSubstitutePerSuspendBoundary() {
            var source = pepLikeFlux("A", "SUSPEND", "B", "SUSPEND", "C");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {}, () -> "SUB"))
                    .expectNext("A", "SUB", "B", "SUB", "C").verifyComplete();
        }

        @Test
        void invokesObserverAndEmitsSubstitute() {
            var captured = new ArrayList<String>();
            var source   = pepLikeFlux("A", "SUSPEND", "B");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> captured.add(e.getMessage()), () -> "SUB"))
                    .expectNext("A", "SUB", "B").verifyComplete();

            assertThat(captured).containsExactly(SUSPEND_MESSAGE);
        }

        @Test
        void invokesSupplierFreshlyPerSuspendBoundary() {
            var counter = new AtomicInteger();
            var source  = pepLikeFlux("SUSPEND", "SUSPEND", "SUSPEND");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {}, () -> "R" + counter.incrementAndGet()))
                    .expectNext("R1", "R2", "R3").verifyComplete();
        }

        @Test
        void preservesOrderingAcrossSuspendBoundaries() {
            var source = pepLikeFlux("1", "SUSPEND", "2", "SUSPEND", "3");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {}, () -> "X"))
                    .expectNext("1", "X", "2", "X", "3").verifyComplete();
        }

        @Test
        void propagatesNonBoundaryErrorsAfterEmittingSubstitute() {
            var source = pepLikeFlux("A", "SUSPEND", "ERROR");

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {}, () -> "SUB")).expectNext("A", "SUB")
                    .expectError(RuntimeException.class).verify();
        }

        @Test
        void worksWithNonStringElementType() {
            Flux<Integer> source = Flux.just(1, 2, 3).flatMap(i -> {
                if (i == 2) {
                    return Mono.<Integer>error(new AccessDeniedException(SUSPEND_MESSAGE));
                }
                return Mono.just(i);
            });

            StepVerifier.create(TransitionSignals.onSuspend(source, e -> {}, () -> -1)).expectNext(1, -1, 3)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("onGranted (observe-only)")
    class OnGrantedObserveOnly {

        @Test
        void invokesObserverPerGrantAndContinues() {
            var captured = new ArrayList<String>();
            var source   = pepLikeFlux("A", "GRANT", "B", "GRANT", "C");

            StepVerifier.create(TransitionSignals.onGranted(source, e -> captured.add(e.getMessage())))
                    .expectNext("A", "B", "C").verifyComplete();

            assertThat(captured).hasSize(2).allSatisfy(m -> assertThat(m).isEqualTo("Access granted."));
        }

        @Test
        void ignoresSuspendBoundary() {
            var count  = new AtomicInteger();
            var source = pepLikeFlux("A", "SUSPEND", "B");

            StepVerifier.create(TransitionSignals.onGranted(source, e -> count.incrementAndGet())).expectNext("A", "B")
                    .verifyComplete();

            assertThat(count.get()).isZero();
        }

        @Test
        void propagatesNonBoundaryErrors() {
            var source = pepLikeFlux("A", "GRANT", "ERROR");

            StepVerifier.create(TransitionSignals.onGranted(source, e -> {})).expectNext("A")
                    .expectError(RuntimeException.class).verify();
        }
    }

    @Nested
    @DisplayName("onGranted (observe and emit substitute)")
    class OnGrantedWithSubstitute {

        @Test
        void emitsSubstitutePerGrantBoundary() {
            var source = pepLikeFlux("A", "GRANT", "B");

            StepVerifier.create(TransitionSignals.onGranted(source, e -> {}, () -> "RESUMED"))
                    .expectNext("A", "RESUMED", "B").verifyComplete();
        }

        @Test
        void invokesObserverAndEmits() {
            var captured = new AtomicInteger();
            var source   = pepLikeFlux("A", "GRANT", "B");

            StepVerifier.create(TransitionSignals.onGranted(source, e -> captured.incrementAndGet(), () -> "R"))
                    .expectNext("A", "R", "B").verifyComplete();

            assertThat(captured.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("onTransitions (observe both directions)")
    class OnTransitionsObserveOnly {

        @Test
        void observesBothBoundaries() {
            var suspendCount = new AtomicInteger();
            var grantCount   = new AtomicInteger();
            var source       = pepLikeFlux("A", "SUSPEND", "B", "GRANT", "C", "SUSPEND", "D");

            StepVerifier.create(TransitionSignals.onTransitions(source, s -> suspendCount.incrementAndGet(),
                    g -> grantCount.incrementAndGet())).expectNext("A", "B", "C", "D").verifyComplete();

            assertThat(suspendCount.get()).isEqualTo(2);
            assertThat(grantCount.get()).isEqualTo(1);
        }

        @Test
        void propagatesNonBoundaryErrors() {
            var source = pepLikeFlux("A", "SUSPEND", "ERROR");

            StepVerifier.create(TransitionSignals.onTransitions(source, s -> {}, g -> {})).expectNext("A")
                    .expectError(RuntimeException.class).verify();
        }

        @Test
        void completesWhenSourceIsEmpty() {
            StepVerifier.create(TransitionSignals.onTransitions(Flux.<String>empty(), s -> {}, g -> {}))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("onTransitions (observe and emit per-direction substitutes)")
    class OnTransitionsWithSubstitute {

        @Test
        void emitsDirectionSpecificSubstitutes() {
            var suspendCount = new AtomicInteger();
            var grantCount   = new AtomicInteger();
            var source       = pepLikeFlux("A", "SUSPEND", "B", "GRANT", "C");

            StepVerifier
                    .create(TransitionSignals.onTransitions(source, s -> suspendCount.incrementAndGet(), () -> "S",
                            g -> grantCount.incrementAndGet(), () -> "G"))
                    .expectNext("A", "S", "B", "G", "C").verifyComplete();

            assertThat(suspendCount.get()).isEqualTo(1);
            assertThat(grantCount.get()).isEqualTo(1);
        }

        @Test
        void preservesOrderingWithInterleavedBoundaries() {
            var source = pepLikeFlux("SUSPEND", "1", "GRANT", "2", "SUSPEND");

            StepVerifier.create(TransitionSignals.onTransitions(source, s -> {}, () -> "S", g -> {}, () -> "G"))
                    .expectNext("S", "1", "G", "2", "S").verifyComplete();
        }

        @Test
        void propagatesNonBoundaryErrors() {
            var source = pepLikeFlux("A", "SUSPEND", "ERROR");

            StepVerifier.create(TransitionSignals.onTransitions(source, s -> {}, () -> "S", g -> {}, () -> "G"))
                    .expectNext("A", "S").expectError(RuntimeException.class).verify();
        }
    }
}
