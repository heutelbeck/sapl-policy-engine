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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final String                    id       = UUID.randomUUID().toString();
    private final AttributeFinderInvocation invocation;
    private final boolean                   shared;
    private final Consumer<Value>           onValue;
    private final AtomicInteger             refcount = new AtomicInteger();

    private final Object                                 lock        = new Object();
    private Stream<Value>                                sourceStream;
    private Thread                                       pumpThread;
    private Optional<Value>                              latestValue = Optional.empty();
    private Optional<Value>                              firstValue  = Optional.empty();
    private @Nullable StreamAttributeFinderSpecification sourceSpec;
    private boolean                                      closed      = false;

    /**
     * @param invocation the invocation this backing serves
     * @param shared {@code true} when registered in the broker's
     * shared-dedup map; {@code false} when private to one consumer
     * (created via {@code fresh=true})
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
            boolean shared,
            @Nullable Stream<Value> sourceStream,
            @Nullable StreamAttributeFinderSpecification sourceSpec,
            Consumer<Value> onValue) {
        this.invocation   = invocation;
        this.shared       = shared;
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

    boolean shared() {
        return shared;
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
     * Increments the refcount. Returns the new count.
     */
    int attach() {
        return refcount.incrementAndGet();
    }

    /**
     * Decrements the refcount. Returns the new count.
     */
    int detach() {
        return refcount.decrementAndGet();
    }

    int refcount() {
        return refcount.get();
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
     */
    void publishImmediate(Value value) {
        publish(value);
    }

    /**
     * Atomic source rebind for hot-swap. Replaces the current source
     * stream with a fresh one without clearing the mailbox: consumers
     * keep observing the prior value until the new stream emits its
     * first value. Closes the old stream so its pump exits naturally.
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
            oldStream    = sourceStream;
            sourceStream = newSourceStream;
            sourceSpec   = newSourceSpec;
            pumpThread   = null;
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
                log.debug("Backing subscription {} pump threw: {}", id, e.getMessage());
                return;
            }
            if (next == null) {
                return;
            }
            publish(next);
        }
    }

    private void publish(Value value) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (firstValue.isEmpty()) {
                firstValue = Optional.of(value);
            }
            latestValue = Optional.of(value);
        }
        try {
            onValue.accept(value);
        } catch (RuntimeException e) {
            log.warn("Backing subscription {} onValue handler threw: {}", id, e.getMessage(), e);
        }
    }

    private static void safeClose(Stream<Value> stream) {
        try {
            stream.close();
        } catch (RuntimeException e) {
            log.debug("Stream close threw: {}", e.getMessage());
        }
    }
}
