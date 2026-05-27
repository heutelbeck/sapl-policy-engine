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

import org.junit.jupiter.api.Test;

import io.sapl.spring.pep.streaming.MealyMachine.State.Pending;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;

/**
 * Sequence-level invariants on
 * {@link MealyMachine#step(State, Event)}.
 * <p>
 * Each test names the theorem from
 * {@code stream-pep-lean/StreamPepFsm/Properties.lean} that it
 * witnesses. The file grows as the Lean module proves additional
 * sequence theorems; if a future test needs a generic iterated fold,
 * introduce a {@code Trace} helper at that point.
 */
class MealyMachineSequenceInvariantTests {

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
}
