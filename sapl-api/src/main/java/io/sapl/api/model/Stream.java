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

import java.util.Optional;

/**
 * A closeable handle for receiving values one at a time. Use
 * {@link #awaitNext()} to block until a value is ready. Use
 * {@link #tryNext()} to retrieve a value without blocking, or
 * {@link Optional#empty()} if none is queued.
 * <p>
 * A stream that has completed delivers no further values:
 * {@link #awaitNext()} returns {@code null}, {@link #tryNext()}
 * returns {@link Optional#empty()}. A stream completes either when
 * its source has no more values to produce or when {@link #close()}
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
     * Returns the next value if one is queued, otherwise
     * returns {@link Optional#empty()}. Never blocks.
     *
     * @return the next value, or empty if none is queued
     */
    Optional<T> tryNext();

    /**
     * Closes the stream. After this returns, no further values
     * will be delivered. Idempotent and safe to call from any thread.
     */
    @Override
    void close();
}
