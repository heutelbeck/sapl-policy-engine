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

import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.streaming.Emission.Emit;
import io.sapl.spring.pep.streaming.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.Emission.EmitError;
import io.sapl.spring.pep.streaming.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.Emission.StayQuiet;
import io.sapl.spring.pep.streaming.Event.Cancel;
import io.sapl.spring.pep.streaming.Event.PdpComplete;
import io.sapl.spring.pep.streaming.Event.PdpDeny;
import io.sapl.spring.pep.streaming.Event.PdpError;
import io.sapl.spring.pep.streaming.Event.PdpPermit;
import io.sapl.spring.pep.streaming.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.Event.RapComplete;
import io.sapl.spring.pep.streaming.Event.RapError;
import io.sapl.spring.pep.streaming.Event.RapItem;
import io.sapl.spring.pep.streaming.Event.Request;
import io.sapl.spring.pep.streaming.State.Pending;
import io.sapl.spring.pep.streaming.State.Permitting;
import io.sapl.spring.pep.streaming.State.Suspended;
import io.sapl.spring.pep.streaming.State.Terminated;
import io.sapl.spring.pep.streaming.TransitionReason.DecisionDenied;
import io.sapl.spring.pep.streaming.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.TransitionReason.ItemEnforcementFailed;
import io.sapl.spring.util.Maybe;
import lombok.experimental.UtilityClass;

/**
 * The streaming PEP's transition function. One pure step:
 * {@code (state, event) -> (newState, emissions)}.
 * <p>
 * Routing dispatches on the PDP decision verb (carried by the event) and
 * the current state. There is no annotation-driven boolean parameter on
 * the deny path: explicit {@link PdpDeny} always terminates;
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
public class StateMachine {

    /**
     * Compute the next state and emissions for a single (state, event)
     * pair. Pure: no side effects, no Reactor types, no Spring types.
     */
    public static Transition step(State state, Event event) {
        if (state instanceof Terminated terminated) {
            return Transition.to(terminated);
        }
        return switch (event) {

        // Lifecycle terminations and no-ops.
        case Cancel ignored      -> terminate();
        case RapComplete ignored -> terminateNormally();
        case RapError(var t)     -> terminateWithError(t);
        case PdpError(var t)     -> terminateWithError(t);
        case PdpComplete ignored -> Transition.to(state);
        case Request ignored     -> Transition.to(state);

        // Decision events: dispatch on verb.
        case PdpPermit permit   -> onPermit(state, permit);
        case PdpSuspend suspend -> onSuspend(state, suspend);
        case PdpDeny deny       -> onDeny(deny);

        // Item flow: routes through the binding state.
        case RapItem item -> onItem(state, item);
        };
    }

    private static Transition onPermit(State state, PdpPermit permit) {
        var next = new Permitting(permit.plan(), permit.decision(), permit.terminateOnItemEnforcementFailure());
        // Plan replacement (Permitting -> Permitting) is silent. Initial
        // grant (Pending -> Permitting) and resume (Suspended -> Permitting)
        // emit the Granted boundary signal; the pipeline gates visibility.
        if (state instanceof Permitting) {
            return Transition.to(next);
        }
        return Transition.to(next, new EmitTransition(new Granted(permit.decision())));
    }

    private static Transition onSuspend(State state, PdpSuspend suspend) {
        var next = new Suspended(suspend.plan(), suspend.decision(), suspend.reason());
        // Re-suspend within Suspended: the boundary already happened; only
        // the reason changed. Silent transition; downstream readers can
        // observe the new reason via state inspection if needed.
        if (state instanceof Suspended) {
            return Transition.to(next);
        }
        return Transition.to(next, new EmitTransition(suspend.reason()));
    }

    private static Transition onDeny(PdpDeny deny) {
        var reason = new DecisionDenied(deny.decision());
        return Transition.to(new Terminated(reason), new EmitError(new AccessDeniedException(reason.toString())));
    }

    private static Transition onItem(State state, RapItem item) {
        return switch (state) {
        // No decision yet: drop silently. RAP should not normally emit
        // before a permit, but the FSM tolerates it.
        case Pending p -> Transition.to(p);
        // Suspended: items are dropped silently regardless of reason.
        case Suspended s -> Transition.to(s);
        // Permitting: the enforcement result decides; failure routes
        // through onPermittingItemFailure with the binding flag.
        case Permitting p -> permittingItem(p, item);
        // Exhaustive; unreachable here because Terminated was handled in step().
        case Terminated t -> Transition.to(t);
        };
    }

    private static Transition permittingItem(Permitting state, RapItem item) {
        var result = item.enforcementResult();
        if (result.failureState()) {
            var reason = new ItemEnforcementFailed(item.payload(), null);
            if (state.terminateOnItemEnforcementFailure()) {
                return Transition.to(new Terminated(reason),
                        new EmitError(new AccessDeniedException(reason.toString())));
            }
            var next = new Suspended(state.plan(), state.lastDecision(), reason);
            return Transition.to(next, new EmitTransition(reason));
        }
        if (result.value() instanceof Maybe.Present<Object>(var value)) {
            return Transition.to(state, new Emit(value));
        }
        // Maybe.Absent: the mapper said "drop this item."
        return Transition.to(state, StayQuiet.INSTANCE);
    }

    private static Transition terminate() {
        return Transition.to(new Terminated(null));
    }

    private static Transition terminateNormally() {
        return Transition.to(new Terminated(null), EmitComplete.INSTANCE);
    }

    private static Transition terminateWithError(Throwable throwable) {
        return Transition.to(new Terminated(null), new EmitError(throwable));
    }
}
