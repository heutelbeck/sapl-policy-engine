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

@Slf4j
public class InMemoryAttributeRepository implements AttributeRepository {
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
    private final int                             maxRepositorySize;

    public InMemoryAttributeRepository(@NonNull Clock clock) {
        this(clock, new HeapAttributeStorage(),  DEFAULT_MAX_REPOSITORY_SIZE);
    }

    public InMemoryAttributeRepository(@NonNull Clock clock, @NonNull AttributeStorage storage) {
        this(clock, storage, DEFAULT_MAX_REPOSITORY_SIZE);
    }

    public InMemoryAttributeRepository(@NonNull Clock clock,
                                       @NonNull AttributeStorage storage,
                                       int maxRepositorySize) {
        if (maxRepositorySize <= 0) {
            throw new IllegalArgumentException("Max repository size must be positive");
        }

        this.clock             = clock;
        this.persistence       = storage;
        this.maxRepositorySize = maxRepositorySize;
    }

    @Override
    public Flux<Val> invoke(AttributeFinderInvocation invocation) {
        return Flux.defer(() -> {
            log.debug("Invoking attribute finder for: {}", invocation);
            val key = AttributeKey.of(invocation);
            // Capture runtime state reference at subscription time. This intentionally captures
            // the sequence number at this moment, which serves as the baseline for filtering
            // future updates. This is not a "stale read" - it's the correct reference point
            // representing "what has been seen up to subscription time".
            val runtime = runtimeState.computeIfAbsent(key, this::createSinkForAttribute);
            val subscriptionSequence = runtime.sequenceNumber;

            // Read persistent storage reactively to get current value.
            // The reactive chain ensures the storage read completes before subscribing
            // to the replayed sink, maintaining the same temporal ordering as blocking
            // reads but without blocking the thread.
            return persistence.get(key)
                    .map(PersistedAttribute::value)
                    .defaultIfEmpty(ATTRIBUTE_UNAVAILABLE)
                    .flatMapMany(currentValue ->
                            // Subscribe to replayed sink and prepend current storage value.
                            // Filter by sequence number to prevent emitting the same update twice.
                            // The replay(1) buffer ensures any update emitted between the storage
                            // read and this subscription is captured.
                            runtime.replayedSink
                                    .filter(sequencedVal -> sequencedVal.sequenceNumber > subscriptionSequence)
                                    .map(SequencedVal::value)
                                    .startWith(currentValue)
                    );
        });
    }

    /**
     * Creates a new sink infrastructure for an attribute key. The sink uses replay(1)
     * to buffer the last emitted value, preventing race conditions where updates occur
     * between storage reads and sink subscriptions.
     *
     * @param key the attribute key for which to create the sink
     * @return the runtime state containing the sink, replayed flux, and initial sequence
     */
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
    public Mono<Void> publishAttribute(Val entity, String attributeName, List<Val> arguments, Val value, Duration ttl,
                                       TimeOutStrategy timeOutStrategy) {
        validatePublishParameters(attributeName, arguments, value, ttl, timeOutStrategy);
        return Mono.defer( () -> {
            val key                = new AttributeKey(entity, attributeName, arguments);
            val deadline           = calculateDeadline(ttl);
            val sequenceNumber     = sequenceGenerator.incrementAndGet();
            val persistedAttribute = new PersistedAttribute(value, clock.instant(), ttl, timeOutStrategy, deadline);
            return enforceCapacityLimit(key)
                    .then(scheduleTimeout(deadline));
            runtimeState.compute(key,
                    (k, existing) -> updateAttributeValue(k, existing, persistedAttribute, sequenceNumber));
        });
    }

    private Mono<Void> scheduleTimeout(Instant deadline) {
        return Mono.fromCallable(()-> {

            Disposable newTimeout = null;
            try {
                newTimeout = scheduleTimeoutIfNeeded(key, newValue.timeoutDeadline());
            } catch (Exception ex) {
                disposeExistingTimeout(existing);
                throw ex;
            }
            disposeExistingTimeout(existing);
            return newTimeout;
        }
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

    private Mono<RuntimeState> updateAttributeValue(AttributeKey key, RuntimeState existing, PersistedAttribute newValue,
                                              long sequenceNumber) {
        // Enforce capacity limit before scheduling timeout to avoid resource leaks
        return enforceCapacityLimit(key).then(Mono.fromCallable(()-> {

                    Disposable newTimeout = null;
                    try {
                        newTimeout = scheduleTimeoutIfNeeded(key, newValue.timeoutDeadline());
                    } catch (Exception ex) {
                        disposeExistingTimeout(existing);
                        throw ex;
                    }
                    disposeExistingTimeout(existing);
                    return newTimeout;
                }).flatMap(finalTimeout ->
                    // Critical ordering: Write to persistent storage BEFORE emitting to sink.
                    // This ensures any subscriber reading storage after this method returns
                    // will see a value with sequence >= any event emitted to the sink.
                     persistence.put(key, newValue).then(Mono.fromCallable(()->emitToSinkIfPresent(key, existing, newValue.value(), sequenceNumber, finalTimeout)))
                ));
    }

    /**
     * Enforces the repository capacity limit by checking if adding a new attribute
     * would exceed the maximum. Returns the existing persisted attribute if present.
     *
     * @param key the attribute key to check
     * @return the existing persisted attribute, or null if not present
     * @throws IllegalStateException if the repository is full and the attribute is new
     */
    private Mono<Void> enforceCapacityLimit(AttributeKey key) {
        return persistence.get(key).flatMap( existing -> {
            if (existing == null) {
                int currentCount = attributeCount.incrementAndGet();
                if (currentCount > maxRepositorySize) {
                    attributeCount.decrementAndGet();
                    return Mono.error( new IllegalStateException("Repository is full. Maximum " + maxRepositorySize
                            + " attributes allowed. Cannot store new attribute: " + key.attributeName()));
                }
            }
            return Mono.empty();
        });
    }

    /**
     * Disposes an existing timeout subscription if present, preventing memory leaks
     * when attributes are updated or removed.
     *
     * @param runtime the runtime state containing the timeout subscription to dispose
     */
    private void disposeExistingTimeout(RuntimeState runtime) {
        if (runtime != null && runtime.timeoutSubscription != null) {
            log.debug("Cancelling existing timeout");
            runtime.timeoutSubscription.dispose();
        }
    }

    /**
     * Schedules a timeout for an attribute based on its deadline. If the deadline
     * is Instant.MAX (infinite), no timeout is scheduled. If the deadline has already
     * passed, the timeout handler is invoked immediately.
     *
     * @param key the attribute key for which to schedule the timeout
     * @param deadline the instant when the timeout should fire
     * @return the disposable timeout subscription, or null if no timeout was scheduled
     */
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

    /**
     * Emits a value to the sink if present, or creates a new sink if needed.
     * Used during attribute publication to ensure subscribers receive updates.
     *
     * @param key the attribute key
     * @param existing the existing runtime state
     * @param value the value to emit
     * @param sequenceNumber the sequence number for duplicate filtering
     * @param newTimeout the new timeout subscription
     * @return the updated runtime state
     */
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

    /**
     * Creates a new RuntimeState after emitting a value to the sink. Handles both
     * cases where a sink exists (emits and returns state) or doesn't exist (returns
     * state with null sink).
     *
     * @param key the attribute key
     * @param current the current runtime state
     * @param value the value to emit
     * @param sequenceNumber the sequence number for the emission
     * @return the new runtime state with no timeout subscription
     */
    private RuntimeState createStateAfterEmit(AttributeKey key, RuntimeState current, Val value, long sequenceNumber) {
        if (current != null && current.sink != null) {
            tryEmitNext(key, current.sink, value, sequenceNumber);
            return new RuntimeState(current.sink, current.replayedSink, null, sequenceNumber);
        }
        return new RuntimeState(null, null, null, sequenceNumber);
    }

    /**
     * Handles timeout events for attributes. Invoked when a scheduled timeout fires,
     * triggering the appropriate timeout strategy (REMOVE or BECOME_UNDEFINED).
     *
     * @param key the attribute key that timed out
     */
    private void handleTimeout(AttributeKey key) {
        log.debug("Handling timeout for attribute: {}", key);
        runtimeState.compute(key, (k, current) -> processTimeout(key, current));
    }

    /**
     * Processes a timeout by verifying the attribute still exists and its deadline
     * has been reached, then applying the configured timeout strategy.
     *
     * @param key the attribute key that timed out
     * @param current the current runtime state
     * @return the updated runtime state after processing timeout, or current state if timeout invalid
     */
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

    /**
     * Checks if a timeout deadline has been reached based on the current clock time.
     *
     * @param deadline the deadline to check
     * @return true if the current time is after the deadline, false otherwise
     */
    private boolean isDeadlineReached(Instant deadline) {
        return clock.instant().isAfter(deadline);
    }

    /**
     * Applies the configured timeout strategy for an attribute, either removing it
     * or transitioning it to UNDEFINED state.
     *
     * @param key the attribute key
     * @param current the current runtime state
     * @param persisted the persisted attribute containing the strategy
     * @return the updated runtime state after applying the strategy
     */
    private RuntimeState applyTimeoutStrategy(AttributeKey key, RuntimeState current, PersistedAttribute persisted) {
        if (persisted.timeoutStrategy() == TimeOutStrategy.REMOVE) {
            return handleRemovalStrategy(key, current);
        }
        return handleBecomeUndefinedStrategy(key, current);
    }

    /**
     * Handles the REMOVE timeout strategy by deleting the attribute from persistence
     * and emitting ATTRIBUTE_UNAVAILABLE to subscribers.
     *
     * @param key the attribute key to remove
     * @param current the current runtime state
     * @return the updated runtime state with ATTRIBUTE_UNAVAILABLE emitted, or null if no sink
     */
    private RuntimeState handleRemovalStrategy(AttributeKey key, RuntimeState current) {
        log.debug("Removing timed-out attribute");
        attributeCount.decrementAndGet();
        persistence.remove(key).block();

        if (current != null && current.sink != null) {
            val sequenceNumber = sequenceGenerator.incrementAndGet();
            return createStateAfterEmit(key, current, ATTRIBUTE_UNAVAILABLE, sequenceNumber);
        }
        return null;
    }

    /**
     * Handles the BECOME_UNDEFINED timeout strategy by transitioning the attribute to
     * UNDEFINED state with infinite TTL and emitting Val.UNDEFINED to subscribers.
     *
     * @param key the attribute key to transition
     * @param current the current runtime state
     * @return the updated runtime state with Val.UNDEFINED emitted
     */
    private RuntimeState handleBecomeUndefinedStrategy(AttributeKey key, RuntimeState current) {
        log.debug("Setting timed-out attribute to UNDEFINED");
        val sequenceNumber     = sequenceGenerator.incrementAndGet();
        val undefinedAttribute = new PersistedAttribute(Val.UNDEFINED, clock.instant(), INFINITE,
                TimeOutStrategy.REMOVE, Instant.MAX);

        persistence.put(key, undefinedAttribute).block();

        return createStateAfterEmit(key, current, Val.UNDEFINED, sequenceNumber);
    }

    @Override
    public Mono<Void> removeAttribute(Val entity, String attributeName, List<Val> arguments) {
        validateRemovalParameters(attributeName, arguments);
        val key = new AttributeKey(entity, attributeName, arguments);
        return Mono.fromRunnable( () -> {
            runtimeState.compute(key, (k, existing) -> processRemoval(key, existing));
        });
    }

    /**
     * Validates parameters for attribute removal operations.
     *
     * @param attributeName the attribute name to validate
     * @param arguments the arguments to validate
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateRemovalParameters(String attributeName, Iterable<Val> arguments) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
    }

    /**
     * Processes attribute removal by disposing timeouts, deleting from persistence,
     * and emitting ATTRIBUTE_UNAVAILABLE to subscribers.
     *
     * @param key the attribute key to remove
     * @param existing the existing runtime state
     * @return the updated runtime state with ATTRIBUTE_UNAVAILABLE emitted, or null if no sink
     */
    private RuntimeState processRemoval(AttributeKey key, RuntimeState existing) {
        disposeExistingTimeout(existing);

        val persisted = persistence.get(key).block();
        if (persisted != null) {
            attributeCount.decrementAndGet();
            persistence.remove(key).block();
        }

        if (existing != null && existing.sink != null) {
            val sequenceNumber = sequenceGenerator.incrementAndGet();
            return createStateAfterEmit(key, existing, ATTRIBUTE_UNAVAILABLE, sequenceNumber);
        }

        return null;
    }
}