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

import io.sapl.api.model.Stream;
import lombok.val;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Producer-facing {@link Stream} backed by an unbounded FIFO queue.
 * Every value pushed via {@link #put(Object)} is delivered to the
 * consumer; no value is dropped. Use when ordered, full-fidelity
 * delivery is required and the consumer can keep up.
 * <p>
 * Single producer, single consumer. Producers signal end-of-stream
 * via {@link #complete()} or via {@link #close()}; subsequent
 * {@link #awaitNext()} calls return {@code null} and {@link #tryNext()}
 * calls return {@link Optional#empty()}.
 *
 * @param <T> the value type carried by this stream
 */
final class QueueStream<T> implements Stream<T> {

    private static final Object COMPLETION_SENTINEL = new Object();

    private final BlockingQueue<Object> queue  = new LinkedBlockingQueue<>();
    private final AtomicBoolean         closed = new AtomicBoolean(false);

    private volatile boolean completed   = false;
    private Runnable         closeAction = () -> {};

    /**
     * Sets the action run when {@link #close()} is called. Used to
     * release any underlying subscription that feeds this stream.
     */
    void onClose(Runnable closeAction) {
        if (!closed.get()) {
            this.closeAction = closeAction;
        }
    }

    /**
     * Appends a value to the queue. No effect after {@link #complete()}
     * or {@link #close()}.
     */
    void put(T value) {
        if (completed) {
            return;
        }
        queue.add(value);
    }

    /**
     * Marks the stream completed. Queued values remain readable; once
     * drained, {@link #awaitNext()} returns {@code null} and
     * {@link #tryNext()} returns {@link Optional#empty()}. Idempotent.
     */
    void complete() {
        if (completed) {
            return;
        }
        completed = true;
        queue.add(COMPLETION_SENTINEL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T awaitNext() throws InterruptedException {
        val item = queue.take();
        if (item == COMPLETION_SENTINEL) {
            queue.add(COMPLETION_SENTINEL);
            return null;
        }
        return (T) item;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<T> tryNext() {
        val item = queue.poll();
        if (item == null) {
            return Optional.empty();
        }
        if (item == COMPLETION_SENTINEL) {
            queue.add(COMPLETION_SENTINEL);
            return Optional.empty();
        }
        return Optional.of((T) item);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            complete();
            closeAction.run();
        }
    }
}
