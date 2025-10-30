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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory implementation of AttributeRepository supporting reactive attribute
 * streaming with TTL-based expiration and subscription management.
 * <p>
 * Provides thread-safe attribute storage with automatic timeout handling.
 * Attributes
 * can be published with optional TTL and timeout strategies (REMOVE or
 * BECOME_UNDEFINED).
 * Subscribers receive the current value immediately upon invocation, followed
 * by updates
 * as attributes are published or removed.
 * <p>
 * Uses Project Reactor for non-blocking operations. All subscriptions are
 * managed with
 * replay(1).refCount(1) semantics, ensuring late subscribers receive the last
 * value and
 * automatic cleanup occurs when the last subscriber cancels.
 */
@Slf4j
public class InMemoryAttributeRepository implements AttributeRepository {

    public static final Val ATTRIBUTE_UNAVAILABLE = Val.error("Attribute unavailable.");

    private final AttributeStorage                             storage;
    private final Clock                                        clock;
    private final ConcurrentHashMap<AttributeKey, Disposable>  scheduledTimeouts   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AttributeKey, SinkAndFlux> activeSubscriptions = new ConcurrentHashMap<>();

    private record SinkAndFlux(Sinks.Many<Val> sink, Flux<Val> flux) {}

    /**
     * Creates an attribute repository with default heap-based storage.
     *
     * @param clock the clock for time-based operations, must not be null
     */
    public InMemoryAttributeRepository(@NonNull Clock clock) {
        this(clock, new HeapAttributeStorage());
    }

    /**
     * Creates an attribute repository with custom storage backend.
     *
     * @param clock the clock for time-based operations, must not be null
     * @param storage the storage implementation, must not be null
     */
    public InMemoryAttributeRepository(@NonNull Clock clock, @NonNull AttributeStorage storage) {
        this.clock   = clock;
        this.storage = storage;
        initialize();
    }

    /**
     * Initializes the repository by recovering pending timeouts from storage.
     * Removes stale attributes whose timeout deadlines have passed and reschedules
     * active timeouts for attributes that are still within their TTL.
     * <p>
     * Blocks until completion. Must be called before accepting connections.
     */
    private void initialize() {
        log.info("Initializing attribute repository with storage: {}", storage.getClass().getSimpleName());
        recoverTimeouts().block(); // Must complete before accepting connections
        log.info("Attribute repository initialized. Scheduled timeouts: {}", scheduledTimeouts.size());
    }

    private Mono<Void> recoverTimeouts() {
        log.debug("Recovering attribute timeouts from storage...");

        val staleAttributes     = new AtomicInteger(0);
        val permanentAttributes = new AtomicInteger(0);
        val scheduledAttributes = new AtomicInteger(0);

        return storage.findAll().concatMap(entry -> {
            val key     = entry.getKey();
            val value   = entry.getValue();
            val timeout = value.timeoutDeadline();
            if (timeout == null || timeout.equals(Instant.MAX)) {
                permanentAttributes.incrementAndGet();
                return Mono.empty();
            } else {
                if (timeout.isBefore(clock.instant())) {
                    staleAttributes.incrementAndGet();
                    log.info("Removing stale attribute from storage; {}", key);
                    return storage.remove(key);
                } else {
                    scheduledAttributes.incrementAndGet();
                    scheduleTimeout(key, timeout, value.timeoutStrategy());
                    return Mono.empty();
                }
            }
        }).then()
                .doOnSuccess(v -> log.info("Timeout recovery complete. Scheduled: {}, Permanent: {}, Stale: {}",
                        scheduledAttributes.get(), permanentAttributes.get(), staleAttributes.get()))
                .doOnError(error -> log.error("Failed to recover timeouts", error));
    }

    private void scheduleTimeout(AttributeKey key, Instant deadline, TimeOutStrategy strategy) {
        val      now          = clock.instant();
        Duration practicalTTL = Duration.ofNanos(Long.MAX_VALUE);
        try {
            practicalTTL = Duration.between(now, deadline);
        } catch (ArithmeticException e) {
            log.warn("Delay is excessive. Fall back to default max delay of 292years");
        }

        log.info("Scheduling timeout for key: {} at deadline: {} (delay: {})", key, deadline, practicalTTL);

        val subscriptionRef = new AtomicReference<Disposable>();

        val timeout = Mono.delay(practicalTTL, Schedulers.parallel())
                .flatMap(tick -> handleTimeout(key, strategy, deadline)).doFinally(signal -> {
                    val self = subscriptionRef.get();
                    if (self != null) {
                        // Only remove if we're still the active timeout for this key
                        val removed = scheduledTimeouts.remove(key, self);
                        if (removed) {
                            log.debug("Timeout cleanup: removed timeout for key: {}", key);
                        } else {
                            log.debug("Timeout cleanup: timeout for key: {} was already replaced", key);
                        }
                    }
                }).subscribe(v -> log.debug("Timeout completed successfully for key: {}", key),
                        error -> log.error("Timeout handling failed for key: {}", key, error));

        subscriptionRef.set(timeout);

        val previous = scheduledTimeouts.put(key, timeout);
        if (previous != null) {
            log.debug("Canceling previous timeout for key: {}", key);
            previous.dispose();
        }
    }

    /**
     * Publishes an attribute value with optional TTL and timeout strategy.
     * If an attribute with the same key exists, it is replaced. If a timeout
     * was scheduled for the key, it is cancelled and rescheduled if TTL is
     * provided.
     * <p>
     * Active subscribers are notified immediately with the new value.
     *
     * @param entity the entity the attribute belongs to, may be null
     * @param attributeName the attribute name, must not be null or blank
     * @param arguments the attribute arguments, must not be null
     * @param value the attribute value, must not be null
     * @param ttl the time-to-live, null for permanent attributes
     * @param timeOutStrategy the strategy when TTL expires, must not be null
     * @return a Mono that completes when the attribute is persisted
     * @throws IllegalArgumentException if any parameter validation fails
     */
    @Override
    public Mono<Void> publishAttribute(Val entity, String attributeName, List<Val> arguments, Val value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        validatePublishParameters(attributeName, arguments, value, ttl, timeOutStrategy);

        return Mono.defer(() -> {
            val key       = new AttributeKey(entity, attributeName, arguments);
            val deadline  = calculateDeadline(ttl);
            val persisted = new PersistedAttribute(value, clock.instant(), ttl, timeOutStrategy, deadline);

            log.debug("Publishing attribute key: {}, value: {}, ttl: {}, deadline: {}, strategy: {}", key, value, ttl,
                    deadline, timeOutStrategy);

            return storage.put(key, persisted).doOnSuccess(v -> {
                log.debug("Attribute persisted successfully: {}", key);
                cancelScheduledTimeout(key);
                if (ttl != null) {
                    scheduleTimeout(key, deadline, timeOutStrategy);
                }
                notifySubscribers(key, value);
            }).doOnError(error -> log.error("Failed to publish attribute: {}", key, error));
        });
    }

    private void validatePublishParameters(String attributeName, List<Val> arguments, Val value, Duration ttl,
            TimeOutStrategy strategy) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank.");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null.");
        }
        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must not be negative.");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Timeout strategy must not be null.");
        }
    }

    private Instant calculateDeadline(Duration ttl) {
        if (ttl == null) {
            return null;
        }
        try {
            return clock.instant().plus(ttl);
        } catch (DateTimeException | ArithmeticException e) {
            log.debug("TTL too large, using Instant.MAX as deadline");
            return Instant.MAX;
        }
    }

    /**
     * Removes an attribute and cancels its scheduled timeout if any.
     * Active subscribers are notified with ATTRIBUTE_UNAVAILABLE.
     * <p>
     * If the attribute does not exist, the operation completes without error.
     *
     * @param entity the entity the attribute belongs to, may be null
     * @param attributeName the attribute name, must not be null or blank
     * @param arguments the attribute arguments, must not be null
     * @return a Mono that completes when the attribute is removed
     * @throws IllegalArgumentException if any parameter validation fails
     */
    @Override
    public Mono<Void> removeAttribute(Val entity, String attributeName, List<Val> arguments) {
        validateRemovalParameters(attributeName, arguments);
        val key = new AttributeKey(entity, attributeName, arguments);

        log.debug("Removing attribute: {}", key);

        return storage.get(key).flatMap(persisted -> {
            cancelScheduledTimeout(key);
            return storage.remove(key).doOnSuccess(v -> {
                log.debug("Attribute removed successfully: {}", key);
                notifySubscribers(key, ATTRIBUTE_UNAVAILABLE);
            }).doOnError(error -> log.error("Failed to remove attribute: {}", key, error));

        }).then();
    }

    private void validateRemovalParameters(String attributeName, Iterable<Val> arguments) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank.");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null.");
        }
    }

    private void cancelScheduledTimeout(AttributeKey key) {
        val timeout = scheduledTimeouts.remove(key);
        if (timeout != null) {
            log.debug("Canceling scheduled timeout for key: {}", key);
            timeout.dispose();
        }
    }

    private Mono<Void> handleTimeout(AttributeKey key, TimeOutStrategy strategy, Instant expectedDeadline) {
        log.debug("Handling timeout for key: {}, strategy: {}, expected deadline: {}", key, strategy, expectedDeadline);
        return storage.get(key).flatMap(persisted -> {
            if (!expectedDeadline.equals(persisted.timeoutDeadline())) {
                log.debug(
                        "Deadline mismatch for key: {}. Expected: {}, Actual: {}. Attribute was republished, skipping timeout.",
                        key, expectedDeadline, persisted.timeoutDeadline());
                return Mono.empty();
            }
            log.debug("Deadline reached for key: {}, applying strategy: {}", key, strategy);
            return applyTimeoutStrategy(key, strategy);
        }).switchIfEmpty(Mono.fromRunnable(
                () -> log.debug("Timeout fired for key: {} but attribute no longer exists in storage", key))).then();
    }

    private Mono<Void> applyTimeoutStrategy(AttributeKey key, TimeOutStrategy strategy) {
        if (strategy == TimeOutStrategy.REMOVE) {
            return handleRemovalStrategy(key);
        }
        return handleBecomeUndefinedStrategy(key);
    }

    private Mono<Void> handleRemovalStrategy(AttributeKey key) {
        log.debug("Applying REMOVE strategy for timed-out attribute: {}", key);
        return storage.remove(key).doOnSuccess(v -> {
            log.debug("Timed-out attribute removed: {}", key);
            notifySubscribers(key, ATTRIBUTE_UNAVAILABLE);
        });
    }

    private Mono<Void> handleBecomeUndefinedStrategy(AttributeKey key) {
        log.debug("Applying BECOME_UNDEFINED strategy for timed-out attribute: {}", key);
        val undefinedAttribute = new PersistedAttribute(Val.UNDEFINED, clock.instant(), INFINITE,
                TimeOutStrategy.REMOVE, Instant.MAX);

        return storage.put(key, undefinedAttribute).doOnSuccess(v -> {
            log.debug("Timed-out attribute set to UNDEFINED: {}", key);
            notifySubscribers(key, Val.UNDEFINED);
        });
    }

    /**
     * Creates a reactive stream for an attribute that emits the current value
     * followed by all future updates until the subscription is cancelled.
     * <p>
     * On first subscription for a key, emits the current value from storage or
     * ATTRIBUTE_UNAVAILABLE if not present. Subsequent subscriptions share the
     * same stream via replay(1).refCount(1) semantics.
     * <p>
     * The stream automatically cleans up when the last subscriber cancels.
     *
     * @param invocation the attribute invocation specifying entity, name, and
     * arguments
     * @return a Flux emitting attribute values, never completes unless explicitly
     * cancelled
     */
    @Override
    public Flux<Val> invoke(AttributeFinderInvocation invocation) {
        val key         = AttributeKey.of(invocation);
        val sinkAndFlux = activeSubscriptions.compute(key, (k, sinkFlux) -> createOrReuseSinkAndFlux(key, sinkFlux));
        return sinkAndFlux.flux;
    }

    /**
     * Creates a new SinkAndFlux for an attribute key or reuses an existing one.
     * <p>
     * Implements replay(1).refCount(1) semantics for sharing subscriptions.
     *
     * @param key the attribute key
     * @param existingSinkFlux the existing SinkAndFlux if present, null otherwise
     * @return a SinkAndFlux for the attribute
     */
    private SinkAndFlux createOrReuseSinkAndFlux(AttributeKey key, SinkAndFlux existingSinkFlux) {
        if (existingSinkFlux != null) {
            return existingSinkFlux;
        }

        val sink = Sinks.many().multicast().<Val>onBackpressureBuffer();
        val flux = storage.get(key).map(PersistedAttribute::value).defaultIfEmpty(ATTRIBUTE_UNAVAILABLE)
                .concatWith(sink.asFlux()).doOnCancel(() -> cleanupSubscription(key, sink)).replay(1).refCount(1);

        return new SinkAndFlux(sink, flux);
    }

    /**
     * Cleans up subscription state when the last subscriber cancels.
     * <p>
     * Removes the SinkAndFlux from active subscriptions if it matches the
     * original sink.
     *
     * @param key the attribute key
     * @param sink the sink that triggered cleanup
     */
    private void cleanupSubscription(AttributeKey key, Sinks.Many<Val> sink) {
        log.debug("Last subscriber cancelled {}, cleanup triggered", key);
        activeSubscriptions.compute(key, (k, sinkFlux) -> shouldRemoveSinkAndFlux(sinkFlux, sink));
    }

    /**
     * Determines whether a SinkAndFlux should be removed during cleanup.
     * <p>
     * Returns null if the sink matches the original sink that triggered cleanup,
     * indicating removal. Returns the existing SinkAndFlux if it represents a
     * newer subscription.
     *
     * @param sinkFlux the current SinkAndFlux in the map
     * @param originalSink the sink that triggered the cleanup
     * @return null to remove, or the SinkAndFlux to retain
     */
    private SinkAndFlux shouldRemoveSinkAndFlux(SinkAndFlux sinkFlux, Sinks.Many<Val> originalSink) {
        if (sinkFlux == null) {
            return null;
        }
        if (sinkFlux.sink == originalSink) {
            return null;
        }
        return sinkFlux;
    }

    private void notifySubscribers(AttributeKey key, Val value) {
        val sinkAndFlux = activeSubscriptions.get(key);
        if (sinkAndFlux != null) {
            log.debug("Broadcasting value {} to subscribers for key: {}", value, key);
            val emitResult = sinkAndFlux.sink.tryEmitNext(value);
            if (emitResult.isFailure()) {
                log.debug("Failed to emit value {} to subscriber for key: {} - result: {}", value, key, emitResult);
            }
        }
    }

}
