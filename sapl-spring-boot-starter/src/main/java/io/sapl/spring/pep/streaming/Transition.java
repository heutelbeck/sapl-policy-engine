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

/**
 * The codomain of the streaming PEP's combined step function. Pairs the
 * post-transition {@link State} with the ordered list of {@link Emission}s
 * produced by the transition (the multi-output of the Mealy formulation).
 * <p>
 * Wherever a single transition produces zero, one, or several emissions,
 * they appear in {@link #emissions()} in the order the reactor adapter
 * should deliver them downstream. Internal contract: producers (the step
 * function) build {@code Transition} via the static factories below,
 * which use {@link List#of} for immutable emission lists. No defensive
 * copying or null validation here — internal infrastructure trusts its
 * producers.
 *
 * @param newState the post-transition state
 * @param emissions the (possibly empty) emission sequence produced by
 * the transition
 *
 * @since 4.1.0
 */
public record Transition(State newState, List<Emission> emissions) {

    /** Convenience: a transition into {@code newState} that emits nothing. */
    public static Transition to(State newState) {
        return new Transition(newState, List.of());
    }

    /** Convenience: a transition into {@code newState} that emits one item. */
    public static Transition to(State newState, Emission emission) {
        return new Transition(newState, List.of(emission));
    }

    /**
     * Convenience: a transition into {@code newState} that emits two items in
     * order.
     */
    public static Transition to(State newState, Emission first, Emission second) {
        return new Transition(newState, List.of(first, second));
    }

    /**
     * @return {@code true} when {@link #newState()} is
     * {@link State.Terminated}; the reactor adapter stops dispatching
     * events after observing a terminal transition.
     */
    public boolean isTerminal() {
        return newState instanceof State.Terminated;
    }
}
