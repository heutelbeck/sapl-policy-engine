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

import io.sapl.compiler.document.VoteResultWithCoverage;

import java.util.Optional;

/**
 * Synchronous-pull stream of {@link VoteResultWithCoverage} for callers
 * running on virtual threads. Coverage counterpart of {@link ValueStream}:
 * same producer-pushes, consumer-blocks-on-take semantic, single-slot
 * latest-wins mailbox.
 */
public interface CoverageStream extends AutoCloseable {

    /**
     * Blocks until the next coverage result is available or the stream
     * completes.
     *
     * @return the next {@link VoteResultWithCoverage}, or {@code null}
     * when the stream has completed (no more results will arrive)
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting
     */
    VoteResultWithCoverage awaitNext() throws InterruptedException;

    /**
     * Non-blocking probe. Returns and drains the slot if a result is
     * pending right now; returns {@link Optional#empty()} otherwise.
     * Same caveats as {@link ValueStream#tryNext()}: only meaningful
     * when the test thread is the sole driver of publishes.
     *
     * @return the pending result if one is queued, otherwise empty
     */
    Optional<VoteResultWithCoverage> tryNext();

    /**
     * Closes the stream, releasing the underlying subscription. Pending
     * {@link #awaitNext()} calls will return {@code null}. Idempotent.
     */
    @Override
    void close();
}
