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
package io.sapl.attributes.broker;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Projects complete source views onto a serialized single-key observer. */
@Slf4j
@RequiredArgsConstructor
final class RepositoryValueProjection implements Consumer<RepositoryState> {

    private static final String ERROR_OBSERVER = "Repository value observer for '{}' threw.";

    private final RepositoryKey                    key;
    private final Consumer<Value>                  onValue;
    private final AtomicReference<RepositoryState> pending       = new AtomicReference<>();
    private final DispatchCoalescer                coalescer     = new DispatchCoalescer(this::deliverLatest);
    private RepositoryState.@Nullable Presence     previousKind;
    private RepositoryState.@Nullable Binding      previous;
    private long                                   lastDelivered = Long.MIN_VALUE;
    private volatile boolean                       started;
    private volatile boolean                       closed;

    void start(RepositoryState initial) {
        accept(initial);
        // This thread owns initial delivery until started is visible to publishers.
        deliverLatest();
        started = true;
        coalescer.requestFire();
    }

    @Override
    public void accept(RepositoryState state) {
        pending.accumulateAndGet(state,
                (prior, next) -> prior == null || next.revision() > prior.revision() ? next : prior);
        if (started) {
            coalescer.requestFire();
        }
    }

    void close() {
        closed = true;
    }

    private void deliverLatest() {
        val state = pending.getAndSet(null);
        if (closed || state == null || state.revision() <= lastDelivered) {
            return;
        }
        lastDelivered = state.revision();
        val binding = state.binding(key);
        val kind    = state.presence(key);
        if (kind != previousKind || !binding.equals(previous)) {
            previous     = binding;
            previousKind = kind;
            try {
                onValue.accept(binding.value());
            } catch (RuntimeException failure) {
                log.error(ERROR_OBSERVER, key.name(), failure);
            }
        }
    }
}
