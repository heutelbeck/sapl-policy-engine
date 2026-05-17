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
 * Edge-triggered fire coalescer for a single consumer. Replaces the
 * per-consumer {@code ReentrantLock} that previously serialized
 * {@code fireCallback} invocations.
 * <p>
 * Each {@link #requestFire()} call sets a {@code pending} flag and
 * tries to claim the {@code running} flag. The winning thread drains
 * pending into a single in-flight execution of the wrapped runnable
 * and re-checks pending after the runnable returns; concurrent
 * {@code requestFire()} calls that lose the race simply set
 * {@code pending} and return.
 * <p>
 * Properties relevant to brokers:
 * <ul>
 * <li>Publisher and scheduler threads never block on slow consumers
 * (they only flip flags and exit).</li>
 * <li>N rapid requests during a slow in-flight fire collapse into
 * one re-fire afterwards, against the latest broker state.</li>
 * <li>No queue of values, no queue of fire requests. The wrapped
 * runnable always reads the current snapshot at fire time.</li>
 * <li>No skipped fires: any request that sets {@code pending} is
 * guaranteed to lead to at least one subsequent execution.</li>
 * </ul>
 * <p>
 * This object is per-consumer; one instance is held by each
 * subscription. Two different consumers each have their own
 * coalescer.
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
     * Marks a fire as pending and, if no other thread is currently
     * driving the fire loop, becomes that driver. The driver consumes
     * pending into one or more sequential {@code fire.run()} calls
     * and exits when the pending flag has been cleared by a run that
     * was not concurrent with a new request.
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
