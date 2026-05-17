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
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.ConsumerSubscriptionImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Internal per-invocation value supply for the catalog-backed broker.
 * Bridges a PIP-produced {@link Stream} into the mailbox that
 * consumers read. Owns one virtual-thread pump that drains the
 * source stream and republishes each value into the mailbox; tracks
 * the consumer refcount; supports atomic source rebind for hot-swap
 * so the mailbox value is preserved across the transition.
 */
@Slf4j
final class BackingSubscription {

    private static final String DEBUG_PUMP_THREW         = "Backing subscription {} pump threw: {}";
    private static final String DEBUG_STREAM_CLOSE_THREW = "Stream close threw: {}";
    private static final String WARN_ONVALUE_THREW       = "Backing subscription {} onValue handler threw: {}";

    private final String                    id = UUID.randomUUID().toString();
    private final AttributeFinderInvocation invocation;
    private final Consumer<Value>           onValue;

    // Subscriber index + refcount are guarded by the BROKER lock, not by this
    // BackingSubscription's internal lock. Walking only `subscriberRefs` keeps
    // dispatch O(consumers-of-this-backing), not O(all-broker-consumers).
    // The map is a multiset (count per consumer) so that a consumer routing
    // several keys (e.g., head=true and head=false) to this same backing is
    // accounted for exactly once in subscribers() but balanced correctly on
    // attach/detach against refcount.
    private final Map<ConsumerSubscriptionImpl, Integer> subscriberRefs = new HashMap<>();
    private int                                          refcount       = 0;

    private final Object                                 lock               = new Object();
    private Stream<Value>                                sourceStream;
    private Thread                                       pumpThread;
    private Optional<Value>                              latestValue        = Optional.empty();
    private Optional<Value>                              firstValue         = Optional.empty();
    private @Nullable StreamAttributeFinderSpecification sourceSpec;
    private boolean                                      closed             = false;
    private boolean                                      inRebindTransition = false;

    /**
     * @param invocation the invocation this backing serves
     * @param sourceStream the initial source stream; may be
     * {@code null} for terminal backings (no matching PIP)
     * @param sourceSpec the catalog spec that produced
     * {@code sourceStream}; {@code null} for terminal backings, used
     * by the broker on hot-swap to identify which backings serve which
     * specs
     * @param onValue callback invoked on each new value emitted by
     * the source stream; the broker wires this to its dispatch path
     */
    BackingSubscription(AttributeFinderInvocation invocation,
            @Nullable Stream<Value> sourceStream,
            @Nullable StreamAttributeFinderSpecification sourceSpec,
            Consumer<Value> onValue) {
        this.invocation   = invocation;
        this.sourceStream = sourceStream;
        this.sourceSpec   = sourceSpec;
        this.onValue      = onValue;
    }

    String id() {
        return id;
    }

    AttributeFinderInvocation invocation() {
        return invocation;
    }

    @Nullable
    StreamAttributeFinderSpecification sourceSpec() {
        return sourceSpec;
    }

    /**
     * @param head if {@code true}, returns the first-emitted value
     * (frozen view); if {@code false}, returns the latest-emitted
     * value
     */
    Optional<Value> snapshot(boolean head) {
        synchronized (lock) {
            return head ? firstValue : latestValue;
        }
    }

    /** {@code true} once at least one value has been published. */
    boolean hasValue() {
        synchronized (lock) {
            return latestValue.isPresent();
        }
    }

    /**
     * Registers a consumer-side attach. Returns the new total refcount.
     * Caller must hold the broker lock.
     */
    int attach(ConsumerSubscriptionImpl subscriber) {
        subscriberRefs.merge(subscriber, 1, Integer::sum);
        refcount++;
        return refcount;
    }

    /**
     * Registers a consumer-side detach. Returns the new total refcount.
     * If the consumer's per-backing count drops to zero, the consumer
     * is removed from the subscriber index (no longer eligible for
     * dispatch). Caller must hold the broker lock.
     */
    int detach(ConsumerSubscriptionImpl subscriber) {
        val current = subscriberRefs.get(subscriber);
        if (current == null) {
            return refcount;
        }
        if (current == 1) {
            subscriberRefs.remove(subscriber);
        } else {
            subscriberRefs.put(subscriber, current - 1);
        }
        refcount--;
        return refcount;
    }

    /**
     * Returns the current consumer set for dispatch. Caller must hold
     * the broker lock; the returned view aliases the underlying map's
     * keySet and is invalidated by concurrent attach/detach.
     */
    Set<ConsumerSubscriptionImpl> subscribers() {
        return subscriberRefs.keySet();
    }

    int refcount() {
        return refcount;
    }

    /**
     * Starts the pump thread on the current source stream. No-op if
     * there is no source stream (terminal backing) or if already
     * started or closed.
     */
    void start() {
        synchronized (lock) {
            if (closed || sourceStream == null || pumpThread != null) {
                return;
            }
            pumpThread = Thread.startVirtualThread(this::pumpLoop);
        }
    }

    /**
     * Publishes a value directly into the mailbox without involving a
     * source stream. Used for terminal-state backings: an invocation
     * with no matching PIP publishes {@link Value#UNDEFINED} at open
     * time, and the catalog publishes a terminal value
     * ({@link Value#UNDEFINED} on unload/swap-eviction, an ErrorValue
     * on rebind failure) before discarding the backing.
     * <p>
     * Bypasses the rebind-transition gate (see {@link #rebind}): the
     * caller has explicitly chosen this value as terminal.
     */
    void publishImmediate(Value value) {
        publishInternal(value, false);
    }

    /**
     * Atomic source rebind for hot-swap. Replaces the current source
     * stream with a fresh one without clearing the mailbox: consumers
     * keep observing the prior value until the new stream emits its
     * first value. Closes the old stream so its pump exits naturally.
     * <p>
     * Marks the backing as in a rebind transition: pump-path
     * publishes of {@link Value#UNDEFINED} are suppressed until the
     * new stream emits a non-UNDEFINED value. This prevents transient
     * absence jitter during a hot-swap when the replacement stream's
     * initial-value timeout would otherwise propagate UNDEFINED to
     * consumers that were observing a real prior value. Terminal
     * UNDEFINED (via {@link #publishImmediate}) is unaffected.
     *
     * @param newSourceStream the freshly opened replacement stream
     * @param newSourceSpec the catalog spec that produced the
     * replacement stream
     */
    void rebind(Stream<Value> newSourceStream, StreamAttributeFinderSpecification newSourceSpec) {
        Stream<Value> oldStream;
        synchronized (lock) {
            if (closed) {
                if (newSourceStream != null) {
                    safeClose(newSourceStream);
                }
                return;
            }
            oldStream          = sourceStream;
            sourceStream       = newSourceStream;
            sourceSpec         = newSourceSpec;
            pumpThread         = null;
            inRebindTransition = true;
        }
        if (oldStream != null) {
            safeClose(oldStream);
        }
        start();
    }

    /**
     * Idempotent. Releases the source stream and signals the pump
     * thread to exit. Mailbox state is retained for any consumer
     * still inspecting the snapshot, but the backing should be
     * discarded by the broker after this call.
     */
    void close() {
        Stream<Value> toClose;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed       = true;
            toClose      = sourceStream;
            sourceStream = null;
        }
        if (toClose != null) {
            safeClose(toClose);
        }
    }

    boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    private void pumpLoop() {
        while (true) {
            Stream<Value> current;
            synchronized (lock) {
                current = sourceStream;
                if (closed || current == null) {
                    return;
                }
            }
            Value next;
            try {
                next = current.awaitNext();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.debug(DEBUG_PUMP_THREW, id, e.getMessage());
                return;
            }
            if (next == null) {
                return;
            }
            publishInternal(next, true);
        }
    }

    /**
     * @param value the value to publish
     * @param gatedByRebind {@code true} when this publish flows from
     * the source-stream pump (subject to the rebind-transition gate);
     * {@code false} for immediate / terminal publishes that bypass
     * the gate
     */
    private void publishInternal(Value value, boolean gatedByRebind) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (gatedByRebind && inRebindTransition && Value.UNDEFINED.equals(value)) {
                return;
            }
            inRebindTransition = false;
            if (firstValue.isEmpty()) {
                firstValue = Optional.of(value);
            }
            latestValue = Optional.of(value);
        }
        try {
            onValue.accept(value);
        } catch (RuntimeException e) {
            log.warn(WARN_ONVALUE_THREW, id, e.getMessage(), e);
        }
    }

    private static void safeClose(Stream<Value> stream) {
        try {
            stream.close();
        } catch (RuntimeException e) {
            log.debug(DEBUG_STREAM_CLOSE_THREW, e.getMessage());
        }
    }
}
