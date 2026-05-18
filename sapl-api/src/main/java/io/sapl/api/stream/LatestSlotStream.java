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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Producer-facing {@link Stream} backed by a single-slot, latest-wins
 * mailbox. Producers push values via {@link #put(Object)}; a lagging
 * consumer reads only the latest. Producers signal end-of-stream via
 * {@link #complete()} or via {@link #close()}.
 * <p>
 * Coordination uses {@link ReentrantLock} + {@link Condition} rather
 * than {@code synchronized} + {@code Object.wait()} so a virtual-thread
 * consumer blocked in {@link #awaitNext()} unmounts its carrier
 * thread. Equivalent on JDK 24+ (JEP 491); strictly required on
 * JDK 21 to avoid pinning the carrier pool.
 *
 * @param <T> the value type carried by this stream
 */
public final class LatestSlotStream<T> implements Stream<T> {

    private final AtomicBoolean closed    = new AtomicBoolean(false);
    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition     slotReady = lock.newCondition();

    private Runnable closeAction = () -> {};
    private T        value;
    private boolean  hasValue;
    private boolean  completed;

    /**
     * Sets the action run when {@link #close()} is called. Used to
     * release any underlying subscription that feeds this stream.
     */
    public void onClose(Runnable closeAction) {
        if (!closed.get()) {
            this.closeAction = closeAction;
        }
    }

    /**
     * Pushes a value into the slot. Overwrites any pending unread
     * value. No effect after {@link #complete()} or {@link #close()}.
     */
    public void put(T v) {
        lock.lock();
        try {
            if (completed) {
                return;
            }
            value    = v;
            hasValue = true;
            slotReady.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks the stream completed. After this call, {@link #awaitNext()}
     * returns {@code null} once the slot is drained, and {@link #put}
     * is a no-op.
     */
    public void complete() {
        lock.lock();
        try {
            completed = true;
            slotReady.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T awaitNext() throws InterruptedException {
        lock.lock();
        try {
            while (!hasValue && !completed) {
                slotReady.await();
            }
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return result;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Poll<T> tryNext() {
        lock.lock();
        try {
            if (hasValue) {
                val result = value;
                value    = null;
                hasValue = false;
                return Poll.value(result);
            }
            if (completed) {
                return Poll.done();
            }
            return Poll.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            lock.lock();
            try {
                value     = null;
                hasValue  = false;
                completed = true;
                slotReady.signalAll();
            } finally {
                lock.unlock();
            }
            closeAction.run();
        }
    }
}
