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
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.BrokerSubscription;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Active invocation fed by the broker's fallback repository, used when no PIP
 * in the catalog matches the invocation.
 * Wraps an {@link AttributeRepository#observe} registration on the fallback
 * (typically the
 * {@link io.sapl.attributes.broker.repository.InMemoryAttributeRepository}) and
 * exposes the same
 * {@link ActiveInvocation} contract as
 * {@link ActivePolicyInformationPointInvocation}, so the PIP broker dispatches
 * and
 * tracks refcount uniformly across both kinds.
 * <p>
 * If a PIP later becomes available for this invocation (catalog load or swap),
 * the broker migrates: close this active
 * invocation and replace it with an
 * {@link ActivePolicyInformationPointInvocation} fed by the PIP. Migration is
 * handled
 * by the broker. This class itself is static during its lifetime.
 */
@Slf4j
final class ActiveRepositoryInvocation implements ActiveInvocation {

    private static final String DEBUG_CLOSED               = "Active repository invocation {} closed";
    private static final String DEBUG_FALLBACK_CLOSE_THREW = "Active repository invocation {} fallback close threw: {}";
    private static final String DEBUG_OPENED               = "Active repository invocation {} opened for '{}'";
    private static final String ERROR_ONVALUE_THREW        = "Active repository invocation {} onValue handler threw (engine invariant: it must never throw): {}";

    private static final long WARN_LOG_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private static final AtomicLong NEXT_ID = new AtomicLong(Long.MIN_VALUE);

    private final long                      id = NEXT_ID.getAndIncrement();
    private final AttributeFinderInvocation invocation;
    private final AttributeRepository       fallback;
    private final InstantSource             timestampSource;
    private final Consumer<Value>           onValue;

    // Broker lock guards both subscriberRefs and refcount.
    private final Map<BrokerSubscription, Integer> subscriberRefs = new HashMap<>();
    private final AtomicInteger                    refcount       = new AtomicInteger();

    private final Object                               lock           = new Object();
    private AttributeRepository.@Nullable Registration handle         = null;
    private volatile Optional<AttributeSnapshot>       latestSnapshot = Optional.empty();
    private volatile boolean                           closed         = false;

    // Rate-limits the onValue-handler-threw warning to one per minute.
    private long    lastWarnLogNanos;
    private boolean warnLogged;

    /**
     * @param invocation
     * the normalized invocation this active invocation serves
     * @param fallback
     * the repository this active invocation observes
     * @param timestampSource
     * source for value-arrival timestamps
     * @param onValue
     * dispatched on every new value from the fallback
     */
    ActiveRepositoryInvocation(AttributeFinderInvocation invocation,
            AttributeRepository fallback,
            InstantSource timestampSource,
            Consumer<Value> onValue) {
        this.invocation      = invocation;
        this.fallback        = fallback;
        this.timestampSource = timestampSource;
        this.onValue         = onValue;
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
        return closed;
    }

    /**
     * Registers an observation on the fallback. The fallback delivers the current
     * value synchronously, which
     * {@link #onUpdate} stores in the mailbox and dispatches via {@code onValue}.
     * Idempotent.
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
        latestSnapshot = Optional.of(new AttributeSnapshot(value, timestampSource.instant()));
        try {
            onValue.accept(value);
        } catch (RuntimeException e) {
            logHandlerFailure(e);
        }
    }

    private void logHandlerFailure(RuntimeException failure) {
        val now = System.nanoTime();
        if (!warnLogged || now - lastWarnLogNanos >= WARN_LOG_INTERVAL_NANOS) {
            log.error(ERROR_ONVALUE_THREW, id, failure.getMessage(), failure);
            lastWarnLogNanos = now;
            warnLogged       = true;
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
