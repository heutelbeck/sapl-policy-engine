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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compiles an expression string and exposes its evaluation as a
 * {@link ValueStream} backed by an {@link AttributeStore}. Pure
 * expressions deliver one value then complete. Streaming expressions
 * deliver the latest value per fulfilled trigger; consumer blocks on
 * {@link ValueStream#awaitNext()} until the next value or completion.
 * <p>
 * Single-slot mailbox semantic: if the producer fires multiple times
 * before the consumer reads, the consumer observes only the latest
 * value. Intermediate values are dropped. Matches the same drop-old
 * behaviour as the {@link FluxExpressionEvaluator} side via
 * {@code onBackpressureLatest()}.
 * <p>
 * Synchronous-pull counterpart of {@link FluxExpressionEvaluator} for
 * callers running on virtual threads. The consumer's blocking awaitNext
 * is cheap (no platform thread tied up); the AttributeStore callback
 * runs on whatever thread published the underlying value and just
 * overwrites the slot.
 */
@UtilityClass
public class VTExpressionEvaluator {

    public static ValueStream evaluate(String expression, AttributeStore store) {
        return evaluate(SaplTesting.compileExpression(expression), store);
    }

    public static ValueStream evaluate(CompiledExpression expr, AttributeStore store) {
        val baseCtx = SaplTesting.evaluationContext();
        val slot    = new LatestSlot();
        val closed  = new AtomicBoolean(false);

        if (expr instanceof Value v) {
            slot.put(v);
            slot.complete();
            return new BlockingValueStream(slot, closed, () -> {});
        }
        if (expr instanceof PureOperator p) {
            slot.put(p.evaluate(baseCtx));
            slot.complete();
            return new BlockingValueStream(slot, closed, () -> {});
        }
        if (!(expr instanceof StreamOperator streamOp)) {
            slot.put(Value.error("Unexpected CompiledExpression variant: " + expr.getClass().getName()));
            slot.complete();
            return new BlockingValueStream(slot, closed, () -> {});
        }

        val initialDeps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
        StreamOperator.evalChild(streamOp, baseCtx, initialDeps);
        if (initialDeps.isEmpty()) {
            val r = streamOp.evaluate(baseCtx);
            if (r.result() != null) {
                slot.put(r.result());
            }
            slot.complete();
            return new BlockingValueStream(slot, closed, () -> {});
        }

        val sub = store.open("vt-eval-" + UUID.randomUUID(), initialDeps.keySet(), snap -> {
            val r = streamOp.evaluate(baseCtx.withSnapshot(snap));
            if (r.result() != null) {
                slot.put(r.result());
            }
            return r.dependencies().keySet();
        });
        return new BlockingValueStream(slot, closed, () -> {
            slot.complete();
            sub.close();
        });
    }

    /**
     * Single-slot mailbox: producer's {@link #put(Value)} overwrites any
     * unread value, so a consumer that lags behind a fast producer reads
     * only the latest value. Bursts collapse. {@link #complete()} signals
     * end-of-stream; subsequent {@link #take()} returns {@code null}.
     * Reads after completion that find a pending unread value still
     * deliver it before returning {@code null} on the following call.
     */
    private static final class LatestSlot {
        private Value   value;
        private boolean hasValue;
        private boolean completed;

        synchronized void put(Value v) {
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

        synchronized Value take() throws InterruptedException {
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

        synchronized Optional<Value> tryTake() {
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return Optional.of(result);
            }
            return Optional.empty();
        }
    }

    private static final class BlockingValueStream implements ValueStream {
        private final LatestSlot    slot;
        private final AtomicBoolean closed;
        private final Runnable      closeAction;

        BlockingValueStream(LatestSlot slot, AtomicBoolean closed, Runnable closeAction) {
            this.slot        = slot;
            this.closed      = closed;
            this.closeAction = closeAction;
        }

        @Override
        public Value awaitNext() throws InterruptedException {
            return slot.take();
        }

        @Override
        public Optional<Value> tryNext() {
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
