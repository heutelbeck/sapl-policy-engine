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
package io.sapl.compiler.eval;

import io.sapl.api.model.Value;

import java.util.Optional;

/**
 * Synchronous-pull equivalent of {@code Flux<Value>} for callers running
 * on virtual threads. Producer pushes values via the source's callback;
 * consumer blocks on {@link #awaitNext()} until the next value is
 * available or the stream completes. {@code null} return signals
 * completion.
 */
public interface ValueStream extends AutoCloseable {

    /**
     * Blocks until the next value is available or the stream completes.
     *
     * @return the next value, or {@code null} when the stream has
     * completed (no more values will arrive)
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting
     */
    Value awaitNext() throws InterruptedException;

    /**
     * Non-blocking probe. Returns and drains the slot if a value is
     * pending right now; returns {@link Optional#empty()} otherwise.
     * Used by tests to assert "no emission queued at this instant".
     * <p>
     * The empty result only means "nothing pending now" -- a producer
     * thread can publish microseconds later. When the test is the sole
     * driver of publishes and the underlying store fires callbacks
     * synchronously on the publish-caller thread (as
     * {@code TestAttributeStore} does), the assertion is deterministic.
     *
     * @return the pending value if one is queued, otherwise empty
     */
    Optional<Value> tryNext();

    /**
     * Closes the stream, releasing the underlying subscription. Pending
     * {@link #awaitNext()} calls will return {@code null}. Idempotent.
     */
    @Override
    void close();
}
