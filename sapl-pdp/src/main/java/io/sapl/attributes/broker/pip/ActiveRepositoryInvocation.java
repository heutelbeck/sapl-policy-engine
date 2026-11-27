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
package io.sapl.attributes.broker.pip;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.BrokerSubscription;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import lombok.val;

/**
 * A repository key projected from its broker's shared, routed repository state.
 * The broker owns registration, revision ordering and atomic installation of all
 * active keys in the route. This projection must not request an independent
 * latest value or notify a consumer before the other projections are installed.
 * <p>
 * Installation and capture use the broker routing lock. A changed binding keeps
 * one new observation instance until its next change. An unchanged binding keeps
 * the old instance, so a change to another repository key does not become a false
 * update here. Confirmed absence and source errors are fulfilled observations.
 */
final class ActiveRepositoryInvocation implements ActiveInvocation {

    private static final AtomicLong NEXT_ID = new AtomicLong(Long.MIN_VALUE);

    private final long                                  id             = NEXT_ID.getAndIncrement();
    private final AttributeFinderInvocation             invocation;
    private final RepositoryKey                         key;
    private final PolicyInformationPointAttributeBroker broker;
    private final Map<BrokerSubscription, Integer>      subscriberRefs = new HashMap<>();
    private final AtomicInteger                         refcount       = new AtomicInteger();
    private final AtomicBoolean                         closed         = new AtomicBoolean();

    // Installed and read under the broker's routing lock.
    private @Nullable RuntimeException         registrationFailure;
    private RepositoryState.@Nullable Presence previousKind;
    private RepositoryState.@Nullable Binding  binding;
    private Optional<AttributeSnapshot>        latestSnapshot = Optional.empty();

    ActiveRepositoryInvocation(AttributeFinderInvocation invocation, PolicyInformationPointAttributeBroker broker) {
        this.invocation = invocation;
        this.key        = RepositoryKey.fromInvocation(invocation);
        this.broker     = broker;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public AttributeFinderInvocation invocation() {
        return invocation;
    }

    @Override
    public @Nullable StreamAttributeFinderSpecification sourceSpec() {
        return null;
    }

    @Override
    public Optional<AttributeSnapshot> snapshot() {
        return latestSnapshot;
    }

    @Override
    public int attach(BrokerSubscription subscriber) {
        subscriberRefs.merge(subscriber, 1, Integer::sum);
        return refcount.incrementAndGet();
    }

    @Override
    public int detach(BrokerSubscription subscriber) {
        val current = subscriberRefs.get(subscriber);
        if (current == null) {
            return refcount.get();
        }
        if (current == 1) {
            subscriberRefs.remove(subscriber);
        } else {
            subscriberRefs.put(subscriber, current - 1);
        }
        return refcount.decrementAndGet();
    }

    @Override
    public Set<BrokerSubscription> subscribers() {
        return subscriberRefs.keySet();
    }

    @Override
    public int refcount() {
        return refcount.get();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void start() {
        broker.startRepositoryInvocation(this);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            broker.stopRepositoryInvocation(this);
        }
    }

    void retire(RuntimeException failure) {
        registrationFailure = failure;
    }

    void checkAdmission() {
        if (registrationFailure != null) {
            throw registrationFailure;
        }
    }

    boolean install(RepositoryState state, Instant observedAt) {
        val next = state.binding(key);
        val kind = state.presence(key);
        if (kind == previousKind && next.equals(binding)) {
            return false;
        }
        binding        = next;
        previousKind   = kind;
        latestSnapshot = Optional.of(new AttributeSnapshot(next.value(), observedAt));
        return true;
    }
}
