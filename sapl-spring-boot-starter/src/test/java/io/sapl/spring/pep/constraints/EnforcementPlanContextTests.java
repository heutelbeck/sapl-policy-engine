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
package io.sapl.spring.pep.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@DisplayName("EnforcementPlanContext")
class EnforcementPlanContextTests {

    private static final EnforcementPlan PLAN_A = new EnforcementPlan(Map.of());
    private static final EnforcementPlan PLAN_B = new EnforcementPlan(Map.of());

    @AfterEach
    void clearThreadLocal() {
        EnforcementPlanContext.bindBlocking(null);
    }

    @Nested
    @DisplayName("Reactor context propagation")
    class ReactorContext {

        @Test
        @DisplayName("withReactor binds the plan visible to currentReactor inside the chain")
        void givenWithReactorThenCurrentReactorReturnsPlan() {
            val seenInside = new AtomicReference<EnforcementPlan>();
            val body       = Mono.deferContextual(ctx -> {
                               seenInside.set(EnforcementPlanContext.currentReactor(ctx).orElse(null));
                               return Mono.just("ok");
                           });

            StepVerifier.create(EnforcementPlanContext.withReactor(PLAN_A, body)).expectNext("ok").verifyComplete();

            assertThat(seenInside.get()).isSameAs(PLAN_A);
        }

        @Test
        @DisplayName("currentReactor returns empty when no plan is bound")
        void givenNoPlanWhenCurrentReactorThenEmpty() {
            val seenInside = new AtomicReference<EnforcementPlan>();
            val body       = Mono.deferContextual(ctx -> {
                               seenInside.set(EnforcementPlanContext.currentReactor(ctx).orElse(null));
                               return Mono.just("ok");
                           });

            StepVerifier.create(body).expectNext("ok").verifyComplete();

            assertThat(seenInside.get()).isNull();
        }

        @Test
        @DisplayName("Plan survives across publishOn/subscribeOn scheduler hops")
        void givenSchedulerHopsThenPlanStillVisible() {
            val seenAfterHop = new AtomicReference<EnforcementPlan>();
            val body         = Mono.just("trigger").publishOn(Schedulers.parallel()).flatMap(s -> Mono.just(s + "-1"))
                    .publishOn(Schedulers.boundedElastic()).flatMap(s -> Mono.deferContextual(ctx -> {
                                         seenAfterHop.set(EnforcementPlanContext.currentReactor(ctx).orElse(null));
                                         return Mono.just(s + "-2");
                                     }));

            StepVerifier.create(EnforcementPlanContext.withReactor(PLAN_A, body)).expectNextCount(1).verifyComplete();

            assertThat(seenAfterHop.get()).isSameAs(PLAN_A);
        }

        @Test
        @DisplayName("Concurrent subscribers see independent plan bindings")
        void givenConcurrentSubscribersThenEachSeesItsOwnPlan() {
            val builder = (java.util.function.Function<EnforcementPlan, Mono<EnforcementPlan>>) plan -> EnforcementPlanContext
                    .withReactor(plan,
                            Mono.deferContextual(ctx -> Mono.justOrEmpty(EnforcementPlanContext.currentReactor(ctx))));

            val subscribers = Flux.merge(builder.apply(PLAN_A).subscribeOn(Schedulers.parallel()),
                    builder.apply(PLAN_B).subscribeOn(Schedulers.parallel())).collectList();

            StepVerifier.create(subscribers)
                    .assertNext(seen -> assertThat(seen).containsExactlyInAnyOrder(PLAN_A, PLAN_B)).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Blocking ThreadLocal propagation")
    class BlockingThreadLocal {

        @Test
        @DisplayName("withBlocking binds the plan visible to currentBlocking inside the body")
        void givenWithBlockingThenCurrentBlockingReturnsPlan() {
            val result = EnforcementPlanContext.withBlocking(PLAN_A,
                    () -> EnforcementPlanContext.currentBlocking().orElse(null));

            assertThat(result).isSameAs(PLAN_A);
        }

        @Test
        @DisplayName("currentBlocking returns empty after withBlocking has finished")
        void givenWithBlockingFinishedThenCurrentBlockingEmpty() {
            EnforcementPlanContext.withBlocking(PLAN_A, () -> "ok");
            assertThat(EnforcementPlanContext.currentBlocking()).isEmpty();
        }

        @Test
        @DisplayName("Nested withBlocking restores the outer plan after the inner returns")
        void givenNestedWithBlockingThenOuterPlanRestored() {
            val outer = EnforcementPlanContext.withBlocking(PLAN_A, () -> {
                val inner      = EnforcementPlanContext.withBlocking(PLAN_B,
                        () -> EnforcementPlanContext.currentBlocking().orElse(null));
                val afterInner = EnforcementPlanContext.currentBlocking().orElse(null);
                return new Object[] { inner, afterInner };
            });

            assertThat(outer).containsExactly(PLAN_B, PLAN_A);
        }

        @Test
        @DisplayName("Exception in body still clears the binding")
        void givenExceptionInBodyThenBindingStillCleared() {
            assertThatThrownBy(() -> EnforcementPlanContext.withBlocking(PLAN_A, () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class).hasMessage("boom");

            assertThat(EnforcementPlanContext.currentBlocking()).isEmpty();
        }

        @Test
        @DisplayName("Exception in nested withBlocking still restores the outer plan")
        void givenExceptionInInnerWithBlockingThenOuterPlanRestored() {
            EnforcementPlanContext.withBlocking(PLAN_A, () -> {
                assertThatThrownBy(() -> EnforcementPlanContext.withBlocking(PLAN_B, () -> {
                    throw new RuntimeException("inner boom");
                })).isInstanceOf(RuntimeException.class);

                assertThat(EnforcementPlanContext.currentBlocking()).hasValue(PLAN_A);
                return null;
            });
        }

        @Test
        @DisplayName("bindBlocking(null) explicitly clears the binding")
        void givenBindNullThenBindingCleared() {
            EnforcementPlanContext.bindBlocking(PLAN_A);
            assertThat(EnforcementPlanContext.currentBlocking()).hasValue(PLAN_A);

            EnforcementPlanContext.bindBlocking(null);
            assertThat(EnforcementPlanContext.currentBlocking()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reactor and blocking holders are independent")
    class Independence {

        @Test
        @DisplayName("ThreadLocal binding does not leak into Reactor context")
        void givenThreadLocalBoundWhenCurrentReactorThenEmpty() {
            EnforcementPlanContext.bindBlocking(PLAN_A);
            try {
                val seenInside = new AtomicReference<EnforcementPlan>();
                val body       = Mono.deferContextual(ctx -> {
                                   seenInside.set(EnforcementPlanContext.currentReactor(ctx).orElse(null));
                                   return Mono.just("ok");
                               });

                StepVerifier.create(body).expectNext("ok").verifyComplete();

                assertThat(seenInside.get()).isNull();
            } finally {
                EnforcementPlanContext.bindBlocking(null);
            }
        }

        @Test
        @DisplayName("Reactor binding does not leak into ThreadLocal")
        void givenReactorBoundWhenCurrentBlockingThenEmpty() {
            val seenInside = new AtomicReference<java.util.Optional<EnforcementPlan>>();
            val body       = Mono.fromRunnable(() -> seenInside.set(EnforcementPlanContext.currentBlocking()));

            StepVerifier.create(EnforcementPlanContext.withReactor(PLAN_A, body.then(Mono.just("ok")))).expectNext("ok")
                    .verifyComplete();

            assertThat(seenInside.get()).isEmpty();
        }
    }
}
