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
package io.sapl.api.stream;

import java.time.Instant;

/**
 * Schedules a task to run at a specific {@link Instant}. The
 * production implementation runs tasks against wall-clock time on a
 * shared executor; deterministic test implementations queue tasks and
 * fire them on explicit time advance.
 */
public interface TimeScheduler {

    /**
     * Schedules {@code task} to run at {@code when}. If {@code when}
     * is in the past, the task may run immediately. The returned
     * {@link Cancellable} prevents the task from running if cancelled
     * before its scheduled fire time.
     *
     * @param when the instant at which the task should run
     * @param task the task to run
     * @return a handle to cancel the scheduled task
     */
    Cancellable scheduleAt(Instant when, Runnable task);
}
