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
package io.sapl.attributes.broker.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.DispatchCoalescer;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.AttributeAssignment.UnboundValue;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Publishes one complete, immutable tenant state per commit. Concurrent
 * producers deliver outside the repository lock. A reader can retain the
 * newest revision even when a slower producer delivers an older view later.
 * The TTL scheduler commits expiries. Each registration delivers expiry updates
 * on an independent worker, coalescing outstanding notifications into the
 * latest complete tenant view. A blocked observer cannot hold up another expiry.
 */
@Slf4j
public final class InMemoryAttributeRepository implements AttributeRepository {

    private static final String          ERROR_CLOSED        = "Repository is closed.";
    private static final String          ERROR_MIXED_TENANTS = "All attribute assignments must belong to one tenant.";
    private static final String          ERROR_OBSERVER      = "Repository state observer for tenant '{}' threw.";
    private static final RepositoryState EMPTY               = new RepositoryState(0, Map.of(), null);

    private final Object                                 lock      = new Object();
    private final Map<String, RepositoryState>           states    = new HashMap<>();
    private final Map<String, Set<StateObserver>>        observers = new HashMap<>();
    private final Map<RepositoryKey, ScheduledFuture<?>> timers    = new HashMap<>();
    private final ScheduledExecutorService               scheduler;
    private boolean                                      closed;

    public InMemoryAttributeRepository() {
        val ttlScheduler = new ScheduledThreadPoolExecutor(1,
                Thread.ofVirtual().name("InMemoryAttributeRepository-ttl").factory());
        ttlScheduler.setRemoveOnCancelPolicy(true);
        scheduler = ttlScheduler;
    }

    @Override
    public void publish(@NonNull List<AttributeAssignment> assignments) {
        val changes = List.copyOf(assignments);
        if (changes.isEmpty()) {
            return;
        }
        val pdpId            = changes.getFirst().key().pdpId();
        val finalAssignments = new LinkedHashMap<RepositoryKey, AttributeAssignment>();
        for (val assignment : changes) {
            if (!pdpId.equals(assignment.key().pdpId())) {
                throw new IllegalArgumentException(ERROR_MIXED_TENANTS);
            }
            finalAssignments.put(assignment.key(), assignment);
        }
        Delivery delivery;
        synchronized (lock) {
            if (closed) {
                return;
            }
            val before      = states.getOrDefault(pdpId, EMPTY);
            val revision    = before.revision() + 1;
            val bindings    = new HashMap<>(before.bindings());
            val publishedAt = System.nanoTime();
            for (val assignment : finalAssignments.values()) {
                val key = assignment.key();
                cancelTimer(key);
                switch (assignment) {
                case BoundValue(var ignored, var value, var ttl) -> {
                    val binding = new RepositoryState.Binding(revision, value);
                    bindings.put(key, binding);
                    if (ttl != null) {
                        scheduleExpiry(key, binding, publishedAt, ttl);
                    }
                }
                case UnboundValue(var ignored)                   -> bindings.remove(key);
                }
            }
            if (bindings.equals(before.bindings())) {
                return;
            }
            delivery = install(pdpId, new RepositoryState(revision, bindings, null));
        }
        delivery.fire();
    }

    @Override
    public StateRegistration observeState(@NonNull String pdpId, @NonNull String configurationId,
            @NonNull Consumer<RepositoryState> onUpdate) {
        synchronized (lock) {
            val initial  = closed ? new RepositoryState(0, Map.of(), Value.error(ERROR_CLOSED))
                    : states.getOrDefault(pdpId, EMPTY);
            val observer = new StateObserver(pdpId, onUpdate, initial);
            if (!closed) {
                observers.computeIfAbsent(pdpId, ignored -> new HashSet<>()).add(observer);
            }
            return observer;
        }
    }

    @Override
    public void close() {
        val terminal = new ArrayList<Delivery>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            observers.forEach((pdpId, bucket) -> {
                val revision    = states.getOrDefault(pdpId, EMPTY).revision() + 1;
                val unavailable = new RepositoryState(revision, Map.of(), Value.error(ERROR_CLOSED));
                terminal.add(new Delivery(unavailable, List.copyOf(bucket)));
            });
            observers.clear();
            timers.values().forEach(timer -> timer.cancel(false));
            timers.clear();
            states.clear();
        }
        scheduler.shutdownNow();
        terminal.forEach(Delivery::fire);
    }

    private void cancelTimer(RepositoryKey key) {
        val timer = timers.remove(key);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private void scheduleExpiry(RepositoryKey key, RepositoryState.Binding binding, long publishedAt, Duration ttl) {
        val elapsed   = System.nanoTime() - publishedAt;
        val remaining = Math.max(0L, TimeUnit.NANOSECONDS.convert(ttl) - elapsed);
        timers.put(key, scheduler.schedule(() -> expire(key, binding), remaining, TimeUnit.NANOSECONDS));
    }

    private void expire(RepositoryKey key, RepositoryState.Binding expected) {
        Delivery delivery;
        synchronized (lock) {
            val before = states.getOrDefault(key.pdpId(), EMPTY);
            if (before.bindings().get(key) != expected) {
                return;
            }
            timers.remove(key);
            val bindings = new HashMap<>(before.bindings());
            bindings.remove(key);
            delivery = install(key.pdpId(), new RepositoryState(before.revision() + 1, bindings, null));
        }
        delivery.observers().forEach(observer -> observer.expiries.requestFireAsync());
    }

    private Delivery install(String pdpId, RepositoryState state) {
        val interested = observers.getOrDefault(pdpId, Set.of());
        if (interested.isEmpty() && state.bindings().isEmpty()) {
            states.remove(pdpId);
        } else {
            states.put(pdpId, state);
        }
        return new Delivery(state, List.copyOf(interested));
    }

    private record Delivery(RepositoryState state, List<StateObserver> observers) {
        void fire() {
            observers.forEach(observer -> observer.deliver(state));
        }
    }

    private final class StateObserver implements StateRegistration {

        private final String                    pdpId;
        private final Consumer<RepositoryState> onUpdate;
        private final RepositoryState           initial;
        private final DispatchCoalescer         expiries = new DispatchCoalescer(this::deliverCurrent);
        private volatile boolean                observerClosed;

        StateObserver(String pdpId, Consumer<RepositoryState> onUpdate, RepositoryState initial) {
            this.pdpId    = pdpId;
            this.onUpdate = onUpdate;
            this.initial  = initial;
        }

        @Override
        public RepositoryState initialState() {
            return initial;
        }

        private void deliverCurrent() {
            RepositoryState current;
            synchronized (lock) {
                if (closed || observerClosed) {
                    return;
                }
                current = states.get(pdpId);
            }
            deliver(current);
        }

        void deliver(RepositoryState state) {
            if (!observerClosed) {
                try {
                    onUpdate.accept(state);
                } catch (RuntimeException failure) {
                    log.error(ERROR_OBSERVER, pdpId, failure);
                }
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                observerClosed = true;
                val bucket = observers.get(pdpId);
                if (bucket != null) {
                    bucket.remove(this);
                    if (bucket.isEmpty()) {
                        observers.remove(pdpId);
                        if (states.getOrDefault(pdpId, EMPTY).bindings().isEmpty()) {
                            states.remove(pdpId);
                        }
                    }
                }
            }
        }
    }
}
