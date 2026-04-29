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
package io.sapl.ast;

/**
 * The set of effects a vote may carry. Each constant is the union of one or
 * more {@link Effect} values, encoded as a bitmask over
 * {@code Effect.ordinal()}
 * positions. Combining two outcomes is bitwise OR over the masks, resolved
 * to the corresponding constant via a precomputed cache. This keeps combine
 * branch-free and allocation-free in hot paths.
 */
public enum Outcome {
    DENY(0b001),
    PERMIT(0b010),
    SUSPEND(0b100),
    PERMIT_OR_DENY(0b011),
    DENY_OR_SUSPEND(0b101),
    PERMIT_OR_SUSPEND(0b110),
    PERMIT_OR_DENY_OR_SUSPEND(0b111);

    private final int mask;

    private static final Outcome[] BY_MASK = new Outcome[8];

    static {
        for (Outcome o : values()) {
            BY_MASK[o.mask] = o;
        }
    }

    Outcome(int mask) {
        this.mask = mask;
    }

    /**
     * Returns the union of two outcomes, allocation-free via a static lookup
     * table indexed by the OR of the input masks.
     *
     * @param a the first outcome
     * @param b the second outcome
     * @return the outcome carrying the union of both effect sets
     */
    public static Outcome combine(Outcome a, Outcome b) {
        return BY_MASK[a.mask | b.mask];
    }

    /**
     * Tests whether this outcome includes the given effect.
     *
     * @param effect the effect to test for
     * @return true if this outcome carries the effect
     */
    public boolean contains(Effect effect) {
        return (mask & (1 << effect.ordinal())) != 0;
    }
}
