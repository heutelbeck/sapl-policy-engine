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

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.DispatchCoalescer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * In-memory {@link AttributeBroker} that doubles as an
 * {@link AttributeRepository}. Producers push values via
 * {@link #publish(RepositoryKey, Value)} and friends; consumers
 * subscribe via {@link #open(String, Set, Function)} and observe
 * the published values (or {@link Value#UNDEFINED} for any key that
 * has no entry).
 * <p>
 * Designed as the fallback half of a
 * {@code LayeredAttributeBroker(catalog, repository)}: PIPs in the
 * catalog-backed broker win; when a PIP is absent or unloaded the
 * layered broker falls through to this repository.
 * <p>
 * State is volatile: lost on close. TTL expiry removes the entry
 * and notifies subscribers (who observe {@link Value#UNDEFINED}).
 * <p>
 * Thread-safety: all state mutations occur under a single internal
 * lock; consumer callbacks fire outside the broker lock and are
 * serialized per-consumer by an internal callback lock.
 *
 * @since 4.1.0
 */
@Slf4j
public final class InMemoryAttributeRepository implements AttributeBroker, AttributeRepository {

    private static final String ERROR_DEPS_EMPTY             = "initialDependencies must not be empty";
    private static final String ERROR_RETURNED_DEPS_INVALID  = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK  = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE = "subscriptionId already open: %s";
    private static final String ERROR_TTL_NOT_POSITIVE       = "ttl must be a strictly positive Duration";
    private static final String WARN_CALLBACK_THREW          = "Consumer {} onUpdate threw: {}";

    private final Clock                    clock;
    private final ScheduledExecutorService scheduler;

    private final Object                                lock      = new Object();
    private final Map<RepositoryKey, Entry>             entries   = new HashMap<>();
    private final Map<String, ConsumerSubscriptionImpl> consumers = new HashMap<>();
    private boolean                                     closed    = false;

    public InMemoryAttributeRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryAttributeRepository(@NonNull Clock clock) {
        this.clock     = clock;
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
        List<ConsumerSubscriptionImpl> toFire;
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
            toFire = findAffectedConsumers(key);
        }
        fireAll(toFire);
    }

    @Override
    public Subscription open(@NonNull String subscriptionId, @NonNull Set<SubscriptionKey> initialDependencies,
            @NonNull Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        if (subscriptionId.isBlank()) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_BLANK);
        }
        if (initialDependencies.isEmpty()) {
            throw new IllegalArgumentException(ERROR_DEPS_EMPTY);
        }

        ConsumerSubscriptionImpl consumer;
        synchronized (lock) {
            if (consumers.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            consumer = new ConsumerSubscriptionImpl(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            captureHeadValues(consumer, initialDependencies);
            consumers.put(subscriptionId, consumer);
        }
        // The gate is trivially open: every key has a value (real entry
        // or UNDEFINED) at all times in this broker. Fire synchronously.
        consumer.fireCallback();
        return consumer;
    }

    @Override
    public void close() {
        List<ConsumerSubscriptionImpl> toClose;
        Collection<Entry>              toCancel;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed   = true;
            toClose  = new ArrayList<>(consumers.values());
            toCancel = new ArrayList<>(entries.values());
            consumers.clear();
            entries.clear();
        }
        for (val c : toClose) {
            c.markClosed();
        }
        for (val e : toCancel) {
            if (e.expiryTask != null) {
                e.expiryTask.cancel(false);
            }
        }
        scheduler.shutdownNow();
    }

    private void publishInternal(RepositoryKey key, Value value, @Nullable Duration ttl) {
        List<ConsumerSubscriptionImpl> toFire;
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
            toFire = findAffectedConsumers(key);
        }
        fireAll(toFire);
    }

    private void expireKey(RepositoryKey key, Entry expectedEntry) {
        List<ConsumerSubscriptionImpl> toFire;
        synchronized (lock) {
            val current = entries.get(key);
            if (current != expectedEntry) {
                return;
            }
            entries.remove(key);
            toFire = findAffectedConsumers(key);
        }
        fireAll(toFire);
    }

    /**
     * Caller holds the broker lock.
     */
    private void captureHeadValues(ConsumerSubscriptionImpl consumer, Set<SubscriptionKey> deps) {
        for (val dep : deps) {
            if (dep.head()) {
                consumer.capturedHeadValues.put(dep, currentValueLocked(dep));
            }
        }
    }

    /**
     * Caller holds the broker lock.
     */
    private Value currentValueLocked(SubscriptionKey dep) {
        val repositoryKey = RepositoryKey.fromInvocation(dep.invocation());
        val entry         = entries.get(repositoryKey);
        return entry != null ? entry.value : Value.UNDEFINED;
    }

    /**
     * Caller holds the broker lock. Returns consumers that hold at
     * least one head=false dependency whose invocation projects onto
     * {@code repositoryKey}.
     */
    private List<ConsumerSubscriptionImpl> findAffectedConsumers(RepositoryKey repositoryKey) {
        val affected = new ArrayList<ConsumerSubscriptionImpl>();
        for (val consumer : consumers.values()) {
            for (val dep : consumer.deps) {
                if (!dep.head() && keyMatches(dep, repositoryKey)) {
                    affected.add(consumer);
                    break;
                }
            }
        }
        return affected;
    }

    private static boolean keyMatches(SubscriptionKey dep, RepositoryKey repositoryKey) {
        val invocation = dep.invocation();
        return repositoryKey.name().equals(invocation.attributeName())
                && Objects.equals(repositoryKey.entity(), invocation.entity())
                && repositoryKey.arguments().equals(invocation.arguments());
    }

    private void fireAll(List<ConsumerSubscriptionImpl> consumers) {
        for (val consumer : consumers) {
            consumer.fireCallback();
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

    private final class ConsumerSubscriptionImpl implements Subscription {

        private final String                                                                  id;
        private final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        private final DispatchCoalescer                                                       coalescer;
        private final Map<SubscriptionKey, Value>                                             capturedHeadValues = new HashMap<>();
        private Set<SubscriptionKey>                                                          deps;
        private boolean                                                                       closed             = false;

        ConsumerSubscriptionImpl(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id        = id;
            this.deps      = deps;
            this.onUpdate  = onUpdate;
            this.coalescer = new DispatchCoalescer(this::runOneFire);
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                consumers.remove(id);
            }
        }

        void markClosed() {
            synchronized (lock) {
                closed = true;
            }
        }

        void fireCallback() {
            coalescer.requestFire();
        }

        /**
         * Body of a single coalesced fire. Reads the current snapshot
         * under the broker lock, invokes the consumer callback outside
         * the lock, and applies the returned dep diff. See
         * {@link DispatchCoalescer} for the surrounding flag dance.
         */
        private void runOneFire() {
            Map<SubscriptionKey, AttributeSnapshot>                                 snapshot;
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> callback;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                snapshot = currentSnapshot();
                callback = onUpdate;
            }
            Set<SubscriptionKey> newDeps;
            try {
                newDeps = callback.apply(snapshot);
            } catch (RuntimeException e) {
                log.warn(WARN_CALLBACK_THREW, id, e.getMessage(), e);
                return;
            }
            if (newDeps == null || newDeps.isEmpty()) {
                throw new IllegalStateException(ERROR_RETURNED_DEPS_INVALID.formatted(id));
            }
            applyDepDiff(newDeps);
        }

        /**
         * Caller holds the broker lock.
         */
        private Map<SubscriptionKey, AttributeSnapshot> currentSnapshot() {
            val now      = clock.instant();
            val snapshot = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(deps.size());
            for (val dep : deps) {
                Value value;
                if (dep.head()) {
                    value = capturedHeadValues.getOrDefault(dep, Value.UNDEFINED);
                } else {
                    value = currentValueLocked(dep);
                }
                snapshot.put(dep, new AttributeSnapshot(value, now));
            }
            return Map.copyOf(snapshot);
        }

        private void applyDepDiff(Set<SubscriptionKey> newDeps) {
            boolean refire;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                if (newDeps.equals(deps)) {
                    return;
                }
                val added = new HashSet<>(newDeps);
                added.removeAll(deps);
                val removed = new HashSet<>(deps);
                removed.removeAll(newDeps);

                for (val dep : added) {
                    if (dep.head()) {
                        capturedHeadValues.put(dep, currentValueLocked(dep));
                    }
                }
                for (val dep : removed) {
                    capturedHeadValues.remove(dep);
                }
                deps   = new HashSet<>(newDeps);
                refire = !added.isEmpty();
            }
            if (refire) {
                fireCallback();
            }
        }
    }

}
