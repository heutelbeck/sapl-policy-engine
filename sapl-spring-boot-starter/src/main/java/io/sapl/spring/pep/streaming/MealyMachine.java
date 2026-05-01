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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.Emit;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitError;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.MealyMachine.Event.Cancel;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapItem;
import io.sapl.spring.pep.streaming.MealyMachine.State.Pending;
import io.sapl.spring.pep.streaming.MealyMachine.State.Permitting;
import io.sapl.spring.pep.streaming.MealyMachine.State.Suspended;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.DecisionDenied;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.ItemEnforcementFailed;
import io.sapl.spring.util.Maybe;
import lombok.experimental.UtilityClass;

/**
 * The streaming PEP's Mealy machine. Defines the alphabets and the
 * combined transition + output function {@code S × Σ → S × Λ}, which
 * is the defining property of a Mealy machine (output depends on
 * {@code (state, event)}, as opposed to Moore where output depends
 * only on state). Four sealed types form one
 * {@code (state, event) -> (state, emissions)} step relation:
 * <ul>
 * <li>{@link State} — what the subscription is currently doing.</li>
 * <li>{@link Event} — the input alphabet (PDP, RAP, subscriber).</li>
 * <li>{@link Emission} — the output alphabet (what the adapter does
 * downstream).</li>
 * <li>{@link TransitionReason} — discriminator for boundary signals
 * surfaced via {@link Emission.EmitTransition}.</li>
 * </ul>
 * Plus the {@link Step} record packing a step's results, and the pure
 * {@link #step(State, Event)} function that advances the machine.
 * <p>
 * Routing dispatches on the PDP decision verb (carried by the event)
 * and the current state. Explicit {@link PdpDeny} always terminates;
 * {@link PdpSuspend} always transitions to {@link Suspended}. The
 * {@code terminateOnItemEnforcementFailure} flag is consulted only on
 * the per-item failure path (it lives on the {@link Permitting} state,
 * stamped from the triggering {@link PdpPermit}).
 * <p>
 * Boundary signals fire symmetrically on every entry into
 * {@link Permitting} from a non-Permitting state (initial grant or
 * resume) and on every entry into {@link Suspended} from a non-Suspended
 * state. Plan replacement (Permitting&nbsp;-&gt;&nbsp;Permitting) and
 * re-suspension (Suspended&nbsp;-&gt;&nbsp;Suspended) are silent.
 *
 * @since 4.1.0
 */
@UtilityClass
public class MealyMachine {

    /**
     * The state set of the streaming PEP's Mealy machine. Sealed into
     * four cases that together describe the entire lifecycle of one
     * subscription. Routing is driven by the PDP decision verb, not
     * by an annotation flag.
     */
    public sealed interface State {

        /**
         * No PDP decision has arrived yet. The pipeline is subscribed to
         * the PDP. Singleton.
         */
        record Pending() implements State {

            public static final Pending INSTANCE = new Pending();
        }

        /**
         * The current decision permits and the {@link EnforcementPlan} is
         * usable. Per-item enforcement runs against {@code plan};
         * lifecycle signals fire against it as long as it remains
         * current.
         *
         * @param plan the active plan for this decision
         * @param terminateOnItemEnforcementFailure whether a subsequent
         * per-item enforcement failure terminates the subscription
         * (true) or transitions to {@link Suspended} (false). Stamped
         * from the triggering {@link Event.PdpPermit} so a future
         * mid-stream change takes effect on the next permit.
         */
        record Permitting(EnforcementPlan plan, boolean terminateOnItemEnforcementFailure) implements State {}

        /**
         * The PDP returned a non-DENY non-PERMIT decision (SUSPEND,
         * INDETERMINATE, NOT_APPLICABLE) or PERMIT with failed
         * decision-scoped enforcement, or a per-item handler failed
         * with {@code terminateOnItemEnforcementFailure = false}.
         * Subscription is preserved; items arriving from the RAP are
         * dropped silently. The next PERMIT decision transitions back
         * to {@link Permitting}; an explicit DENY transitions to
         * {@link Terminated}. Singleton; the suspending event's reason
         * flows out via {@link Emission.EmitTransition} rather than
         * being stored on the state.
         */
        record Suspended() implements State {

            public static final Suspended INSTANCE = new Suspended();
        }

        /**
         * Absorbing state. Reached on RAP completion, RAP error,
         * downstream cancellation, PDP error, or an explicit DENY
         * decision (or per-item enforcement failure when the PEP is
         * configured with {@code terminateOnItemEnforcementFailure =
         * true}). No further events are processed. Singleton; the
         * terminating event's outcome flows out via the corresponding
         * {@link Emission} (EmitComplete / EmitError) rather than
         * being stored on the state.
         */
        record Terminated() implements State {

            public static final Terminated INSTANCE = new Terminated();
        }
    }

    /**
     * The input alphabet of the streaming PEP's Mealy machine. Sealed
     * into eight cases covering three input domains: PDP-side decision
     * events, RAP-side stream events, and downstream subscriber-side
     * lifecycle events.
     * <p>
     * The pipeline pre-classifies PDP decisions: it receives a raw
     * {@link AuthorizationDecision}, builds the {@link EnforcementPlan}
     * (always succeeds; substitutes are baked into the plan), runs the
     * plan's decision-scoped handlers, and emits the appropriate event
     * variant by decision verb.
     */
    public sealed interface Event {

        /**
         * The PDP returned PERMIT and decision-scoped enforcement
         * succeeded. The state machine transitions to
         * {@link State.Permitting} carrying the plan and the
         * {@code terminateOnItemEnforcementFailure} flag.
         */
        record PdpPermit(
                AuthorizationDecision decision,
                EnforcementPlan plan,
                boolean terminateOnItemEnforcementFailure) implements Event {}

        /**
         * The PDP returned a non-DENY non-PERMIT decision (SUSPEND,
         * INDETERMINATE, NOT_APPLICABLE), or PERMIT with failed
         * decision-scoped enforcement. The state machine transitions
         * to {@link State.Suspended} carrying the plan and the
         * discriminating {@link TransitionReason}.
         */
        record PdpSuspend(AuthorizationDecision decision, EnforcementPlan plan, TransitionReason reason)
                implements Event {}

        /**
         * The PDP returned an explicit DENY. The state machine
         * transitions to {@link State.Terminated} and emits a terminal
         * {@link Emission.EmitError}.
         */
        record PdpDeny(AuthorizationDecision decision, EnforcementPlan plan) implements Event {}

        /**
         * The PDP's decision flux raised. Treated as terminal: emit
         * the throwable downstream and transition to
         * {@link State.Terminated}.
         */
        record PdpError(Throwable throwable) implements Event {}

        /**
         * The protected method emitted an item. Per-item enforcement
         * has already been attempted by the adapter; the
         * {@code enforcementResult} carries both the post-mapper value
         * and the failure flag.
         */
        record RapItem(Object payload, EnforcementResult<Object> enforcementResult) implements Event {}

        /**
         * The protected method (or the wrapping pipeline) raised.
         * Terminal.
         */
        record RapError(Throwable throwable) implements Event {}

        /**
         * The protected method completed normally. Terminal.
         */
        record RapComplete() implements Event {

            public static final RapComplete INSTANCE = new RapComplete();
        }

        /**
         * The downstream subscriber canceled. Terminal.
         */
        record Cancel() implements Event {

            public static final Cancel INSTANCE = new Cancel();
        }
    }

    /**
     * The output alphabet of the streaming PEP's Mealy machine. Sealed
     * into four cases that cover what the state machine can ask the
     * Reactor adapter to deliver downstream on a single transition.
     * <p>
     * The {@code step} function returns a {@link Step} carrying a
     * {@code List<Emission>} (the multi-output of the MealyMachine
     * formulation): a single step can produce zero, one, or several
     * emissions in order. Zero emissions encode "this event was
     * processed and produced nothing observable" (e.g. a
     * suspended-state item drop, or a mapper that returned null).
     */
    public sealed interface Emission {

        /**
         * Deliver {@code value} to the subscriber. Used for permitted
         * item pass-through after per-item enforcement succeeded.
         */
        record Emit(Object value) implements Emission {}

        /**
         * Terminate the subscriber with an error. Used for explicit
         * DENY, RAP errors, PDP stream errors, and item-enforcement
         * failure when the PEP is configured with
         * {@code terminateOnItemEnforcementFailure = true}.
         */
        record EmitError(Throwable throwable) implements Emission {}

        /**
         * Terminate the subscriber normally. Used when the protected
         * method (RAP) completes without error.
         */
        record EmitComplete() implements Emission {

            public static final EmitComplete INSTANCE = new EmitComplete();
        }

        /**
         * Deliver an out-of-band transition signal carrying a
         * {@link TransitionReason}. Emitted on every suspend / resume
         * boundary crossing. The pipeline gates visibility on the
         * annotation's {@code signalTransitions} flag and surfaces the
         * signal as a non-terminal exception on the error channel
         * ({@link org.springframework.security.access.AccessDeniedException}
         * for suspension boundaries, {@link AccessGrantedException} for
         * resume), consumed via {@code onErrorContinue} or
         * {@link io.sapl.spring.pep.streaming.TransitionSignals}.
         */
        record EmitTransition(TransitionReason reason) implements Emission {}
    }

    /**
     * Why the machine crossed a state boundary. Carried by
     * {@link Emission.EmitTransition} so subscribers (when the PEP is
     * configured with {@code signalTransitions = true}) can react to
     * the boundary, and used internally to format the
     * {@link org.springframework.security.access.AccessDeniedException}
     * message on terminal denial / item-enforcement failure.
     */
    public sealed interface TransitionReason {

        /**
         * The PDP returned an explicit {@code Decision.DENY}.
         * Terminal.
         */
        record DecisionDenied(AuthorizationDecision decision) implements TransitionReason {}

        /**
         * The subscription has been suspended by a non-terminal
         * decision-time event. The {@link SuspendKind} discriminates
         * between the four equivalent suspend-causing inputs the PDP
         * may emit.
         *
         * @param kind discriminator
         * @param decision the decision that caused the suspension
         */
        record Suspended(SuspendKind kind, AuthorizationDecision decision) implements TransitionReason {}

        /**
         * A per-item obligation handler failed when enforcing a single
         * item under an otherwise-valid PERMIT. The PEP's
         * {@code terminateOnItemEnforcementFailure} flag controls the
         * downstream behavior: {@code true} terminates the
         * subscription; {@code false} (default) transitions to
         * suspended and waits for a fresh decision.
         *
         * @param payload the item whose enforcement failed
         * @param throwable the cause of the failure, if available
         */
        record ItemEnforcementFailed(Object payload, @Nullable Throwable throwable) implements TransitionReason {}

        /**
         * The PEP entered or resumed permitting state. Emitted on the
         * pending-to-permitting transition (initial grant) and the
         * suspended-to-permitting transition (resume). Plan
         * replacement (permitting-to-permitting) is silent.
         */
        record Granted(AuthorizationDecision decision) implements TransitionReason {}
    }

    /**
     * Discriminator for {@link TransitionReason.Suspended}. The four
     * causes are isomorphic (each carries an
     * {@link AuthorizationDecision}); the kind names which decision
     * verb or which decision-scoped enforcement outcome put the
     * subscription into the suspended state.
     */
    public enum SuspendKind {
        /** Explicit {@code Decision.SUSPEND} from the PDP. */
        POLICY_SUSPENDED,
        /** {@code Decision.INDETERMINATE} from the PDP. */
        EVALUATION_ERROR,
        /** {@code Decision.NOT_APPLICABLE} from the PDP. */
        NO_POLICY_APPLICABLE,
        /** PERMIT but the plan's decision-scoped enforcement failed. */
        PERMIT_NOT_ENFORCEABLE
    }

    /**
     * The codomain of the machine's combined step function. Pairs the
     * post-step {@link State} with the ordered list of {@link Emission}s
     * produced by the step (the multi-output of the MealyMachine formulation).
     *
     * @param newState the post-step state
     * @param emissions the (possibly empty) emission sequence
     */
    public record Step(State newState, List<Emission> emissions) {

        /**
         * A step into {@code newState} producing the given emissions
         * in order. Zero emissions ({@code Step.to(state)}) means
         * "event processed; nothing to emit downstream."
         */
        public static Step to(State newState, Emission... emissions) {
            return new Step(newState, List.of(emissions));
        }

        /**
         * @return {@code true} when {@link #newState()} is
         * {@link State.Terminated}; the reactor adapter stops
         * dispatching events after observing a terminal step.
         */
        public boolean isTerminal() {
            return newState instanceof State.Terminated;
        }
    }

    /**
     * Compute the next state and emissions for a single (state, event)
     * pair. Pure: no side effects, no Reactor types, no Spring types.
     */
    public static Step step(State state, Event event) {
        if (state instanceof Terminated terminated) {
            return Step.to(terminated);
        }
        return switch (event) {

        // Lifecycle terminations.
        case Cancel ignored      -> terminate();
        case RapComplete ignored -> terminateNormally();
        case RapError(var t)     -> terminateWithError(t);
        case PdpError(var t)     -> terminateWithError(t);

        // Decision events: dispatch on verb.
        case PdpPermit permit   -> onPermit(state, permit);
        case PdpSuspend suspend -> onSuspend(state, suspend);
        case PdpDeny deny       -> onDeny(deny);

        // Item flow: routes through the binding state.
        case RapItem item -> onItem(state, item);
        };
    }

    private static Step onPermit(State state, PdpPermit permit) {
        var next = new Permitting(permit.plan(), permit.terminateOnItemEnforcementFailure());
        // Plan replacement (Permitting -> Permitting) is silent. Initial
        // grant (Pending -> Permitting) and resume (Suspended -> Permitting)
        // emit the Granted boundary signal; the pipeline gates visibility.
        if (state instanceof Permitting) {
            return Step.to(next);
        }
        return Step.to(next, new EmitTransition(new Granted(permit.decision())));
    }

    private static Step onSuspend(State state, PdpSuspend suspend) {
        // Re-suspend within Suspended: the boundary already happened.
        // Silent transition; the new reason flows out via the next
        // EmitTransition only on a true boundary crossing.
        if (state instanceof Suspended) {
            return Step.to(Suspended.INSTANCE);
        }
        return Step.to(Suspended.INSTANCE, new EmitTransition(suspend.reason()));
    }

    private static Step onDeny(PdpDeny deny) {
        var reason = new DecisionDenied(deny.decision());
        return Step.to(Terminated.INSTANCE, new EmitError(new AccessDeniedException(reason.toString())));
    }

    private static Step onItem(State state, RapItem item) {
        return switch (state) {
        // No decision yet: drop silently. RAP should not normally emit
        // before a permit, but the FSM tolerates it.
        case Pending p -> Step.to(p);
        // Suspended: items are dropped silently regardless of reason.
        case Suspended s -> Step.to(s);
        // Permitting: the enforcement result decides; failure routes
        // through permittingItem with the binding flag.
        case Permitting p -> permittingItem(p, item);
        // Exhaustive; unreachable here because Terminated was handled in step().
        case Terminated t -> Step.to(t);
        };
    }

    private static Step permittingItem(Permitting state, RapItem item) {
        var result = item.enforcementResult();
        if (result.failureState()) {
            var reason = new ItemEnforcementFailed(item.payload(), null);
            if (state.terminateOnItemEnforcementFailure()) {
                return Step.to(Terminated.INSTANCE, new EmitError(new AccessDeniedException(reason.toString())));
            }
            return Step.to(Suspended.INSTANCE, new EmitTransition(reason));
        }
        if (result.value() instanceof Maybe.Present<Object>(var value)) {
            return Step.to(state, new Emit(value));
        }
        // Maybe.Absent: the mapper said "drop this item." Empty emission
        // list = no observable output.
        return Step.to(state);
    }

    private static Step terminate() {
        return Step.to(Terminated.INSTANCE);
    }

    private static Step terminateNormally() {
        return Step.to(Terminated.INSTANCE, EmitComplete.INSTANCE);
    }

    private static Step terminateWithError(Throwable throwable) {
        return Step.to(Terminated.INSTANCE, new EmitError(throwable));
    }
}
