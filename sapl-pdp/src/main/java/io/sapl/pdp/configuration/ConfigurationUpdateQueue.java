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
package io.sapl.pdp.configuration;

import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Serializes transitions under the source lock and defers nested changes to their requesting thread. */
@Slf4j
final class ConfigurationUpdateQueue {

    private static final String ERROR_UPDATE_ABORTED        = "PDP configuration update aborted while attempting to {}. Discarding {} queued changes.";
    private static final String WARN_DEFERRED_UPDATE_FAILED = "Deferred PDP configuration update failed while attempting to {}.";

    private final ReentrantLock                 stateLock;
    private @Nullable ArrayDeque<PendingUpdate> active;

    ConfigurationUpdateQueue(ReentrantLock stateLock) {
        this.stateLock = stateLock;
    }

    @FunctionalInterface
    interface Step {
        /** @return the next migration step, or null when the update is complete */
        @Nullable
        Step run();
    }

    private record PendingUpdate(Supplier<String> description, Step step, boolean deferred) {}

    void execute(Supplier<String> description, Runnable action) {
        executeSteps(description, () -> {
            action.run();
            return null;
        });
    }

    void executeSteps(Supplier<String> description, Step step) {
        if (stateLock.isHeldByCurrentThread()) {
            val current = active;
            if (current != null) {
                current.addLast(new PendingUpdate(description, step, true));
                return;
            }
        }
        val              local   = new ArrayDeque<PendingUpdate>();
        @Nullable
        RuntimeException failure = runStep(new PendingUpdate(description, step, false), local);
        while (!local.isEmpty()) {
            val nextFailure = runStep(local.removeFirst(), local);
            if (nextFailure != null) {
                if (failure == null) {
                    failure = nextFailure;
                } else if (failure != nextFailure) {
                    failure.addSuppressed(nextFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    boolean isProcessingOnCurrentThread() {
        return stateLock.isHeldByCurrentThread() && active != null;
    }

    private @Nullable RuntimeException runStep(PendingUpdate update, ArrayDeque<PendingUpdate> local) {
        stateLock.lock();
        try {
            active = local;
            val continuation = update.step().run();
            if (continuation != null) {
                local.addLast(new PendingUpdate(update.description(), continuation, update.deferred()));
            }
            return null;
        } catch (RuntimeException failure) {
            if (update.deferred()) {
                log.warn(WARN_DEFERRED_UPDATE_FAILED, update.description().get(), failure);
                return null;
            }
            return failure;
        } catch (Error failure) {
            log.error(ERROR_UPDATE_ABORTED, update.description().get(), local.size(), failure);
            throw failure;
        } finally {
            active = null;
            stateLock.unlock();
        }
    }
}
