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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.Emit;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitError;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.Event.Cancel;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapItem;
import io.sapl.spring.pep.streaming.MealyMachine.State;
import io.sapl.spring.pep.streaming.MealyMachine.State.Pending;
import io.sapl.spring.pep.streaming.MealyMachine.State.Permitting;
import io.sapl.spring.pep.streaming.MealyMachine.State.Suspended;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;
import io.sapl.spring.pep.streaming.MealyMachine.Step;
import io.sapl.spring.pep.streaming.MealyMachine.SuspendKind;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.ItemEnforcementFailed;
import io.sapl.spring.util.Maybe;

/**
 * Unit tests for the {@link MealyMachine} step function. Pure: no
 * Reactor, no Spring, no I/O.
 * <p>
 * Two complementary suites:
 * <ul>
 * <li>{@code RoutingMatrix} — exhaustively asserts the per-cell
 * (state, event) routing as documented in
 * {@code notes/4.1.0-notes/streaming-pep-mealy-diagrams.md}. The
 * routing table here is the executable spec; if it diverges from
 * the implementation, one of the two is wrong.</li>
 * <li>{@code Properties} — sequence-quantified invariants the matrix
 * cannot capture (absorption, universalities, no-leak under suspend,
 * boundary count fidelity).</li>
 * </ul>
 */
class MealyMachineTests {

    private static final EnforcementPlan PLAN = new EnforcementPlan(Map.of());

    private static EnforcementResult<Object> resultPresent(Object v) {
        return new EnforcementResult<>(Maybe.of(v), false);
    }

    private static EnforcementResult<Object> resultAbsent() {
        return new EnforcementResult<>(Maybe.absent(), false);
    }

    private static EnforcementResult<Object> resultFailed() {
        return new EnforcementResult<>(Maybe.absent(), true);
    }

    private static Permitting permitting(boolean terminate) {
        return new Permitting(PLAN, terminate);
    }

    private static PdpPermit pdpPermit(boolean terminate) {
        return new PdpPermit(AuthorizationDecision.PERMIT, PLAN, terminate);
    }

    private static PdpSuspend pdpSuspend(SuspendKind kind) {
        return new PdpSuspend(AuthorizationDecision.NOT_APPLICABLE, PLAN,
                new TransitionReason.Suspended(kind, AuthorizationDecision.NOT_APPLICABLE));
    }

    private static PdpDeny pdpDeny() {
        return new PdpDeny(AuthorizationDecision.DENY, PLAN);
    }

    @Nested
    @DisplayName("Routing matrix: source state x event -> next state + emissions")
    class RoutingMatrix {

        @Nested
        @DisplayName("from Pending")
        class FromPending {

            @Test
            void onPdpPermitTransitionsToPermittingAndEmitsGrantedBoundary() {
                Step step = MealyMachine.step(Pending.INSTANCE, pdpPermit(false));

                assertThat(step.newState()).isInstanceOf(Permitting.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                        e -> assertThat(e.reason()).isInstanceOf(Granted.class));
            }

            @Test
            void onPdpSuspendTransitionsToSuspendedAndEmitsTransition() {
                PdpSuspend ev   = pdpSuspend(SuspendKind.POLICY_SUSPENDED);
                Step       step = MealyMachine.step(Pending.INSTANCE, ev);

                assertThat(step.newState()).isInstanceOf(Suspended.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                        e -> assertThat(e.reason()).isSameAs(ev.reason()));
            }

            @Test
            void onPdpDenyTransitionsToTerminatedAndEmitsAccessDeniedError() {
                Step step = MealyMachine.step(Pending.INSTANCE, pdpDeny());

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isInstanceOf(AccessDeniedException.class));
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyRapItem")
            void onAnyRapItemDropsAndStaysInPending(String name, RapItem item) {
                Step step = MealyMachine.step(Pending.INSTANCE, item);

                assertThat(step.newState()).isInstanceOf(Pending.class);
                assertThat(step.emissions()).isEmpty();
            }

            @Test
            void onRapCompleteTransitionsToTerminatedAndEmitsComplete() {
                Step step = MealyMachine.step(Pending.INSTANCE, RapComplete.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOf(EmitComplete.class);
            }

            @Test
            void onRapErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("rap boom");
                Step      step = MealyMachine.step(Pending.INSTANCE, new RapError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onPdpErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("pdp boom");
                Step      step = MealyMachine.step(Pending.INSTANCE, new PdpError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onCancelTransitionsToTerminatedSilently() {
                Step step = MealyMachine.step(Pending.INSTANCE, Cancel.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).isEmpty();
            }
        }

        @Nested
        @DisplayName("from Permitting")
        class FromPermitting {

            @Test
            void onPdpPermitReplansSilentlyAndStaysInPermitting() {
                Step step = MealyMachine.step(permitting(false), pdpPermit(false));

                assertThat(step.newState()).isInstanceOf(Permitting.class);
                assertThat(step.emissions()).isEmpty();
            }

            // Behavioural validation that the per-item terminate flag carried
            // on PdpPermit is preserved across the transition: a subsequent
            // RapItem(failed) on a state established by PdpPermit(t=true)
            // must terminate (cf. the t=false case below). No introspection
            // of the post-state's record fields.
            @Test
            void permitWithTerminateFlagTrueIsPreservedAcrossSubsequentItemFailure() {
                Step afterPermit = MealyMachine.step(Pending.INSTANCE, pdpPermit(true));
                Step afterItem   = MealyMachine.step(afterPermit.newState(), new RapItem("bad", resultFailed()));

                assertThat(afterItem.newState()).isInstanceOf(Terminated.class);
                assertThat(afterItem.emissions()).singleElement().isInstanceOf(EmitError.class);
            }

            @Test
            void permitWithTerminateFlagFalseIsPreservedAcrossSubsequentItemFailure() {
                Step afterPermit = MealyMachine.step(Pending.INSTANCE, pdpPermit(false));
                Step afterItem   = MealyMachine.step(afterPermit.newState(), new RapItem("bad", resultFailed()));

                assertThat(afterItem.newState()).isInstanceOf(Suspended.class);
                assertThat(afterItem.emissions()).singleElement().isInstanceOf(EmitTransition.class);
            }

            // Re-stamp via replan: a Permitting state established with t=false
            // must, after a fresh PdpPermit(t=true), behave with the new flag.
            @Test
            void replanWithTerminateFlagTrueOverwritesPriorFlag() {
                Step afterFirstPermit = MealyMachine.step(Pending.INSTANCE, pdpPermit(false));
                Step afterReplan      = MealyMachine.step(afterFirstPermit.newState(), pdpPermit(true));
                Step afterItem        = MealyMachine.step(afterReplan.newState(), new RapItem("bad", resultFailed()));

                assertThat(afterItem.newState()).isInstanceOf(Terminated.class);
                assertThat(afterItem.emissions()).singleElement().isInstanceOf(EmitError.class);
            }

            @Test
            void onPdpSuspendCrossesBoundaryToSuspended() {
                PdpSuspend ev   = pdpSuspend(SuspendKind.POLICY_SUSPENDED);
                Step       step = MealyMachine.step(permitting(false), ev);

                assertThat(step.newState()).isInstanceOf(Suspended.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                        e -> assertThat(e.reason()).isSameAs(ev.reason()));
            }

            @Test
            void onPdpDenyTransitionsToTerminatedAndEmitsAccessDeniedError() {
                Step step = MealyMachine.step(permitting(false), pdpDeny());

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOf(EmitError.class);
            }

            @Test
            void onRapItemPresentEmitsValueAndStaysInPermitting() {
                RapItem item = new RapItem("payload-1", resultPresent("post-mapper-1"));
                Step    step = MealyMachine.step(permitting(false), item);

                assertThat(step.newState()).isInstanceOf(Permitting.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(Emit.class,
                        e -> assertThat(e.value()).isEqualTo("post-mapper-1"));
            }

            @Test
            void onRapItemAbsentDropsSilentlyAndStaysInPermitting() {
                RapItem item = new RapItem("payload-2", resultAbsent());
                Step    step = MealyMachine.step(permitting(false), item);

                assertThat(step.newState()).isInstanceOf(Permitting.class);
                assertThat(step.emissions()).isEmpty();
            }

            @Test
            void onRapItemFailedWithTerminateFlagTrueTransitionsToTerminatedAndEmitsError() {
                RapItem item = new RapItem("bad-payload", resultFailed());
                Step    step = MealyMachine.step(permitting(true), item);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isInstanceOf(AccessDeniedException.class));
            }

            @Test
            void onRapItemFailedWithTerminateFlagFalseTransitionsToSuspendedAndEmitsItemFailedTransition() {
                RapItem item = new RapItem("bad-payload", resultFailed());
                Step    step = MealyMachine.step(permitting(false), item);

                assertThat(step.newState()).isInstanceOf(Suspended.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                        e -> assertThat(e.reason()).isInstanceOfSatisfying(ItemEnforcementFailed.class,
                                r -> assertThat(r.payload()).isEqualTo("bad-payload")));
            }

            @Test
            void onRapCompleteTransitionsToTerminatedAndEmitsComplete() {
                Step step = MealyMachine.step(permitting(false), RapComplete.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOf(EmitComplete.class);
            }

            @Test
            void onRapErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("rap boom");
                Step      step = MealyMachine.step(permitting(false), new RapError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onPdpErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("pdp boom");
                Step      step = MealyMachine.step(permitting(false), new PdpError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onCancelTransitionsToTerminatedSilently() {
                Step step = MealyMachine.step(permitting(false), Cancel.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).isEmpty();
            }
        }

        @Nested
        @DisplayName("from Suspended")
        class FromSuspended {

            @Test
            void onPdpPermitResumesToPermittingAndEmitsGrantedBoundary() {
                Step step = MealyMachine.step(Suspended.INSTANCE, pdpPermit(false));

                assertThat(step.newState()).isInstanceOf(Permitting.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitTransition.class,
                        e -> assertThat(e.reason()).isInstanceOf(Granted.class));
            }

            @Test
            void onPdpSuspendReSuspendsSilently() {
                Step step = MealyMachine.step(Suspended.INSTANCE, pdpSuspend(SuspendKind.EVALUATION_ERROR));

                assertThat(step.newState()).isInstanceOf(Suspended.class);
                assertThat(step.emissions()).isEmpty();
            }

            @Test
            void onPdpDenyTransitionsToTerminatedAndEmitsAccessDeniedError() {
                Step step = MealyMachine.step(Suspended.INSTANCE, pdpDeny());

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOf(EmitError.class);
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyRapItem")
            void onAnyRapItemDropsAndStaysInSuspended(String name, RapItem item) {
                Step step = MealyMachine.step(Suspended.INSTANCE, item);

                assertThat(step.newState()).isInstanceOf(Suspended.class);
                assertThat(step.emissions()).isEmpty();
            }

            @Test
            void onRapCompleteTransitionsToTerminatedAndEmitsComplete() {
                Step step = MealyMachine.step(Suspended.INSTANCE, RapComplete.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOf(EmitComplete.class);
            }

            @Test
            void onRapErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("rap boom");
                Step      step = MealyMachine.step(Suspended.INSTANCE, new RapError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onPdpErrorTransitionsToTerminatedAndForwardsThrowable() {
                Throwable t    = new RuntimeException("pdp boom");
                Step      step = MealyMachine.step(Suspended.INSTANCE, new PdpError(t));

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).singleElement().isInstanceOfSatisfying(EmitError.class,
                        e -> assertThat(e.throwable()).isSameAs(t));
            }

            @Test
            void onCancelTransitionsToTerminatedSilently() {
                Step step = MealyMachine.step(Suspended.INSTANCE, Cancel.INSTANCE);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).isEmpty();
            }
        }

        @Nested
        @DisplayName("from Terminated (absorbing)")
        class FromTerminated {

            @ParameterizedTest(name = "{0}")
            @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyEvent")
            void anyEventLeavesTerminatedUnchangedAndEmitsNothing(String name, Event event) {
                Step step = MealyMachine.step(Terminated.INSTANCE, event);

                assertThat(step.newState()).isInstanceOf(Terminated.class);
                assertThat(step.emissions()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Properties: invariants over states and events")
    class Properties {

        @ParameterizedTest(name = "from {0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyNonTerminatedState")
        void pdpDenyAlwaysReachesTerminatedFromAnyNonTerminatedState(String name, State source) {
            Step step = MealyMachine.step(source, pdpDeny());

            assertThat(step.newState()).isInstanceOf(Terminated.class);
            assertThat(step.emissions()).singleElement().isInstanceOf(EmitError.class);
        }

        @ParameterizedTest(name = "from {0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyNonTerminatedState")
        void pdpPermitAlwaysReachesPermittingFromAnyNonTerminatedState(String name, State source) {
            Step step = MealyMachine.step(source, pdpPermit(false));

            assertThat(step.newState()).isInstanceOf(Permitting.class);
        }

        @ParameterizedTest(name = "from {0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyNonTerminatedState")
        void pdpSuspendAlwaysReachesSuspendedFromAnyNonTerminatedState(String name, State source) {
            Step step = MealyMachine.step(source, pdpSuspend(SuspendKind.POLICY_SUSPENDED));

            assertThat(step.newState()).isInstanceOf(Suspended.class);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyLifecycleTerminator")
        void lifecycleTerminatorsAlwaysReachTerminatedFromAnyNonTerminatedState(String name, Event event) {
            for (State source : List.of(Pending.INSTANCE, permitting(false), Suspended.INSTANCE)) {
                Step step = MealyMachine.step(source, event);
                assertThat(step.newState()).as("source=%s event=%s", source, name).isInstanceOf(Terminated.class);
            }
        }

        @ParameterizedTest(name = "in Pending, item={0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyRapItem")
        void noLeakInPendingForAnyRapItem(String name, RapItem item) {
            Step step = MealyMachine.step(Pending.INSTANCE, item);

            assertThat(step.emissions()).noneMatch(Emit.class::isInstance);
        }

        @ParameterizedTest(name = "in Suspended, item={0}")
        @MethodSource("io.sapl.spring.pep.streaming.MealyMachineTests#anyRapItem")
        void noLeakInSuspendedForAnyRapItem(String name, RapItem item) {
            Step step = MealyMachine.step(Suspended.INSTANCE, item);

            assertThat(step.emissions()).noneMatch(Emit.class::isInstance);
        }

        @Test
        void boundaryEmissionCountEqualsTrueCrossingCount() {
            // Pending -> Permitting (crossing, +1) -> Permitting (replan, silent)
            // -> Suspended (crossing, +1) -> Suspended (re-suspend, silent)
            // -> Permitting (crossing, +1) -> Terminated (DENY, EmitError, not
            // EmitTransition)
            List<Event> sequence = List.of(pdpPermit(false), pdpPermit(false), pdpSuspend(SuspendKind.POLICY_SUSPENDED),
                    pdpSuspend(SuspendKind.EVALUATION_ERROR), pdpPermit(false), pdpDeny());

            int   crossings         = 0;
            int   boundaryEmissions = 0;
            State state             = Pending.INSTANCE;
            State prev              = state;
            for (Event ev : sequence) {
                Step step = MealyMachine.step(state, ev);
                state = step.newState();
                if (isBoundaryCrossing(prev, state)) {
                    crossings++;
                }
                boundaryEmissions += (int) step.emissions().stream().filter(EmitTransition.class::isInstance).count();
                prev               = state;
            }

            assertThat(boundaryEmissions).as("EmitTransition count equals true crossing count").isEqualTo(crossings);
        }

        @Test
        void terminalEmissionSingularityHoldsForEachTerminator() {
            for (Event terminator : List.of(Cancel.INSTANCE, RapComplete.INSTANCE, new RapError(new RuntimeException()),
                    new PdpError(new RuntimeException()), pdpDeny())) {
                Step step              = MealyMachine.step(Pending.INSTANCE, terminator);
                long terminalEmissions = step.emissions().stream()
                        .filter(e -> e instanceof EmitComplete || e instanceof EmitError).count();
                assertThat(terminalEmissions).as("event=%s", terminator).isLessThanOrEqualTo(1);
            }
        }

        @Test
        void perItemFlagOnlyConsultedInPermittingState() {
            // From non-Permitting source states, the per-item flag carried on
            // PdpPermit only affects future Permitting -> *. RapItem(failed)
            // arriving in Pending/Suspended must drop silently regardless.
            RapItem failed = new RapItem("p", resultFailed());

            assertThat(MealyMachine.step(Pending.INSTANCE, failed).emissions()).isEmpty();
            assertThat(MealyMachine.step(Suspended.INSTANCE, failed).emissions()).isEmpty();
        }
    }

    static Stream<Arguments> anyRapItem() {
        return Stream.of(arguments("ok-present", new RapItem("payload", resultPresent("v"))),
                arguments("ok-absent", new RapItem("payload", resultAbsent())),
                arguments("failed", new RapItem("payload", resultFailed())));
    }

    static Stream<Arguments> anyEvent() {
        return Stream.of(arguments("PdpPermit", new PdpPermit(AuthorizationDecision.PERMIT, PLAN, false)),
                arguments("PdpSuspend",
                        new PdpSuspend(AuthorizationDecision.NOT_APPLICABLE, PLAN,
                                new TransitionReason.Suspended(SuspendKind.POLICY_SUSPENDED,
                                        AuthorizationDecision.NOT_APPLICABLE))),
                arguments("PdpDeny", new PdpDeny(AuthorizationDecision.DENY, PLAN)),
                arguments("PdpError", new PdpError(new RuntimeException("e"))),
                arguments("RapItem-ok", new RapItem("p", resultPresent("v"))),
                arguments("RapError", new RapError(new RuntimeException("e"))),
                arguments("RapComplete", RapComplete.INSTANCE), arguments("Cancel", Cancel.INSTANCE));
    }

    static Stream<Arguments> anyNonTerminatedState() {
        return Stream.of(arguments("Pending", Pending.INSTANCE),
                arguments("Permitting(terminate=false)", new Permitting(PLAN, false)),
                arguments("Permitting(terminate=true)", new Permitting(PLAN, true)),
                arguments("Suspended", Suspended.INSTANCE));
    }

    static Stream<Arguments> anyLifecycleTerminator() {
        return Stream.of(arguments("Cancel", Cancel.INSTANCE), arguments("RapComplete", RapComplete.INSTANCE),
                arguments("RapError", new RapError(new RuntimeException())),
                arguments("PdpError", new PdpError(new RuntimeException())));
    }

    private static boolean isBoundaryCrossing(State from, State to) {
        if (from instanceof Pending && to instanceof Permitting)
            return true;
        if (from instanceof Pending && to instanceof Suspended)
            return true;
        if (from instanceof Permitting && to instanceof Suspended)
            return true;
        if (from instanceof Suspended && to instanceof Permitting)
            return true;
        return false;
    }

}
