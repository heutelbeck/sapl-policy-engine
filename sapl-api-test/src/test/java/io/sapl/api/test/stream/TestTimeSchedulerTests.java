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
package io.sapl.api.test.stream;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TestTimeScheduler")
class TestTimeSchedulerTests {

    private static final Instant T0 = Instant.parse("2026-05-08T12:00:00Z");

    @Test
    @DisplayName("does not run a scheduled task until time advances to its instant")
    void whenAdvanceBeforeTaskInstantThenTaskNotRun() {
        val scheduler = new TestTimeScheduler(T0);
        val ranBefore = new AtomicBoolean(false);
        scheduler.scheduleAt(T0.plusSeconds(10), () -> ranBefore.set(true));

        scheduler.advanceTo(T0.plusSeconds(5));

        assertThat(ranBefore).isFalse();
    }

    @Test
    @DisplayName("runs a scheduled task when time advances to its instant")
    void whenAdvanceToTaskInstantThenTaskRuns() {
        val scheduler = new TestTimeScheduler(T0);
        val ran       = new AtomicBoolean(false);
        scheduler.scheduleAt(T0.plusSeconds(10), () -> ran.set(true));

        scheduler.advanceTo(T0.plusSeconds(10));

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("runs tasks in scheduled-instant order, ties by insertion")
    void whenMultipleTasksThenFiredInScheduledOrder() {
        val scheduler = new TestTimeScheduler(T0);
        val order     = new ArrayList<String>();
        scheduler.scheduleAt(T0.plusSeconds(20), () -> order.add("c"));
        scheduler.scheduleAt(T0.plusSeconds(10), () -> order.add("a"));
        scheduler.scheduleAt(T0.plusSeconds(10), () -> order.add("b"));

        scheduler.advanceTo(T0.plusSeconds(30));

        assertThat(order).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("cancel before fire removes the task from the queue")
    void whenCancelBeforeFireThenTaskNotRun() {
        val scheduler = new TestTimeScheduler(T0);
        val ran       = new AtomicBoolean(false);
        val cancel    = scheduler.scheduleAt(T0.plusSeconds(10), () -> ran.set(true));

        cancel.cancel();
        scheduler.advanceTo(T0.plusSeconds(20));

        assertThat(ran).isFalse();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    @DisplayName("advancing to a past time is a no-op")
    void whenAdvanceBackwardThenNothingHappens() {
        val scheduler = new TestTimeScheduler(T0.plusSeconds(60));
        val ran       = new AtomicBoolean(false);
        scheduler.scheduleAt(T0.plusSeconds(10), () -> ran.set(true));

        scheduler.advanceTo(T0);

        assertThat(ran).isFalse();
        assertThat(scheduler.pendingCount()).isOne();
    }
}
