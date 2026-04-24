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

import static io.sapl.spring.pep.constraints.EnforcementResultAssert.assertThatResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.Signal.CancelSignal;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import lombok.val;
import reactor.core.Exceptions;

class EnforcementPlanTests {

    private static final Value DUMMY_CONSTRAINT = Value.of("constraint");

    private static EnforcementPlan plan(Signal signal, EnforcementPlanEntry<?>... entries) {
        return new EnforcementPlan(Map.of(signal.type(), List.of(entries)));
    }

    private static EnforcementPlanEntry<?> obligation(ConstraintHandler<?> handler, int priority) {
        return entry(handler, priority, ConstraintType.OBLIGATION);
    }

    private static EnforcementPlanEntry<?> advice(ConstraintHandler<?> handler, int priority) {
        return entry(handler, priority, ConstraintType.ADVICE);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static EnforcementPlanEntry<?> entry(ConstraintHandler<?> handler, int priority, ConstraintType tag) {
        return new EnforcementPlanEntry(handler, priority, tag, DUMMY_CONSTRAINT);
    }

    private static ConstraintHandler.Runner runner(Runnable body) {
        return body::run;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> ConstraintHandler.Consumer<T> consumer(Consumer<T> body) {
        return (ConstraintHandler.Consumer) (ConstraintHandler.Consumer<T>) body::accept;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> ConstraintHandler.Mapper<T> mapper(UnaryOperator<T> body) {
        return (ConstraintHandler.Mapper) (ConstraintHandler.Mapper<T>) body::apply;
    }

    private static Signal.OutputSignal<String> outputString(String value) {
        return OutputSignal.of(String.class, value);
    }

    private static Signal.DecisionSignal decisionSignal() {
        return DecisionSignal.of(AuthorizationDecision.PERMIT);
    }

    private static Signal.CancelSignal cancelSignal() {
        return CancelSignal.INSTANCE;
    }

    @Nested
    @DisplayName("Per-handler-kind dispatch")
    class HandlerDispatch {

        @Test
        @DisplayName("Runner is invoked exactly once with no value")
        void givenRunnerWhenExecutedThenInvokedOnce() {
            val invocations = new AtomicInteger();
            val plan        = plan(outputString("v"), obligation(runner(invocations::incrementAndGet), 0));

            val result = plan.execute(outputString("v"), false);

            assertThat(invocations).hasValue(1);
            assertThatResult(result).hasPresentValue("v").hasFailureState(false);
        }

        @Test
        @DisplayName("Consumer receives the present value and does not transform it")
        void givenConsumerWhenExecutedThenReceivesValue() {
            val received = new ArrayList<String>();
            val plan     = plan(outputString("hello"), obligation(consumer((String value) -> received.add(value)), 0));

            val result = plan.execute(outputString("hello"), false);

            assertThat(received).containsExactly("hello");
            assertThatResult(result).hasPresentValue("hello").hasFailureState(false);
        }

        @Test
        @DisplayName("Mapper transforms the present value")
        void givenMapperWhenExecutedThenValueTransformed() {
            val plan = plan(outputString("hello"), obligation(mapper((String value) -> value.toUpperCase()), 0));

            val result = plan.execute(outputString("hello"), false);

            assertThatResult(result).hasPresentValue("HELLO").hasFailureState(false);
        }

        @Test
        @DisplayName("Consumer at a void signal is silently skipped (defensive: planner prevents this)")
        void givenConsumerAtVoidSignalThenNotInvoked() {
            val received = new ArrayList<Object>();
            val plan     = plan(cancelSignal(), obligation(consumer((Object value) -> received.add(value)), 0));

            val result = plan.execute(cancelSignal(), false);

            assertThat(received).isEmpty();
            assertThatResult(result).hasAbsentValue().hasFailureState(false);
        }
    }

    @Nested
    @DisplayName("Sequence order and pipelining")
    class PlanOrderAndChaining {

        @Test
        @DisplayName("handlers fire in plan order")
        void givenMultipleHandlersThenInvokedInOrder() {
            val log  = new ArrayList<String>();
            val plan = plan(outputString("v"), obligation(runner(() -> log.add("a")), 0),
                    obligation(runner(() -> log.add("b")), 0), obligation(runner(() -> log.add("c")), 0));

            plan.execute(outputString("v"), false);

            assertThat(log).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("mappers chain: each sees the previous mapper's output")
        void givenMapperChainThenOutputIsPipelined() {
            val plan = plan(outputString("a"), obligation(mapper((String value) -> value + "b"), 0),
                    obligation(mapper((String value) -> value + "c"), 1));

            val result = plan.execute(outputString("a"), false);

            assertThatResult(result).hasPresentValue("abc").hasFailureState(false);
        }
    }

    @Nested
    @DisplayName("Best-effort discharge")
    class BestEffortDischarge {

        @Test
        @DisplayName("a failing obligation does not abort subsequent handlers")
        void givenFailingObligationThenSubsequentHandlersStillRun() {
            val ranAfter = new AtomicInteger();
            val plan     = plan(outputString("v"), obligation(runner(() -> {
                             throw new RuntimeException("boom");
                         }), 0), obligation(runner(ranAfter::incrementAndGet), 1));

            val result = plan.execute(outputString("v"), false);

            assertThat(ranAfter).hasValue(1);
            assertThatResult(result).hasPresentValue("v").hasFailureState(true);
        }

        @Test
        @DisplayName("a failing advice does not abort subsequent handlers")
        void givenFailingAdviceThenSubsequentHandlersStillRun() {
            val ranAfter = new AtomicInteger();
            val plan     = plan(outputString("v"), advice(runner(() -> {
                             throw new RuntimeException("boom");
                         }), 0), obligation(runner(ranAfter::incrementAndGet), 1));

            val result = plan.execute(outputString("v"), false);

            assertThat(ranAfter).hasValue(1);
            assertThatResult(result).hasFailureState(false);
        }

        @Test
        @DisplayName("a failing mapper still allows downstream handlers to see the pre-failure value")
        void givenFailingMapperThenDownstreamSeesPreFailureValue() {
            val seen = new ArrayList<String>();
            val plan = plan(outputString("hello"), obligation(mapper((String value) -> {
                         throw new RuntimeException("boom");
                     }), 0), obligation(consumer((String value) -> seen.add(value)), 1));

            val result = plan.execute(outputString("hello"), false);

            assertThat(seen).containsExactly("hello");
            assertThatResult(result).hasPresentValue("hello").hasFailureState(true);
        }
    }

    @Nested
    @DisplayName("Failure-state propagation")
    class FailureStatePropagation {

        @Test
        @DisplayName("obligation failure flips failureState to true")
        void givenObligationFailureThenFailureStateTrue() {
            val plan = plan(outputString("v"), obligation(runner(() -> {
                throw new RuntimeException("boom");
            }), 0));

            val result = plan.execute(outputString("v"), false);

            assertThatResult(result).hasFailureState(true);
        }

        @Test
        @DisplayName("advice failure does not change failureState")
        void givenAdviceFailureThenFailureStateUnchanged() {
            val plan = plan(outputString("v"), advice(runner(() -> {
                throw new RuntimeException("boom");
            }), 0));

            val result = plan.execute(outputString("v"), false);

            assertThatResult(result).hasFailureState(false);
        }

        @Test
        @DisplayName("priorFailureState=true is preserved through a clean run")
        void givenPriorFailureTrueAndNoFailuresThenStaysTrue() {
            val plan = plan(outputString("v"), obligation(runner(() -> {}), 0));

            val result = plan.execute(outputString("v"), true);

            assertThatResult(result).hasFailureState(true);
        }

        @Test
        @DisplayName("failureState is monotonic: never flips back from true to false")
        void givenPriorFailureTrueAndAdviceFailureThenStillTrue() {
            val plan = plan(outputString("v"), advice(runner(() -> {
                throw new RuntimeException("boom");
            }), 0));

            val result = plan.execute(outputString("v"), true);

            assertThatResult(result).hasFailureState(true);
        }
    }

    @Nested
    @DisplayName("Initial value extraction by signal kind")
    class SignalValueExtraction {

        @Test
        @DisplayName("ValueSignal yields Present(value)")
        void givenValueSignalThenInitialValuePresent() {
            val plan = new EnforcementPlan(Map.of());

            val result = plan.execute(outputString("hello"), false);

            assertThatResult(result).hasPresentValue("hello").hasFailureState(false);
        }

        @Test
        @DisplayName("VoidSignal yields Absent")
        void givenVoidSignalThenInitialValueAbsent() {
            val plan = new EnforcementPlan(Map.of());

            val result = plan.execute(cancelSignal(), false);

            assertThatResult(result).hasAbsentValue().hasFailureState(false);
        }

        @Test
        @DisplayName("ValueSignal carrying a null payload still yields Present(null)")
        void givenValueSignalWithNullPayloadThenPresentNull() {
            val plan = new EnforcementPlan(Map.of());

            val result = plan.execute(outputString(null), false);

            assertThatResult(result).hasPresentValue(null).hasFailureState(false);
        }
    }

    @Nested
    @DisplayName("Selective signal execution")
    class SelectiveSignalExecution {

        @Test
        @DisplayName("handlers attached to a signal that does not fire are not invoked")
        void givenHandlerForOtherSignalThenNotInvoked() {
            val cancelInvocations = new AtomicInteger();
            val plan              = new EnforcementPlan(
                    Map.of(cancelSignal().type(), List.of(obligation(runner(cancelInvocations::incrementAndGet), 0))));

            val result = plan.execute(outputString("v"), false);

            assertThat(cancelInvocations).hasValue(0);
            assertThatResult(result).hasPresentValue("v").hasFailureState(false);
        }

        @Test
        @DisplayName("empty plan for the fired signal returns the input value unchanged")
        void givenEmptyPlanThenInputUnchanged() {
            val plan = new EnforcementPlan(Map.of());

            val result = plan.execute(outputString("untouched"), false);

            assertThatResult(result).hasPresentValue("untouched").hasFailureState(false);
        }
    }

    @Nested
    @DisplayName("Self-contained signal (decision)")
    class SelfContainedSignal {

        @Test
        @DisplayName("runner at the decision signal executes; value remains absent")
        void givenRunnerAtDecisionSignalThenValueAbsent() {
            val invocations = new AtomicInteger();
            val plan        = plan(decisionSignal(), obligation(runner(invocations::incrementAndGet), 0));

            val result = plan.execute(decisionSignal(), false);

            assertThat(invocations).hasValue(1);
            assertThatResult(result).hasPresentValue(AuthorizationDecision.PERMIT).hasFailureState(false);
        }
    }

    @Nested
    @DisplayName("Fatal exception propagation")
    class FatalExceptionPropagation {

        @Test
        @DisplayName("VirtualMachineError propagates instead of being caught")
        void givenJvmFatalErrorThenPropagates() {
            val plan = plan(outputString("v"), obligation(runner(() -> {
                throw new InternalError("simulated VM error");
            }), 0));

            assertThatThrownBy(() -> plan.execute(outputString("v"), false)).isInstanceOf(InternalError.class)
                    .hasMessageContaining("simulated VM error");
        }

        @Test
        @DisplayName("Reactor BubblingException propagates via throwIfFatal")
        void givenReactorBubblingExceptionThenPropagates() {
            val plan = plan(outputString("v"), obligation(runner(() -> {
                throw Exceptions.bubble(new RuntimeException("inner"));
            }), 0));

            assertThatThrownBy(() -> plan.execute(outputString("v"), false)).matches(Exceptions::isBubbling,
                    "is a Reactor bubbling exception");
        }
    }
}
