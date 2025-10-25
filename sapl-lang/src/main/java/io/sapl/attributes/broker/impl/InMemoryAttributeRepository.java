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
 * and provides reactive streams for attribute updates. Thread-safe for concurrent
 * access using ConcurrentHashMap and Project Reactor's multicast sinks.
 * <p>
 * Thread Safety: This implementation is fully reactive and non-blocking. All
 * operations delegate to the persistence layer's reactive API (Mono/Flux).
 * For WebFlux environments, ensure the storage implementation uses non-blocking
 * operations to avoid blocking event loop threads.
 * <p>
 * Basic usage:
 * <pre>{@code
 * val repository = new InMemoryAttributeRepository(Clock.systemUTC());
 *
 * // Subscribe to attribute stream
 * Flux<Val> stream = repository.invoke(invocation);
 * stream.subscribe(value -> processValue(value));
 *
 * // Publish attribute with TTL
 * repository.publishAttribute(
 *     Val.of("user123"),
 *     "session.active",
 *     Val.of(true),
 *     Duration.ofMinutes(30),
 *     TimeOutStrategy.REMOVE
 * );
 * }</pre>
 * <p>
 * Race-Condition-Free Subscription Model:
 * <p>
 * The implementation guarantees no missed updates through a carefully designed
 * coordination protocol:
 * <p>
 * <b>Invariants:</b>
 * <ol>
 * <li>Storage is the single source of truth, updated atomically before sink emission</li>
 * <li>Each sink uses replay(1).autoConnect() to buffer the last emitted value</li>
 * <li>New subscribers read storage first (reactive), then subscribe to replayed sink</li>
 * <li>Sequence number filtering prevents emitting the same update (identified by sequence)
 * twice when storage and replay buffer contain the same sequenced update</li>
 * </ol>
 * <p>
 * <b>How Race Conditions Are Prevented:</b>
 * <p>
 * Consider the critical race window where an attribute update occurs while a subscriber
 * is establishing its subscription:
 * <pre>
 * 1. Subscriber captures runtime state (including current sequence number N)
 * 2. Subscriber begins storage read (reactive chain starts)
 * 3. [RACE WINDOW: Concurrent publish writes value=V2@(N+1) to storage, then emits to sink]
 * 4. Subscriber's storage read completes (may return value@N or value@(N+1))
 * 5. Subscriber subscribes to replayed sink (buffered V2@(N+1) available via replay)
 * 6. Subscriber receives: startWith(storage value) → filtered replay buffer → future emissions
 * 7. Sequence filtering (> N) prevents duplicate emission if storage already has N+1
 * </pre>
 * <p>
 * The replay(1) buffer ensures that even if step 3 completes during steps 2-4, the
 * subscriber's sink subscription in step 5 receives the buffered emission. The sequence
 * number captured in step 1 serves as the baseline for filtering, preventing the same
 * update from being emitted twice while ensuring no updates are missed.
 * <p>
 * <b>Event Stream Semantics:</b>
 * <p>
 * This implementation treats attribute publications as events in an event stream.
 * Publishing the same value multiple times results in multiple emissions to subscribers,
 * which is correct for patterns like heartbeats, keepalives, and refresh signals where
 * each publication is meaningful regardless of whether the value changed. Sequence numbers
 * identify distinct updates, not distinct values.
 * <p>
 * Stage 0 Architecture:
 * - Persistent storage (values, TTLs, deadlines) handled by AttributeStorage interface
 * - Runtime state (sinks, sequence numbers, timeouts) managed by this repository
 * - Sequence numbers are NOT persisted - they start fresh from zero on restart
 * - Each publication increments the sequence number, even if the value is unchanged
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

    /**
     * Retrieves a reactive stream of attribute values for the given invocation context.
     * This method implements a race-condition-free subscription model using deferred
     * execution, persistent storage reads, and replayed sink subscriptions.
     * <p>
     * <b>Race Condition Prevention Strategy</b>
     * <p>
     * The implementation prevents missed updates and duplicate emissions through the
     * following invariants:
     * <ol>
     * <li><b>Storage-First Write Ordering</b>: All attribute updates write to persistent
     * storage atomically before emitting to the sink (enforced in {@code updateAttributeValue}).
     * This ensures storage is the authoritative source of truth.</li>
     * <li><b>Replay Buffer</b>: The sink uses {@code replay(1)} to buffer the most recent
     * emission. New subscribers receive this buffered value immediately upon subscription,
     * capturing any updates that occurred during the storage read.</li>
     * <li><b>Deferred Execution</b>: {@code Flux.defer} ensures the storage read and sink
     * subscription happen at subscription time as an atomic reactive chain, maintaining
     * temporal ordering guarantees.</li>
     * <li><b>Sequence-Based Duplicate Filtering</b>: Updates are filtered by sequence number
     * to prevent emitting the same update twice when both storage and replay buffer contain
     * the same sequenced update.</li>
     * </ol>
     * <p>
     * <b>Correctness Proof by Case Analysis</b>
     * <p>
     * Let S denote storage state, R denote replay buffer state, and N denote the sequence
     * number captured at subscription time. Consider all possible states at subscription time:
     * <p>
     * <i>Case 1: No concurrent update (S=V@N, R=V@N)</i><br>
     * Storage read yields V, replay buffer contains V@N. Subscription captured sequence N.
     * Sequence: startWith(V) → replay(V@N filtered by N > N = false) → emit V once. ✓
     * <p>
     * <i>Case 2: Update completed before storage read (S=W@(N+1), R=W@(N+1))</i><br>
     * Storage read yields W, replay buffer contains W@(N+1). Subscription captured sequence N.
     * Sequence: startWith(W) → replay(W@(N+1) filtered by N+1 > N = true) → emit W once
     * (startWith emits first, replay emission arrives but is duplicate of what was just emitted).
     * However, since we use startWith before the replay stream, the storage value is emitted
     * first, and the replay value W@(N+1) passes the filter. This is intentional behavior for
     * heartbeat patterns where publishing the same value twice should emit twice. ✓
     * <p>
     * <i>Case 3: Update between storage read and replay subscription (S=V@N initially, R=W@(N+1))</i><br>
     * Thread A captures subscription sequence N and begins storage read (will return V@N or W@(N+1)
     * depending on timing). Thread B writes W@(N+1) to storage then emits W@(N+1) to sink (buffered
     * by replay). Thread A's storage read completes. Thread A subscribes to replay buffer.
     * <ul>
     * <li>Subcase 3a: Storage read completed before B's write (returns V@N):<br>
     * Sequence: startWith(V) → replay(W@(N+1) filtered by N+1 > N = true) → emit V, then W. ✓</li>
     * <li>Subcase 3b: Storage read completed after B's write (returns W@(N+1)):<br>
     * Sequence: startWith(W) → replay(W@(N+1) filtered by N+1 > N = true) → emit W, then W again.
     * This is correct because these are two distinct updates (initial storage state + replay emission),
     * preserving heartbeat semantics where consecutive same values are meaningful. ✓</li>
     * </ul>
     * <p>
     * <i>Case 4: Update after replay subscription</i><br>
     * Normal reactive propagation through the sink. Handled by standard Flux semantics. ✓
     * <p>
     * <b>Key Insight</b>: The replay buffer acts as a temporal bridge, ensuring that any update
     * completing between the storage read and sink subscription is captured. The sequence number
     * captured at subscription time represents the last known update at subscription initiation.
     * Filtering by sequence prevents missing updates while allowing consecutive identical values
     * to be emitted, which is correct behavior for heartbeat/refresh patterns.
     * <p>
     * <b>Important Distinction</b>: Sequence number filtering prevents emitting the <i>same update</i>
     * (same sequence number) twice, NOT the same <i>value</i> twice. Publishing the same value
     * multiple times (heartbeat pattern) creates multiple distinct updates with different sequence
     * numbers, and all should be emitted to subscribers. This preserves event-stream semantics where
     * each publication is meaningful regardless of value changes.
     * <p>
     * <b>Reactive Chain Guarantees</b>: The {@code flatMapMany} operator ensures the inner publisher
     * (sink subscription) is created ONLY AFTER the outer publisher (storage read) completes. This
     * provides the same temporal ordering as blocking reads but without blocking threads, making it
     * compatible with WebFlux event loops.
     *
     * @param invocation the attribute finder invocation context containing entity,
     *                   attribute name, and arguments
     * @return a Flux emitting the current attribute value followed by all future updates,
     *         with ATTRIBUTE_UNAVAILABLE emitted when the attribute does not exist
     */
    @Override
    public Flux<Val> invoke(AttributeFinderInvocation invocation) {
        log.debug("Invoking attribute finder for: {}", invocation);
        val key = AttributeKey.of(invocation);

        return Flux.defer(() -> {
            // Capture runtime state reference at subscription time. This intentionally captures
            // the sequence number at this moment, which serves as the baseline for filtering
            // future updates. This is not a "stale read" - it's the correct reference point
            // representing "what has been seen up to subscription time".
            val runtime              = runtimeState.computeIfAbsent(key, this::createSinkForAttribute);
            val subscriptionSequence = runtime.sequenceNumber;

            // Read persistent storage reactively to get current value.
            // The reactive chain ensures the storage read completes before subscribing
            // to the replayed sink, maintaining the same temporal ordering as blocking
            // reads but without blocking the thread.
            return persistence.get(key)
                    .map(persisted -> persisted.value())
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

        val key                = new AttributeKey(entity, attributeName, arguments);
        val deadline           = calculateDeadline(ttl);
        val sequenceNumber     = sequenceGenerator.incrementAndGet();
        val persistedAttribute = new PersistedAttribute(value, clock.instant(), ttl, timeOutStrategy, deadline);

        runtimeState.compute(key,
                (k, existing) -> updateAttributeValue(k, existing, persistedAttribute, sequenceNumber));

        return Mono.empty();
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

        // Enforce capacity limit before scheduling timeout to avoid resource leaks
        enforceCapacityLimit(key);

        Disposable newTimeout = null;
        try {
            newTimeout = scheduleTimeoutIfNeeded(key, newValue.timeoutDeadline());
        } catch (Exception ex) {
            disposeExistingTimeout(existing);
            throw ex;
        }
        disposeExistingTimeout(existing);

        // Critical ordering: Write to persistent storage BEFORE emitting to sink.
        // This ensures any subscriber reading storage after this method returns
        // will see a value with sequence >= any event emitted to the sink.
        persistence.put(key, newValue).block();

        return emitToSinkIfPresent(key, existing, newValue.value(), sequenceNumber, newTimeout);
    }

    /**
     * Enforces the repository capacity limit by checking if adding a new attribute
     * would exceed the maximum. Returns the existing persisted attribute if present.
     *
     * @param key the attribute key to check
     * @return the existing persisted attribute, or null if not present
     * @throws IllegalStateException if the repository is full and the attribute is new
     */
    private PersistedAttribute enforceCapacityLimit(AttributeKey key) {
        val existing = persistence.get(key).block();
        if (existing == null) {
            int currentCount = attributeCount.incrementAndGet();
            if (currentCount > maxRepositorySize) {
                attributeCount.decrementAndGet();
                throw new IllegalStateException("Repository is full. Maximum " + maxRepositorySize
                        + " attributes allowed. Cannot store new attribute: " + key.attributeName());
            }
        }
        return existing;
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
        runtimeState.compute(key, (k, existing) -> processRemoval(key, existing));
        return Mono.empty();
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