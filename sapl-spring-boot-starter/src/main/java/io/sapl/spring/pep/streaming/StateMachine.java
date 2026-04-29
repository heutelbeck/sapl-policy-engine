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
import io.sapl.spring.pep.streaming.Event.RapComplete;
import io.sapl.spring.pep.streaming.Event.RapError;
import io.sapl.spring.pep.streaming.Event.RapItem;
import io.sapl.spring.pep.streaming.Event.Request;
import io.sapl.spring.pep.streaming.State.Denying;
import io.sapl.spring.pep.streaming.State.Pending;
import io.sapl.spring.pep.streaming.State.Permitting;
import io.sapl.spring.pep.streaming.State.Terminated;
import io.sapl.spring.pep.streaming.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.TransitionReason.ItemEnforcementFailed;
import io.sapl.spring.util.Maybe;
import lombok.experimental.UtilityClass;

/**
 * The streaming PEP's transition function. One pure step:
 * {@code (state, event) -> (newState, emissions)}.
 * <p>
 * Behaviour is parameterised by a single boolean carried on each
 * deny-shaped event ({@link PdpDeny#hardDeny()} and the binding
 * {@link Permitting#hardDeny()} on the current state for item-enforcement
 * failures): {@code hardDeny == true} routes the deny to
 * {@link Terminated} with a terminal {@link EmitError};
 * {@code hardDeny == false} routes it to {@link Denying} with a
 * non-terminal {@link EmitTransition}. Lifecycle and item-flow events are
 * mode-invariant.
 * <p>
 * Boundary signals on the permit side fire symmetrically on every entry
 * into {@link Permitting} from a non-Permitting state (initial grant or
 * recovery from Denying); plan replacement is silent. The pipeline gates
 * visibility of these emissions via the annotation's
 * {@code signalTransitions} flag.
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

        // Lifecycle terminations and no-ops (mode-invariant).
        case Cancel ignored      -> terminate();
        case RapComplete ignored -> terminateNormally();
        case RapError(var t)     -> terminateWithError(t);
        case PdpError(var t)     -> terminateWithError(t);
        case PdpComplete ignored -> Transition.to(state);
        case Request ignored     -> Transition.to(state);

        // Decision events: mode comes from the event.
        case PdpPermit permit -> onPermit(state, permit);
        case PdpDeny deny     -> onDeny(state, deny);

        // Item flow: mode is read from the binding state when needed.
        case RapItem item -> onItem(state, item);
        };
    }

    private static Transition onPermit(State state, PdpPermit permit) {
        var next = new Permitting(permit.plan(), permit.decision(), permit.hardDeny());
        // Plan replacement (Permitting -> Permitting) is silent. Initial
        // grant (Pending -> Permitting) and recovery (Denying -> Permitting)
        // emit the Granted boundary signal; the pipeline gates visibility.
        if (state instanceof Permitting) {
            return Transition.to(next);
        }
        return Transition.to(next, new EmitTransition(new Granted(permit.decision())));
    }

    private static Transition onDeny(State state, PdpDeny deny) {
        if (deny.hardDeny()) {
            return Transition.to(new Terminated(deny.reason()),
                    new EmitError(new AccessDeniedException(deny.reason().toString())));
        }
        var next = new Denying(deny.plan(), deny.decision(), deny.reason(), deny.hardDeny());
        // Re-deny: the boundary already happened, only the reason changed.
        if (state instanceof Denying) {
            return Transition.to(next);
        }
        return Transition.to(next, new EmitTransition(deny.reason()));
    }

    private static Transition onItem(State state, RapItem item) {
        return switch (state) {
        // No decision yet: drop silently. RAP should not normally emit
        // before a permit, but the FSM tolerates it.
        case Pending p -> Transition.to(p);
        // Denied: items are dropped silently regardless of mode.
        case Denying d -> Transition.to(d);
        // Permitting: enforcement result decides the emission; failure
        // routes through the deny path with the binding hardDeny.
        case Permitting p -> permittingItem(p, item);
        // Exhaustive; unreachable here because Terminated was handled in step().
        case Terminated t -> Transition.to(t);
        };
    }

    private static Transition permittingItem(Permitting state, RapItem item) {
        var result = item.enforcementResult();
        if (result.failureState()) {
            // Per-item obligation failure routes through the deny path with
            // the binding hardDeny from the current Permitting state.
            var reason = new ItemEnforcementFailed(item.payload(), null);
            if (state.hardDeny()) {
                return Transition.to(new Terminated(reason),
                        new EmitError(new AccessDeniedException(reason.toString())));
            }
            var next = new Denying(state.plan(), state.lastDecision(), reason, state.hardDeny());
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
