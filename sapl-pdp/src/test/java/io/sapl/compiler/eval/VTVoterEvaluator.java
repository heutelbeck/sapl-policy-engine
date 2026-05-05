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

import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.Voter;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evaluates a {@link Voter} against an {@link AttributeStore} and
 * exposes per-round votes as a {@link VoteStream}. Voter-side
 * counterpart of {@link VTExpressionEvaluator} and
 * {@link VTCoverageEvaluator}: same subscription lifecycle, single-slot
 * latest-wins mailbox, blocking await on the consumer side.
 * <p>
 * A {@link Vote} terminal voter or a {@link PureVoter} delivers one
 * vote and completes. A {@link StreamVoter} opens a subscription on
 * the store, re-evaluates on every callback, and pushes the resulting
 * vote into the slot when the result is non-null. Incomplete rounds
 * (vote == null because some dep was not yet bound) are skipped.
 */
@UtilityClass
public class VTVoterEvaluator {

    public static VoteStream evaluate(Voter voter, AttributeStore store) {
        return evaluate(voter, SaplTesting.evaluationContext(), store);
    }

    public static VoteStream evaluate(Voter voter, EvaluationContext baseCtx, AttributeStore store) {
        val slot   = new LatestSlot();
        val closed = new AtomicBoolean(false);

        if (voter instanceof Vote v) {
            slot.put(v);
            slot.complete();
            return new BlockingVoteStream(slot, closed, () -> {});
        }
        if (voter instanceof PureVoter p) {
            slot.put(p.vote(baseCtx));
            slot.complete();
            return new BlockingVoteStream(slot, closed, () -> {});
        }
        if (!(voter instanceof StreamVoter streamVoter)) {
            throw new IllegalStateException("Unexpected Voter variant: " + voter.getClass().getName());
        }

        val initial = streamVoter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            if (initial.vote() != null) {
                slot.put(initial.vote());
            }
            slot.complete();
            return new BlockingVoteStream(slot, closed, () -> {});
        }

        val sub = store.open("vt-voter-" + UUID.randomUUID(), initial.dependencies().keySet(), snap -> {
            val r = streamVoter.evaluate(baseCtx.withSnapshot(snap));
            if (r.vote() != null) {
                slot.put(r.vote());
            }
            return r.dependencies().keySet();
        });
        return new BlockingVoteStream(slot, closed, () -> {
            slot.complete();
            sub.close();
        });
    }

    /**
     * Single-slot mailbox for {@link Vote}. Producer's {@link #put(Vote)}
     * overwrites any unread value; a lagging consumer reads only the
     * latest.
     */
    private static final class LatestSlot {
        private Vote    value;
        private boolean hasValue;
        private boolean completed;

        synchronized void put(Vote v) {
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

        synchronized Vote take() throws InterruptedException {
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

        synchronized Optional<Vote> tryTake() {
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return Optional.of(result);
            }
            return Optional.empty();
        }
    }

    private static final class BlockingVoteStream implements VoteStream {
        private final LatestSlot    slot;
        private final AtomicBoolean closed;
        private final Runnable      closeAction;

        BlockingVoteStream(LatestSlot slot, AtomicBoolean closed, Runnable closeAction) {
            this.slot        = slot;
            this.closed      = closed;
            this.closeAction = closeAction;
        }

        @Override
        public Vote awaitNext() throws InterruptedException {
            return slot.take();
        }

        @Override
        public Optional<Vote> tryNext() {
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
