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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.spring.pep.streaming.MealyMachine.Emission.Emit;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitError;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.State;
import io.sapl.spring.pep.streaming.MealyMachine.State.Pending;
import io.sapl.spring.pep.streaming.MealyMachine.State.Permitting;
import io.sapl.spring.pep.streaming.MealyMachine.State.Suspended;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.Granted;

/**
 * Layer-2 invariants on {@link MealyMachine#step(State, Event)}.
 * <p>
 * Each test is the executable witness of a theorem proved on the
 * formal model in
 * {@code stream-pep-lean/StreamPepFsm/Properties.lean}. Test method
 * names mirror the Lean theorem names (snake_case → camelCase). The
 * Javadoc carries the Lean statement; the test body discharges it by
 * computation, by enumeration over a finite quantification domain, or
 * by replaying a fixed event sequence — whichever shape Lean uses.
 * <p>
 * The Lean module groups its theorems by section (per-cell invariants
 * first, sequence invariants last); the test methods follow the same
 * order.
 */
class MealyMachineInvariantTests {

    /**
     * Lean theorem: {@code terminated_is_absorbing}
     *
     * <pre>
     * ∀ (e : Event), step .Terminated e = ⟨.Terminated, []⟩
     * </pre>
     */
    @ParameterizedTest(name = "event = {0}")
    @MethodSource("allEvents")
    void terminatedIsAbsorbing(String name, Event event) {
        var step = MealyMachine.step(Terminated.INSTANCE, event);

        assertThat(step.newState()).isInstanceOf(Terminated.class);
        assertThat(step.emissions()).isEmpty();
    }

    /**
     * Lean theorem: {@code deny_universally_terminates}
     *
     * <pre>
     * ∀ (s : State), s ≠ .Terminated →
     *   step s .PdpDeny = ⟨.Terminated, [.EmitError]⟩
     * </pre>
     */
    @ParameterizedTest(name = "from {0}")
    @MethodSource("nonTerminatedStates")
    void denyUniversallyTerminates(String name, State source) {
        var step = MealyMachine.step(source, MealyTestSupport.pdpDeny());

        assertThat(step.newState()).isInstanceOf(Terminated.class);
        assertThat(step.emissions()).singleElement().isInstanceOf(EmitError.class);
    }

    /**
     * Lean theorem: {@code permit_universally_reaches_permitting}
     *
     * <pre>
     * ∀ (s : State), s ≠ .Terminated →
     *   (step s .PdpPermit).newState = .Permitting
     * </pre>
     */
    @ParameterizedTest(name = "from {0}")
    @MethodSource("nonTerminatedStates")
    void permitUniversallyReachesPermitting(String name, State source) {
        var step = MealyMachine.step(source, MealyTestSupport.pdpPermit());

        assertThat(step.newState()).isInstanceOf(Permitting.class);
    }

    /**
     * Lean theorem: {@code suspend_universally_reaches_suspended}
     *
     * <pre>
     * ∀ (s : State), s ≠ .Terminated →
     *   (step s .PdpSuspend).newState = .Suspended
     * </pre>
     */
    @ParameterizedTest(name = "from {0}")
    @MethodSource("nonTerminatedStates")
    void suspendUniversallyReachesSuspended(String name, State source) {
        var step = MealyMachine.step(source, MealyTestSupport.pdpSuspend());

        assertThat(step.newState()).isInstanceOf(Suspended.class);
    }

    /**
     * Lean theorem: {@code lifecycle_terminators_reach_terminated}
     *
     * <pre>
     * ∀ (s : State) (e : Event),
     *   s ≠ .Terminated →
     *   e = .Cancel ∨ e = .RapComplete ∨ e = .RapError ∨ e = .PdpError →
     *   (step s e).newState = .Terminated
     * </pre>
     */
    @ParameterizedTest(name = "from {0}, event = {1}")
    @MethodSource("nonTerminatedStateAndLifecycleTerminator")
    void lifecycleTerminatorsReachTerminated(String sourceName, String eventName, State source, Event event) {
        var step = MealyMachine.step(source, event);

        assertThat(step.newState()).isInstanceOf(Terminated.class);
    }

    /**
     * Lean theorem: {@code no_emit_in_suspended}
     *
     * <pre>
     * ∀ (o : ItemOutcome),
     *   .Emit ∉ (step .Suspended (.RapItem o)).emissions
     * </pre>
     */
    @ParameterizedTest(name = "outcome = {0}")
    @MethodSource("itemOutcomes")
    void noEmitInSuspended(String outcome, Event item) {
        var step = MealyMachine.step(Suspended.INSTANCE, item);

        assertThat(step.emissions()).noneMatch(Emit.class::isInstance);
    }

    /**
     * Lean theorem: {@code no_emit_in_pending}
     *
     * <pre>
     * ∀ (o : ItemOutcome),
     *   .Emit ∉ (step .Pending (.RapItem o)).emissions
     * </pre>
     */
    @ParameterizedTest(name = "outcome = {0}")
    @MethodSource("itemOutcomes")
    void noEmitInPending(String outcome, Event item) {
        var step = MealyMachine.step(Pending.INSTANCE, item);

        assertThat(step.emissions()).noneMatch(Emit.class::isInstance);
    }

    /**
     * Lean theorem: {@code item_failure_terminates}
     *
     * <pre>
     * step .Permitting (.RapItem .Failed) = ⟨.Terminated, [.EmitError]⟩
     * </pre>
     */
    @Test
    void itemFailureTerminates() {
        var step = MealyMachine.step(MealyTestSupport.permitting(), MealyTestSupport.rapItemFailed());

        assertThat(step.newState()).isInstanceOf(Terminated.class);
        assertThat(step.emissions()).singleElement().isInstanceOf(EmitError.class);
    }

    /**
     * Lean theorem: {@code replan_is_silent}
     *
     * <pre>
     * (step .Permitting .PdpPermit).emissions = []
     * </pre>
     */
    @Test
    void replanIsSilent() {
        var step = MealyMachine.step(MealyTestSupport.permitting(), MealyTestSupport.pdpPermit());

        assertThat(step.emissions()).isEmpty();
    }

    /**
     * Lean theorem: {@code re_suspend_is_silent}
     *
     * <pre>
     * (step .Suspended .PdpSuspend).emissions = []
     * </pre>
     */
    @Test
    void reSuspendIsSilent() {
        var step = MealyMachine.step(Suspended.INSTANCE, MealyTestSupport.pdpSuspend());

        assertThat(step.emissions()).isEmpty();
    }

    /**
     * Lean theorem: {@code initial_permit_emits_boundary}
     *
     * <pre>
     * (step .Pending .PdpPermit).emissions = [.EmitTransition]
     * </pre>
     */
    @Test
    void initialPermitEmitsBoundary() {
        var step = MealyMachine.step(Pending.INSTANCE, MealyTestSupport.pdpPermit());

        assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                emission -> assertThat(emission.reason()).isInstanceOf(Granted.class));
    }

    /**
     * Lean theorem: {@code resume_permit_emits_boundary}
     *
     * <pre>
     * (step .Suspended .PdpPermit).emissions = [.EmitTransition]
     * </pre>
     */
    @Test
    void resumePermitEmitsBoundary() {
        var step = MealyMachine.step(Suspended.INSTANCE, MealyTestSupport.pdpPermit());

        assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                emission -> assertThat(emission.reason()).isInstanceOf(Granted.class));
    }

    /**
     * Lean theorem: {@code pending_to_suspended_emits_boundary}
     *
     * <pre>
     * (step .Pending .PdpSuspend).emissions = [.EmitTransition]
     * </pre>
     */
    @Test
    void pendingToSuspendedEmitsBoundary() {
        var step = MealyMachine.step(Pending.INSTANCE, MealyTestSupport.pdpSuspend());

        assertThat(step.emissions()).singleElement().isInstanceOf(EmitTransition.class);
    }

    /**
     * Lean theorem: {@code permitting_to_suspended_emits_boundary}
     *
     * <pre>
     * (step .Permitting .PdpSuspend).emissions = [.EmitTransition]
     * </pre>
     */
    @Test
    void permittingToSuspendedEmitsBoundary() {
        var step = MealyMachine.step(MealyTestSupport.permitting(), MealyTestSupport.pdpSuspend());

        assertThat(step.emissions()).singleElement().isInstanceOf(EmitTransition.class);
    }

    /**
     * Lean theorem: {@code permit_then_failed_item_terminates}
     *
     * <pre>
     * (replay .Pending [.PdpPermit, .RapItem .Failed]).fst = .Terminated
     * </pre>
     */
    @Test
    void permitThenFailedItemTerminates() {
        var afterPermit = MealyMachine.step(Pending.INSTANCE, MealyTestSupport.pdpPermit());
        var afterItem   = MealyMachine.step(afterPermit.newState(), MealyTestSupport.rapItemFailed());

        assertThat(afterItem.newState()).isInstanceOf(Terminated.class);
    }

    static Stream<Arguments> allEvents() {
        return MealyTestSupport.allEvents();
    }

    static Stream<Arguments> nonTerminatedStates() {
        return MealyTestSupport.nonTerminatedStates();
    }

    static Stream<Arguments> nonTerminatedStateAndLifecycleTerminator() {
        return MealyTestSupport.nonTerminatedStateAndLifecycleTerminator();
    }

    static Stream<Arguments> itemOutcomes() {
        return MealyTestSupport.itemOutcomes();
    }
}
