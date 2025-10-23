/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of AttributeRepository that stores attribute values
 * and provides reactive streams for attribute updates. Thread-safe for
 * concurrent
 * access using ConcurrentHashMap and Project Reactor's multicast sinks.
 * <p>
 * Race-condition-free subscription model:
 * - Storage is the single source of truth, updated atomically before sink
 * emission
 * - Each sink uses replay(1).autoConnect() to buffer the last emitted value
 * - New subscribers read storage first, then subscribe to replayed sink
 * - Sequence numbers filter duplicates when storage and sink contain the same
 * update
 * <p>
 * How the race condition is prevented:
 * 1. Subscriber reads storage (sequence=N, value=V1)
 * 2. [Concurrent publish: storage→sequence=N+1, value=V2, then
 * sink→emit(N+1,V2)]
 * 3. Subscriber subscribes to replayed sink, receives replayed value (N+1,V2)
 * 4. Subscriber emits V1 from storage (startWith)
 * 5. Subscriber receives V2 from sink, filters by sequence N+1 > N, emits V2
 * 6. Result: V1, V2 (both values received, none missed)
 * <p>
 * The replay(1) ensures that even if step 2 happens between steps 1 and 3,
 * the new subscriber still receives the last emitted value from the sink.
 * Sequence filtering prevents emitting V2 twice if it's already in storage.
 * <p>
 * Stage 0 Architecture:
 * - Persistent storage (values, TTLs, deadlines) handled by AttributeStorage
 * interface
 * - Runtime state (sinks, sequence numbers, timeouts) managed by this
 * repository
 * - Sequence numbers are NOT persisted - they start fresh from zero on restart
 */
@Slf4j
public class InMemoryAttributeRepository implements AttributeRepository {
    private static final int DEFAULT_BUFFER_SIZE         = 1000;
    private static final int DEFAULT_MAX_REPOSITORY_SIZE = 10_000;

    public static final Val ATTRIBUTE_UNAVAILABLE = Val.error("Attribute unavailable.");

    private record SequencedVal(Val value, long sequenceNumber) {}

    private record RuntimeState(
            Sinks.Many<SequencedVal> sink,
            Flux<SequencedVal> replayedSink,
            Disposable timeoutSubscription,
            long sequenceNumber) {}

    private final AttributeStorage                persistence;
    private final Map<AttributeKey, RuntimeState> runtimeState      = new ConcurrentHashMap<>();
    private final AtomicInteger                   attributeCount    = new AtomicInteger(0);
    private final AtomicLong                      sequenceGenerator = new AtomicLong(0);
    private final Clock                           clock;
    private final int                             bufferSize;
    private final int                             maxRepositorySize;

    /**
     * Creates an InMemoryAttributeRepository with default configuration and heap
     * storage.
     *
     * @param clock the clock to use for timing operations
     */
    public InMemoryAttributeRepository(@NonNull Clock clock) {
        this(clock, new HeapAttributeStorage(), DEFAULT_BUFFER_SIZE, DEFAULT_MAX_REPOSITORY_SIZE);
    }

    /**
     * Creates an InMemoryAttributeRepository with custom storage backend.
     *
     * @param clock the clock to use for timing operations
     * @param storage the storage backend for persistence
     */
    public InMemoryAttributeRepository(@NonNull Clock clock, @NonNull AttributeStorage storage) {
        this(clock, storage, DEFAULT_BUFFER_SIZE, DEFAULT_MAX_REPOSITORY_SIZE);
    }

    /**
     * Creates an InMemoryAttributeRepository with custom configuration.
     *
     * @param clock the clock to use for timing operations
     * @param storage the storage backend for persistence
     * @param bufferSize the backpressure buffer size for each attribute stream
     * @param maxRepositorySize the maximum number of attributes that can be stored
     */
    public InMemoryAttributeRepository(@NonNull Clock clock,
            @NonNull AttributeStorage storage,
            int bufferSize,
            int maxRepositorySize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        if (maxRepositorySize <= 0) {
            throw new IllegalArgumentException("Max repository size must be positive");
        }

        this.clock             = clock;
        this.persistence       = storage;
        this.bufferSize        = bufferSize;
        this.maxRepositorySize = maxRepositorySize;
    }

    @Override
    public Flux<Val> invoke(AttributeFinderInvocation invocation) {
        log.debug("Invoking attribute finder for: {}", invocation);
        val key = AttributeKey.of(invocation);

        return Flux.defer(() -> {
            val runtime = runtimeState.computeIfAbsent(key, this::createSinkForAttribute);

            // Read persistent storage first to get current value.
            // Even if an update happens concurrently, the replayed sink will have it
            // buffered.
            val persisted       = persistence.get(key).block();
            val currentValue    = persisted != null ? persisted.value() : ATTRIBUTE_UNAVAILABLE;
            val storageSequence = runtime.sequenceNumber;

            // The replayed sink has replay(1), so new subscribers receive the last emitted
            // value.
            // This prevents missed updates during the window between reading storage and
            // subscribing.
            return runtime.replayedSink.filter(sequencedVal -> sequencedVal.sequenceNumber > storageSequence)
                    .map(SequencedVal::value).startWith(currentValue);
        });
    }

    private RuntimeState createSinkForAttribute(AttributeKey key) {
        log.debug("Creating new sink for key: {}", key);
        val sink = Sinks.many().multicast().<SequencedVal>onBackpressureBuffer(bufferSize, false);
        // replay(1) keeps the last emitted value in a buffer for new subscribers.
        // autoConnect() means the replayed flux stays connected to the sink even when
        // all subscribers disconnect.
        // This prevents the race condition where an update is emitted between reading
        // storage and subscribing.
        val replayedSink = sink.asFlux().replay(1).autoConnect();
        return new RuntimeState(sink, replayedSink, null, -1L);
    }

    @Override
    public void publishAttribute(Val entity, String attributeName, List<Val> arguments, Val value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        validatePublishParameters(attributeName, arguments, value, ttl, timeOutStrategy);

        val key                = new AttributeKey(entity, attributeName, arguments);
        val deadline           = calculateDeadline(ttl);
        val sequenceNumber     = sequenceGenerator.incrementAndGet();
        val persistedAttribute = new PersistedAttribute(value, clock.instant(), ttl, timeOutStrategy, deadline);

        runtimeState.compute(key,
                (k, existing) -> updateAttributeValue(k, existing, persistedAttribute, sequenceNumber));
    }

    private void validatePublishParameters(String attributeName, List<Val> arguments, Val value, Duration ttl,
            TimeOutStrategy strategy) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must not be null or negative");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("TimeOutStrategy must not be null");
        }
    }

    private Instant calculateDeadline(Duration ttl) {
        try {
            return clock.instant().plus(ttl);
        } catch (DateTimeException | ArithmeticException ex) {
            return Instant.MAX;
        }
    }

    private RuntimeState updateAttributeValue(AttributeKey key, RuntimeState existing, PersistedAttribute newValue,
            long sequenceNumber) {
        enforceCapacityLimit(key);

        Disposable newTimeout = null;
        try {
            newTimeout = scheduleTimeoutIfNeeded(key, newValue.timeoutDeadline());
        } finally {
            disposeExistingTimeout(existing);
        }

        // Critical ordering: Write to persistent storage BEFORE emitting to sink.
        // This ensures any subscriber reading storage after this method returns
        // will see a value with sequence >= any event emitted to the sink.
        persistence.put(key, newValue).block();

        return emitToSinkIfPresent(key, existing, newValue.value(), sequenceNumber, newTimeout);
    }

    private void enforceCapacityLimit(AttributeKey key) {
        boolean isNewAttribute = !hasPersisted(key);
        if (isNewAttribute) {
            int currentCount = attributeCount.incrementAndGet();
            if (currentCount > maxRepositorySize) {
                attributeCount.decrementAndGet();
                throw new IllegalStateException("Repository is full. Maximum " + maxRepositorySize
                        + " attributes allowed. " + "Cannot store new attribute: " + key.attributeName());
            }
        }
    }

    private boolean hasPersisted(AttributeKey key) {
        return persistence.get(key).block() != null;
    }

    private void disposeExistingTimeout(RuntimeState runtime) {
        if (runtime != null && runtime.timeoutSubscription != null) {
            log.debug("Cancelling existing timeout");
            runtime.timeoutSubscription.dispose();
        }
    }

    private Disposable scheduleTimeoutIfNeeded(AttributeKey key, Instant deadline) {
        if (deadline.equals(Instant.MAX)) {
            return null;
        }

        val delayDuration = Duration.between(clock.instant(), deadline);
        if (delayDuration.isNegative() || delayDuration.isZero()) {
            log.debug("Attribute already expired or expires immediately: {}", key);
            return Mono.fromRunnable(() -> handleTimeout(key)).subscribeOn(Schedulers.parallel()).subscribe();
        }

        log.debug("Scheduling timeout in {} for key: {}", delayDuration, key);
        return Mono.delay(delayDuration).subscribeOn(Schedulers.parallel()).subscribe(tick -> handleTimeout(key));
    }

    private RuntimeState emitToSinkIfPresent(AttributeKey key, RuntimeState existing, Val value, long sequenceNumber,
            Disposable newTimeout) {
        Sinks.Many<SequencedVal> sink;
        Flux<SequencedVal>       replayedSink;

        if (existing != null && existing.sink != null) {
            sink         = existing.sink;
            replayedSink = existing.replayedSink;
        } else {
            // Create new sink if it doesn't exist.
            // This handles the case where publishAttribute() is called before any invoke().
            log.debug("Creating new sink during publish for key: {}", key);
            val newSinkHolder = createSinkForAttribute(key);
            sink         = newSinkHolder.sink;
            replayedSink = newSinkHolder.replayedSink;
        }

        log.debug("Emitting value to sink");
        tryEmitNext(key, sink, value, sequenceNumber);
        return new RuntimeState(sink, replayedSink, newTimeout, sequenceNumber);
    }

    private void tryEmitNext(AttributeKey key, Sinks.Many<SequencedVal> sink, Val value, long sequenceNumber) {
        val result = sink.tryEmitNext(new SequencedVal(value, sequenceNumber));
        if (result.isFailure()) {
            if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                log.warn("Backpressure overflow for attribute {}, update dropped. "
                        + "Subscriber is not consuming fast enough.", key);
            } else if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                log.debug("Sink terminated for attribute: {}", key);
            } else if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.debug("No active subscribers for attribute: {}, value buffered by replay(1)", key);
            } else {
                log.error("Unexpected emit failure for {}: {}", key, result);
                throw new IllegalStateException("Unexpected emit failure for attribute " + key + ": " + result);
            }
        }
    }

    private void handleTimeout(AttributeKey key) {
        log.info("Handling timeout for attribute: {}", key);
        runtimeState.compute(key, (k, current) -> processTimeout(key, current));
    }

    private RuntimeState processTimeout(AttributeKey key, RuntimeState current) {
        val persisted = persistence.get(key).block();
        if (persisted == null) {
            log.debug("Attribute no longer exists or already removed: {}", key);
            return current;
        }

        if (!isDeadlineReached(persisted.timeoutDeadline())) {
            log.warn("Timeout fired but deadline not yet reached for: {}", key);
            return current;
        }

        return applyTimeoutStrategy(key, current, persisted);
    }

    private boolean isDeadlineReached(Instant deadline) {
        return clock.instant().isAfter(deadline);
    }

    private RuntimeState applyTimeoutStrategy(AttributeKey key, RuntimeState current, PersistedAttribute persisted) {
        if (persisted.timeoutStrategy() == TimeOutStrategy.REMOVE) {
            return handleRemovalStrategy(key, current);
        }
        return handleBecomeUndefinedStrategy(key, current);
    }

    private RuntimeState handleRemovalStrategy(AttributeKey key, RuntimeState current) {
        log.debug("Removing timed-out attribute");
        attributeCount.decrementAndGet();
        persistence.remove(key).block();

        if (current != null && current.sink != null) {
            val sequenceNumber = sequenceGenerator.incrementAndGet();
            tryEmitNext(key, current.sink, ATTRIBUTE_UNAVAILABLE, sequenceNumber);
            return new RuntimeState(current.sink, current.replayedSink, null, sequenceNumber);
        }
        return null;
    }

    private RuntimeState handleBecomeUndefinedStrategy(AttributeKey key, RuntimeState current) {
        log.debug("Setting timed-out attribute to UNDEFINED");
        val sequenceNumber     = sequenceGenerator.incrementAndGet();
        val undefinedAttribute = new PersistedAttribute(Val.UNDEFINED, clock.instant(), INFINITE,
                TimeOutStrategy.REMOVE, Instant.MAX);

        persistence.put(key, undefinedAttribute).block();

        if (current != null && current.sink != null) {
            tryEmitNext(key, current.sink, Val.UNDEFINED, sequenceNumber);
            return new RuntimeState(current.sink, current.replayedSink, null, sequenceNumber);
        }
        return new RuntimeState(null, null, null, sequenceNumber);
    }

    @Override
    public void removeAttribute(Val entity, String attributeName, List<Val> arguments) {
        validateRemovalParameters(attributeName, arguments);
        val key = new AttributeKey(entity, attributeName, arguments);
        runtimeState.compute(key, (k, existing) -> processRemoval(key, existing));
    }

    private void validateRemovalParameters(String attributeName, Iterable<Val> arguments) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
    }

    private RuntimeState processRemoval(AttributeKey key, RuntimeState existing) {
        disposeExistingTimeout(existing);

        val persisted = persistence.get(key).block();
        if (persisted != null) {
            attributeCount.decrementAndGet();
            persistence.remove(key).block();
        }

        if (existing != null && existing.sink != null) {
            val sequenceNumber = sequenceGenerator.incrementAndGet();
            tryEmitNext(key, existing.sink, ATTRIBUTE_UNAVAILABLE, sequenceNumber);
            return new RuntimeState(existing.sink, existing.replayedSink, null, sequenceNumber);
        }

        return null;
    }
}
