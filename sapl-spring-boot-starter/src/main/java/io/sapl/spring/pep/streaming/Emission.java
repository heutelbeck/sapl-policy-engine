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

/**
 * The output alphabet of the streaming PEP's FSM. Sealed into five cases
 * that cover what the state machine can ask the Reactor adapter to
 * deliver downstream on a single transition.
 * <p>
 * The {@code step} function returns a {@link Transition} carrying a
 * {@code List<Emission>} (the multi-output of the Mealy formulation): a
 * single transition can produce zero ({@code [StayQuiet]} or just
 * {@code []}), one ({@code [Emit(value)]}), or several
 * ({@code [EmitTransition(Granted), ...]}) emissions in order.
 * <p>
 * The Reactor adapter realises emissions onto a downstream
 * {@code Sinks.Many} in the order returned by {@code step}.
 *
 * @since 4.1.0
 */
public sealed interface Emission {

    /**
     * Deliver {@code value} to the subscriber. Used for permitted item
     * pass-through after per-item enforcement succeeded.
     */
    record Emit(Object value) implements Emission {}

    /**
     * Terminate the subscriber with an error. Used for {@link
     * io.sapl.spring.method.metadata.StreamMode#TILL_DENIED} on the first
     * deny, RAP errors, PDP stream errors, and item-enforcement failure
     * under TILL_DENIED. The Reactor adapter pushes this onto the
     * downstream sink's error channel; the machine transitions to
     * {@link State.Terminated}.
     */
    record EmitError(Throwable throwable) implements Emission {}

    /**
     * Terminate the subscriber normally. Used when the protected method
     * (RAP) completes without error. The Reactor adapter completes the
     * downstream sink; the machine transitions to {@link State.Terminated}.
     */
    record EmitComplete() implements Emission {
        public static final EmitComplete INSTANCE = new EmitComplete();
    }

    /**
     * Deliver an out-of-band transition signal carrying a
     * {@link TransitionReason}. Used by
     * {@link io.sapl.spring.method.metadata.StreamMode#ACCESS_AWARE} on
     * every PERMIT/DENY boundary crossing so the client (or a UI) can
     * disambiguate "no items because denied" from "no items because the
     * source is idle." The Reactor adapter routes this to whichever
     * channel the access-aware contract specifies (data channel as a
     * sealed message type, or a parallel sink — design choice deferred
     * until the adapter is drafted).
     */
    record EmitTransition(TransitionReason reason) implements Emission {}

    /**
     * Do nothing. Used when the variant policy determines an event
     * produces no observable output: an item dropped while denied, an
     * item whose Mapper returned {@code null} ("policy says emit
     * nothing"), or a self-loop event with no associated lifecycle work.
     */
    record StayQuiet() implements Emission {
        public static final StayQuiet INSTANCE = new StayQuiet();
    }
}
