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
package io.sapl.attributes.libraries.vnext.util;

import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("RealTimeScheduler")
class RealTimeSchedulerTests {

    private RealTimeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RealTimeScheduler();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("runs the task at the scheduled instant")
    void whenScheduleAtFutureInstantThenTaskRunsBeforeDeadline() {
        val fired = new AtomicBoolean(false);

        scheduler.scheduleAt(Clock.systemUTC().instant().plusMillis(50), () -> fired.set(true));

        await().atMost(Duration.ofSeconds(1)).untilTrue(fired);
    }

    @Test
    @DisplayName("runs immediately when scheduled instant is in the past")
    void whenScheduleAtPastInstantThenTaskRunsImmediately() {
        val fired = new AtomicBoolean(false);

        scheduler.scheduleAt(Clock.systemUTC().instant().minusSeconds(1), () -> fired.set(true));

        await().atMost(Duration.ofSeconds(1)).untilTrue(fired);
    }

    @Test
    @DisplayName("cancel before fire prevents the task from running")
    void whenCancelBeforeFireThenTaskDoesNotRun() throws InterruptedException {
        val ranCount = new AtomicInteger();

        val cancellable = scheduler.scheduleAt(Clock.systemUTC().instant().plusMillis(200), ranCount::incrementAndGet);
        cancellable.cancel();

        Thread.sleep(300L);
        assertThat(ranCount).hasValue(0);
    }

    @Test
    @DisplayName("a task that throws is logged and does not crash the scheduler")
    void whenTaskThrowsThenSubsequentTasksStillRun() {
        val secondRan = new AtomicBoolean(false);

        scheduler.scheduleAt(Clock.systemUTC().instant().plusMillis(20), () -> {
            throw new RuntimeException("boom");
        });
        scheduler.scheduleAt(Clock.systemUTC().instant().plusMillis(60), () -> secondRan.set(true));

        await().atMost(Duration.ofSeconds(1)).untilTrue(secondRan);
    }
}
