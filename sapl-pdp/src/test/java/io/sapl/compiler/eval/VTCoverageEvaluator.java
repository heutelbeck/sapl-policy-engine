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
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evaluates a {@link CoverageVoter} against an {@link AttributeStore}
 * and exposes per-round results as a {@link CoverageStream}. Coverage
 * counterpart of {@link VTExpressionEvaluator}: same subscription
 * lifecycle, single-slot latest-wins mailbox, blocking await on the
 * consumer side.
 * <p>
 * If the initial evaluation returns no dependencies (pure policy body),
 * the stream delivers one {@link VoteResultWithCoverage} and completes.
 * Otherwise it opens a subscription on the store, re-evaluates the
 * voter on every callback, and pushes the result into the slot.
 */
@UtilityClass
public class VTCoverageEvaluator {

    public static CoverageStream evaluate(CoverageVoter voter, AttributeStore store) {
        val baseCtx = SaplTesting.evaluationContext();
        val slot    = new LatestSlot();
        val closed  = new AtomicBoolean(false);

        val initial = voter.evaluate(baseCtx);
        if (initial.voteResult().dependencies().isEmpty()) {
            slot.put(initial);
            slot.complete();
            return new BlockingCoverageStream(slot, closed, () -> {});
        }

        val sub = store.open("vt-cov-" + UUID.randomUUID(), initial.voteResult().dependencies().keySet(), snap -> {
            val r = voter.evaluate(baseCtx.withSnapshot(snap));
            // Mirror VTExpressionEvaluator's "skip on incomplete" rule: a null
            // vote means the body did not resolve (some dep still missing); do
            // not surface a partial coverage result to the consumer.
            if (r.voteResult().vote() != null) {
                slot.put(r);
            }
            return r.voteResult().dependencies().keySet();
        });
        return new BlockingCoverageStream(slot, closed, () -> {
            slot.complete();
            sub.close();
        });
    }

    /**
     * Single-slot mailbox for {@link VoteResultWithCoverage}. Producer's
     * {@link #put(VoteResultWithCoverage)} overwrites any unread value;
     * a lagging consumer reads only the latest.
     */
    private static final class LatestSlot {
        private VoteResultWithCoverage value;
        private boolean                hasValue;
        private boolean                completed;

        synchronized void put(VoteResultWithCoverage v) {
            if (completed) {
                return;
            }
            value    = v;
            hasValue = true;
            notifyAll();
        }

        synchronized void complete() {
            completed = true;
            notifyAll();
        }

        synchronized VoteResultWithCoverage take() throws InterruptedException {
            while (!hasValue && !completed) {
                wait();
            }
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return result;
            }
            return null;
        }

        synchronized Optional<VoteResultWithCoverage> tryTake() {
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return Optional.of(result);
            }
            return Optional.empty();
        }
    }

    private static final class BlockingCoverageStream implements CoverageStream {
        private final LatestSlot    slot;
        private final AtomicBoolean closed;
        private final Runnable      closeAction;

        BlockingCoverageStream(LatestSlot slot, AtomicBoolean closed, Runnable closeAction) {
            this.slot        = slot;
            this.closed      = closed;
            this.closeAction = closeAction;
        }

        @Override
        public VoteResultWithCoverage awaitNext() throws InterruptedException {
            return slot.take();
        }

        @Override
        public Optional<VoteResultWithCoverage> tryNext() {
            return slot.tryTake();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                closeAction.run();
            }
        }
    }
}
