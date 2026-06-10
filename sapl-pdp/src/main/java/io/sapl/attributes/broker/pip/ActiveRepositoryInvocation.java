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

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.BrokerSubscription;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Active invocation fed by the broker's fallback repository, used
 * when no PIP in the catalog matches the invocation. Wraps an
 * {@link AttributeRepository#observe} registration on the fallback
 * (typically the
 * {@link io.sapl.attributes.broker.repository.InMemoryAttributeRepository})
 * and exposes the same {@link ActiveInvocation} contract as
 * {@link ActivePolicyInformationPointInvocation}, so the PIP broker
 * dispatches and tracks refcount uniformly across both kinds.
 * <p>
 * If a PIP later becomes available for this invocation (catalog
 * load or swap), the broker migrates: close this active invocation
 * and replace it with an {@link ActivePolicyInformationPointInvocation}
 * fed by the PIP. Migration is handled by the broker; this class
 * itself is static during its lifetime.
 */
@Slf4j
final class ActiveRepositoryInvocation implements ActiveInvocation {

    private static final String DEBUG_CLOSED               = "Active repository invocation {} closed";
    private static final String DEBUG_FALLBACK_CLOSE_THREW = "Active repository invocation {} fallback close threw: {}";
    private static final String DEBUG_OPENED               = "Active repository invocation {} opened for '{}'";
    private static final String WARN_ONVALUE_THREW         = "Active repository invocation {} onValue handler threw: {}";

    private static final AtomicLong NEXT_ID = new AtomicLong(Long.MIN_VALUE);

    private final long                      id = NEXT_ID.getAndIncrement();
    private final AttributeFinderInvocation invocation;
    private final AttributeRepository       fallback;
    private final Consumer<Value>           onValue;

    // The broker lock guards subscriberRefs + refcount; the AtomicInteger here
    // is only to silence SonarQube's atomicity check on increment / decrement.
    private final Map<BrokerSubscription, Integer> subscriberRefs = new HashMap<>();
    private final AtomicInteger                    refcount       = new AtomicInteger();

    private final Object                               lock        = new Object();
    private AttributeRepository.@Nullable Registration handle      = null;
    private volatile Optional<Value>                   latestValue = Optional.empty();
    private volatile boolean                           closed      = false;

    /**
     * @param invocation the normalized invocation this active
     * invocation serves
     * @param fallback the repository this active invocation observes
     * @param onValue dispatched on every new value from the fallback
     */
    ActiveRepositoryInvocation(AttributeFinderInvocation invocation,
            AttributeRepository fallback,
            Consumer<Value> onValue) {
        this.invocation = invocation;
        this.fallback   = fallback;
        this.onValue    = onValue;
        log.debug(DEBUG_OPENED, id, invocation.attributeName());
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
    public Optional<Value> snapshot() {
        return latestValue;
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
        return closed;
    }

    /**
     * Registers an observation on the fallback. The fallback delivers
     * the current value synchronously, which {@link #onUpdate} stores
     * in the mailbox and dispatches via {@code onValue}. Idempotent.
     */
    @Override
    public void start() {
        synchronized (lock) {
            if (closed || handle != null) {
                return;
            }
        }
        val newHandle = fallback.observe(invocation, this::onUpdate);
        synchronized (lock) {
            if (closed) {
                safeClose(newHandle);
                return;
            }
            handle = newHandle;
        }
    }

    @Override
    public void close() {
        AttributeRepository.Registration toClose;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed  = true;
            toClose = handle;
            handle  = null;
        }
        if (toClose != null) {
            safeClose(toClose);
        }
        log.debug(DEBUG_CLOSED, id);
    }

    private void onUpdate(Value value) {
        if (closed) {
            return;
        }
        latestValue = Optional.of(value);
        try {
            onValue.accept(value);
        } catch (RuntimeException e) {
            log.warn(WARN_ONVALUE_THREW, id, e.getMessage(), e);
        }
    }

    private void safeClose(AttributeRepository.Registration registration) {
        try {
            registration.close();
        } catch (RuntimeException e) {
            log.debug(DEBUG_FALLBACK_CLOSE_THREW, id, e.getMessage());
        }
    }
}
