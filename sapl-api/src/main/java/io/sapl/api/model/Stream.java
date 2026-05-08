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
package io.sapl.api.model;

/**
 * A closeable handle for receiving values one at a time. Use
 * {@link #awaitNext()} to block until a value is ready. Use
 * {@link #tryNext()} for a non-blocking poll that distinguishes a
 * delivered value, an open-but-empty stream, and a completed
 * stream via the {@link Poll} sealed type.
 * <p>
 * A stream that has completed delivers no further values:
 * {@link #awaitNext()} returns {@code null}, {@link #tryNext()}
 * returns {@link Poll.Done}. A stream completes either when its
 * source has no more values to produce or when {@link #close()}
 * is called.
 * <p>
 * Always close the stream when finished. Use try-with-resources.
 *
 * @param <T> the value type carried by this stream
 *
 * @since 4.1.0
 */
public interface Stream<T> extends AutoCloseable {

    /**
     * Returns the next value, blocking the calling thread until one
     * is available. Returns {@code null} if the stream has completed.
     *
     * @return the next value, or {@code null} on completion
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting
     */
    T awaitNext() throws InterruptedException;

    /**
     * Polls for the next value without blocking. Returns one of:
     * <ul>
     * <li>{@link Poll.Value} - a value was available and is consumed</li>
     * <li>{@link Poll.Empty} - no value was available; the stream is
     * still open and may yield a value later</li>
     * <li>{@link Poll.Done} - the stream has completed; no further
     * values will be produced</li>
     * </ul>
     * <p>
     * Drain pattern:
     *
     * <pre>{@code
     * while (true) {
     *     switch (stream.tryNext()) {
     *     case Poll.Value(var v) -> handle(v);
     *     case Poll.Empty<T> e -> waitOrYield();
     *     case Poll.Done<T> d -> {
     *         return;
     *     }
     *     }
     * }
     * }</pre>
     *
     * @return the poll outcome
     */
    Poll<T> tryNext();

    /**
     * Closes the stream. After this returns, no further values
     * will be delivered. Idempotent and safe to call from any thread.
     */
    @Override
    void close();
}
