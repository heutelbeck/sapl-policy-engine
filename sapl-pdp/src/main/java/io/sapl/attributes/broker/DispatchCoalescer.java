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
package io.sapl.attributes.broker;

import lombok.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes a runnable's invocations and collapses concurrent
 * triggers.
 * <p>
 * How: run the runnable; if anyone asked again while it was
 * running, run it once more; otherwise exit. No queue, just one
 * boolean for "re-run needed". N triggers arriving during one
 * in-flight run collapse into one follow-up run.
 * <p>
 * Why: many producer threads can trigger the same consumer without
 * blocking each other, while the consumer's runnable never runs
 * concurrently with itself.
 * <p>
 * Cost: the first caller while no run is in flight runs the
 * runnable on its thread (plus any follow-up runs triggered while
 * it was running). Other concurrent callers set the re-run bit and
 * return without waiting.
 *
 * @since 4.1.0
 */
public final class DispatchCoalescer {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean pending = new AtomicBoolean(false);
    private final Runnable      fire;

    public DispatchCoalescer(@NonNull Runnable fire) {
        this.fire = fire;
    }

    /**
     * Requests a run. If another thread is already running the
     * runnable, sets the re-run bit and returns immediately.
     * Otherwise runs the runnable on the calling thread (plus any
     * follow-up runs triggered during the run) before returning.
     */
    public void requestFire() {
        pending.set(true);
        while (running.compareAndSet(false, true)) {
            try {
                while (pending.compareAndSet(true, false)) {
                    fire.run();
                }
            } finally {
                running.set(false);
            }
            if (!pending.get()) {
                return;
            }
        }
    }
}
