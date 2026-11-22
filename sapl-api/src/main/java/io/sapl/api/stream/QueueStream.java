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
package io.sapl.api.stream;

import io.sapl.api.model.Poll;
import lombok.val;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Producer-facing {@link Stream} backed by an unbounded FIFO queue.
 * Values pushed via {@link #put(Object)} remain queued until read or
 * cancelled. Use when ordered delivery is required and the consumer can keep up.
 * <p>
 * Single producer, single consumer. Producers signal end-of-stream
 * via {@link #complete()}, allowing the consumer to drain queued values.
 * Consumer cancellation via {@link #close()} discards unread values.
 *
 * @param <T> the value type carried by this stream
 */
public final class QueueStream<T> implements Stream<T> {

    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final ReentrantLock lock  = new ReentrantLock();
    private final Condition     ready = lock.newCondition();

    private boolean  completed;
    private boolean  closed;
    private Runnable closeAction = () -> {};

    /**
     * Sets the action run when {@link #close()} is called. Used to
     * release any underlying subscription that feeds this stream.
     *
     * @param closeAction the cleanup to run on cancellation
     */
    public void onClose(Runnable closeAction) {
        lock.lock();
        try {
            if (!closed) {
                this.closeAction = closeAction;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Appends a value to the queue. No effect after {@link #complete()}
     * or {@link #close()}.
     *
     * @param value the value to enqueue
     */
    public void put(T value) {
        lock.lock();
        try {
            if (!completed) {
                queue.addLast(value);
                ready.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks the stream completed. Queued values remain readable. Once
     * drained, {@link #awaitNext()} returns {@code null} and
     * {@link #tryNext()} returns {@link Poll.Done}. Idempotent.
     */
    public void complete() {
        lock.lock();
        try {
            completed = true;
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T awaitNext() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty() && !completed) {
                ready.await();
            }
            return queue.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Poll<T> tryNext() {
        lock.lock();
        try {
            val item = queue.pollFirst();
            if (item != null) {
                return Poll.value(item);
            }
            return completed ? Poll.done() : Poll.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed    = true;
            completed = true;
            queue.clear();
            ready.signalAll();
        } finally {
            lock.unlock();
        }
        closeAction.run();
    }
}
