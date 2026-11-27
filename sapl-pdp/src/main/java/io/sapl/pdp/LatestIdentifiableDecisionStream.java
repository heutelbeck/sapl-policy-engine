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
package io.sapl.pdp;

import io.sapl.api.model.Poll;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.stream.Stream;
import lombok.val;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Latest-wins stream for identifiable multi-decision changes. Pending work is
 * bounded by subscription ID count.
 */
final class LatestIdentifiableDecisionStream implements Stream<IdentifiableAuthorizationDecision> {

    private final AtomicBoolean                                            closed  = new AtomicBoolean(false);
    private final LinkedHashMap<String, IdentifiableAuthorizationDecision> pending = new LinkedHashMap<>();
    private final ReentrantLock                                            lock    = new ReentrantLock();
    private final Condition                                                ready   = lock.newCondition();

    private Runnable closeAction = () -> {};
    private boolean  completed;

    void onClose(Runnable closeAction) {
        if (!closed.get()) {
            this.closeAction = closeAction;
        }
    }

    void put(IdentifiableAuthorizationDecision decision) {
        lock.lock();
        try {
            if (completed) {
                return;
            }
            pending.put(decision.subscriptionId(), decision);
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void complete() {
        lock.lock();
        try {
            completed = true;
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public IdentifiableAuthorizationDecision awaitNext() throws InterruptedException {
        lock.lock();
        try {
            while (pending.isEmpty() && !completed) {
                ready.await();
            }
            if (!pending.isEmpty()) {
                return removeFirstPending();
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Poll<IdentifiableAuthorizationDecision> tryNext() {
        lock.lock();
        try {
            if (!pending.isEmpty()) {
                return Poll.value(removeFirstPending());
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
            complete();
            closeAction.run();
        }
    }

    private IdentifiableAuthorizationDecision removeFirstPending() {
        val iterator = pending.entrySet().iterator();
        val next     = iterator.next();
        iterator.remove();
        return next.getValue();
    }
}
