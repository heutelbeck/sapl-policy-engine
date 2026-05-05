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

import io.sapl.compiler.document.Vote;

import java.util.Optional;

/**
 * Synchronous-pull stream of {@link Vote} for callers running on
 * virtual threads. Voter-side counterpart of {@link ValueStream} and
 * {@link CoverageStream}: same producer-pushes, consumer-blocks-on-take
 * semantic, single-slot latest-wins mailbox.
 */
public interface VoteStream extends AutoCloseable {

    /**
     * Blocks until the next vote is available or the stream completes.
     *
     * @return the next {@link Vote}, or {@code null} when the stream
     * has completed (no more votes will arrive)
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting
     */
    Vote awaitNext() throws InterruptedException;

    /**
     * Non-blocking probe. Returns and drains the slot if a vote is
     * pending right now; returns {@link Optional#empty()} otherwise.
     * Same caveats as {@link ValueStream#tryNext()}: only meaningful
     * when the test thread is the sole driver of publishes.
     *
     * @return the pending vote if one is queued, otherwise empty
     */
    Optional<Vote> tryNext();

    /**
     * Closes the stream, releasing the underlying subscription. Pending
     * {@link #awaitNext()} calls will return {@code null}. Idempotent.
     */
    @Override
    void close();
}
