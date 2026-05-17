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

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory {@link AttributeRepository}. Producers publish values
 * keyed by {@link RepositoryKey}; observers register a per-invocation
 * listener via {@link #observe} and receive the current value plus
 * every subsequent change.
 * <p>
 * State is volatile: lost on close. TTL expiry removes the entry and
 * notifies observers (who observe {@link Value#UNDEFINED}).
 * <p>
 * Thread-safety: all state mutations occur under a single internal
 * lock; observer callbacks fire outside the lock.
 *
 * @since 4.1.0
 */
@Slf4j
public final class InMemoryAttributeRepository implements AttributeRepository {

    private static final String ERROR_CLOSED           = "Repository is closed.";
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";
    private static final String WARN_OBSERVER_THREW    = "Observer {} threw: {}.";

    private final ScheduledExecutorService scheduler;

    private final Object                               lock           = new Object();
    private final Map<RepositoryKey, Entry>            entries        = new HashMap<>();
    private final Map<RepositoryKey, Set<KeyObserver>> observersByKey = new HashMap<>();

    private boolean closed = false;

    public InMemoryAttributeRepository() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            val thread = Thread.ofVirtual().unstarted(runnable);
            thread.setName("InMemoryAttributeRepository-ttl");
            return thread;
        });
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        publishInternal(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        publishInternal(key, value, ttl);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        List<KeyObserver> toFire;
        synchronized (lock) {
            if (closed) {
                return;
            }
            val prior = entries.remove(key);
            if (prior == null) {
                return;
            }
            if (prior.expiryTask != null) {
                prior.expiryTask.cancel(false);
            }
            toFire = observers(key);
        }
        fireObservers(toFire, Value.UNDEFINED);
    }

    @Override
    public AttributeRepository.Registration observe(@NonNull AttributeFinderInvocation invocation,
            @NonNull Consumer<Value> onValue) {
        val   repoKey  = RepositoryKey.fromInvocation(invocation);
        val   observer = new KeyObserver(repoKey, onValue);
        Value initial;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException(ERROR_CLOSED);
            }
            observersByKey.computeIfAbsent(repoKey, k -> new HashSet<>()).add(observer);
            val entry = entries.get(repoKey);
            initial = entry != null ? entry.value : Value.UNDEFINED;
        }
        observer.deliver(initial);
        return observer;
    }

    @Override
    public void close() {
        Collection<Entry> toCancel;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed   = true;
            toCancel = new ArrayList<>(entries.values());
            // Mark every observer closed so in-flight fires (already past the
            // observers gate, about to call deliver) become no-ops.
            for (val bucket : observersByKey.values()) {
                for (val observer : bucket) {
                    observer.closed = true;
                }
            }
            entries.clear();
            observersByKey.clear();
        }
        for (val e : toCancel) {
            if (e.expiryTask != null) {
                e.expiryTask.cancel(false);
            }
        }
        scheduler.shutdownNow();
    }

    private void publishInternal(RepositoryKey key, Value value, @Nullable Duration ttl) {
        List<KeyObserver> toFire;
        synchronized (lock) {
            if (closed) {
                return;
            }
            val prior = entries.get(key);
            if (prior != null && prior.expiryTask != null) {
                prior.expiryTask.cancel(false);
            }
            val entry = new Entry(value);
            entries.put(key, entry);
            if (ttl != null) {
                entry.expiryTask = scheduler.schedule(() -> expireKey(key, entry), ttl.toMillis(),
                        TimeUnit.MILLISECONDS);
            }
            toFire = observers(key);
        }
        fireObservers(toFire, value);
    }

    private void expireKey(RepositoryKey key, Entry expectedEntry) {
        List<KeyObserver> toFire;
        synchronized (lock) {
            val current = entries.get(key);
            if (current != expectedEntry) {
                return;
            }
            entries.remove(key);
            toFire = observers(key);
        }
        fireObservers(toFire, Value.UNDEFINED);
    }

    /** Caller holds the lock. */
    private List<KeyObserver> observers(RepositoryKey repositoryKey) {
        val bucket = observersByKey.get(repositoryKey);
        return bucket == null ? List.of() : new ArrayList<>(bucket);
    }

    private void fireObservers(List<KeyObserver> observers, Value value) {
        for (val observer : observers) {
            observer.deliver(value);
        }
    }

    /** Caller holds the lock. */
    private void unregisterObserver(KeyObserver observer) {
        val bucket = observersByKey.get(observer.repositoryKey);
        if (bucket == null) {
            return;
        }
        bucket.remove(observer);
        if (bucket.isEmpty()) {
            observersByKey.remove(observer.repositoryKey);
        }
    }

    /**
     * Per-entry storage. {@code expiryTask} is settable so the
     * publisher can install it after constructing the entry record
     * that the task closure captures.
     */
    private static final class Entry {
        final Value        value;
        @Nullable
        ScheduledFuture<?> expiryTask;

        Entry(Value value) {
            this.value = value;
        }
    }

    /**
     * Single-key observer registered via {@link #observe}. The
     * repository indexes observers in {@link #observersByKey} and
     * fires them on publish, expire and remove.
     */
    @RequiredArgsConstructor
    private final class KeyObserver implements AttributeRepository.Registration {

        private static final AtomicLong NEXT_ID = new AtomicLong(Long.MIN_VALUE);

        private final long            id     = NEXT_ID.getAndIncrement();
        final RepositoryKey           repositoryKey;
        private final Consumer<Value> onValue;
        private volatile boolean      closed = false;

        void deliver(Value value) {
            if (closed) {
                return;
            }
            try {
                onValue.accept(value);
            } catch (RuntimeException e) {
                log.warn(WARN_OBSERVER_THREW, id, e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                unregisterObserver(this);
            }
        }
    }
}
