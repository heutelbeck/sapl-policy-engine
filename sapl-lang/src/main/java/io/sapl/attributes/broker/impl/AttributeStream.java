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
import io.sapl.attributes.broker.api.AttributeFinder;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages a multicast reactive stream for attribute values with grace period
 * support for efficient re-subscription and PIP hot-swapping capabilities.
 * <p>
 * The stream supports two initialization modes:
 * <ul>
 * <li>Without initial PIP: starts without values, awaiting PIP connection
 * via hot-swap</li>
 * <li>With initial PIP: immediately connected and streaming values</li>
 * </ul>
 * <p>
 * Key features:
 * <ul>
 * <li>Multicast with replay(1): all subscribers share the same upstream, new
 * subscribers receive last value</li>
 * <li>Grace period: keeps stream alive briefly after last subscriber cancels to
 * avoid reconnection overhead</li>
 * <li>PIP hot-swapping: can replace data source without recreating the
 * stream</li>
 * <li>Bounded backpressure buffering: buffers up to 128 values to handle
 * synchronous multi-value emissions and transient slow consumers, preventing
 * memory leaks while ensuring legitimate value sequences are delivered</li>
 * <li>Thread-safe disposal: prevents race conditions between getStream() and
 * grace period expiration</li>
 * </ul>
 */
@Slf4j
public class AttributeStream {
    public static final int                 BUFFER_SIZE = 128;
    @Getter
    private final AttributeFinderInvocation invocation;

    private final Many<Val> sink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE);

    private final Flux<Val> stream;

    private final Consumer<AttributeStream>   cleanupAction;
    private final AtomicReference<Disposable> currentPipSubscription          = new AtomicReference<>();
    private final AtomicReference<Disposable> pipSnapshotAtGracePeriodStart   = new AtomicReference<>();
    private final AtomicReference<Flux<Val>>  configuredAttributeFinderStream = new AtomicReference<>();
    private final Object                      connectionLock                  = new Object();
    private volatile boolean                  disconnected                    = false;
    private volatile boolean                  disconnectErrorAlreadyPublished = false;
    private volatile boolean                  hasActiveSubscribers            = false;
    private volatile boolean                  disposed                        = false;

    /**
     * Creates an AttributeStream without an initial PIP connection.
     * <p>
     * The stream awaits PIP connection via
     * {@link #connectToPolicyInformationPoint(AttributeFinder)}.
     * No values are emitted until a PIP is connected and a subscriber arrives.
     * <p>
     * Use case: PIP not yet available but may be hot-deployed during policy
     * evaluation lifecycle.
     *
     * @param invocation the attribute invocation configuration
     * @param cleanupAction callback executed when grace period expires and stream
     * should be cleaned up from broker registry
     * @param gracePeriod duration to keep stream alive after last subscriber
     * cancels
     */
    public AttributeStream(@NonNull AttributeFinderInvocation invocation,
            @NonNull Consumer<AttributeStream> cleanupAction,
            @NonNull Duration gracePeriod) {
        this.invocation    = invocation;
        this.cleanupAction = cleanupAction;
        this.stream        = createMulticastStream(gracePeriod);
    }

    /**
     * Creates an AttributeStream with an immediate PIP connection.
     * <p>
     * The PIP starts emitting values as soon as the first subscriber subscribes.
     * Supports later hot-swapping via
     * {@link #connectToPolicyInformationPoint(AttributeFinder)}.
     * <p>
     * Use case: PIP is available at stream creation time.
     *
     * @param invocation the attribute invocation configuration
     * @param cleanupAction callback executed when grace period expires
     * @param gracePeriod duration to keep stream alive after last subscriber
     * cancels
     * @param attributeFinder the PIP to connect immediately
     */
    public AttributeStream(@NonNull AttributeFinderInvocation invocation,
            @NonNull Consumer<AttributeStream> cleanupAction,
            @NonNull Duration gracePeriod,
            @NonNull AttributeFinder attributeFinder) {
        this.invocation    = invocation;
        this.cleanupAction = cleanupAction;
        this.configuredAttributeFinderStream.set(configureAttributeFinderStream(attributeFinder));
        this.stream = createMulticastStream(gracePeriod);
    }

    /**
     * Creates the multicast stream with unified reactive chain structure.
     * <p>
     * Chain structure: sink → [optional PIP start] → [grace period hooks] →
     * replay(1) → refCount(grace period) → subscribers
     * <p>
     * The hooks capture when refCount drops to zero after grace period expiration:
     * <ul>
     * <li>doOnSubscribe (if startPipOnSubscribe): starts PIP subscription when
     * replay connects to sink</li>
     * <li>doOnCancel: fires when grace period expires, snapshots current PIP and
     * calls cleanup action</li>
     * <li>doFinally: fires after cancellation, checks if PIP should be disposed
     * (not hot-swapped)</li>
     * </ul>
     *
     * @param gracePeriod duration to keep stream alive after last
     * subscriber cancels
     */
    private Flux<Val> createMulticastStream(Duration gracePeriod) {
        var flux = sink.asFlux();

        flux = flux.doOnSubscribe(subscription -> startPipSubscription());

        return flux.doOnCancel(() -> {
            synchronized (connectionLock) {
                log.debug("Last subscriber grace period expired for {}, cleanup triggered", this);
                hasActiveSubscribers = false;
                pipSnapshotAtGracePeriodStart.set(currentPipSubscription.get());
                cleanupAction.accept(this);
            }
        }).doFinally(signalType -> {
            synchronized (connectionLock) {
                log.debug("Stream finalized with signal {} for {}", signalType, this);
                maybeDisposePipAfterGracePeriod();
            }
        }).replay(1).refCount(1, gracePeriod);
    }

    /**
     * Returns the reactive stream for subscription.
     * <p>
     * Thread-safe with respect to disposal. If the stream has been disposed due to
     * grace period expiration, returns null to signal that a new stream should be
     * created by the broker.
     * <p>
     * Synchronizes on connectionLock to ensure atomic check of disposal state,
     * preventing race conditions where disposal completes between checking the
     * disposed flag and returning the stream reference.
     *
     * @return the Flux for subscription, or null if stream has been disposed
     */
    public Flux<Val> getStream() {
        synchronized (connectionLock) {
            if (disposed) {
                return null;
            }
            return stream;
        }
    }

    /**
     * Starts the PIP subscription when first subscriber arrives.
     * <p>
     * Marks that subscribers are active and attempts to start PIP subscription if
     * a PIP is configured. If no PIP is configured, publishes an error to inform
     * subscribers that no matching PIP was found for this invocation.
     * <p>
     * The subscription handlers are empty because values and errors are already
     * published to the sink via doOnNext and doOnError in the configured stream.
     */
    private void startPipSubscription() {
        hasActiveSubscribers = true;
        val pipStream = configuredAttributeFinderStream.get();
        if (pipStream != null) {
            synchronized (connectionLock) {
                if (currentPipSubscription.get() == null) {
                    log.debug("Starting PIP subscription for {}", this);
                    currentPipSubscription.set(pipStream.subscribe(value -> {}, error -> {}, () -> {}));
                }
            }
        } else {
            log.debug("No PIP configured for {}, publishing error", this);
            publish(Val.error("No unique policy information point found for " + invocation));
        }
    }

    /**
     * Disposes the PIP subscription if it wasn't hot-swapped during grace period.
     * <p>
     * Compares the snapshot taken when grace period started with the current PIP
     * subscription. If they're the same, no hot-swap occurred and the PIP should be
     * disposed. If different, a new PIP was connected during grace period and
     * should be preserved.
     * <p>
     * Sets the disposed flag to prevent further getStream() calls from returning
     * this stream instance.
     */
    private void maybeDisposePipAfterGracePeriod() {
        disposed = true;

        val snapshotPip = pipSnapshotAtGracePeriodStart.getAndSet(null);
        val currentPip  = currentPipSubscription.get();

        if (snapshotPip != null && snapshotPip == currentPip) {
            log.debug("Disposing PIP that was present at grace period start for {}", this);
            disposeCurrentPip();
        } else if (snapshotPip != null) {
            log.debug("PIP was hot-swapped during grace period for {}, preserving new PIP", this);
        }
    }

    /**
     * Atomically disposes the current PIP subscription.
     */
    private void disposeCurrentPip() {
        log.debug("Disposing current PIP subscription for {}", this);
        currentPipSubscription.getAndUpdate(subscription -> {
            if (subscription != null) {
                subscription.dispose();
            }
            return null;
        });
    }

    /**
     * Publishes a value to all active subscribers.
     * <p>
     * Package-private: intended for internal use by AttributeStream and related
     * broker components.
     *
     * @param value the value to publish
     */
    void publish(Val value) {
        log.debug("Publishing {} to {}", value, this);
        val emitResult = sink.tryEmitNext(value);
        if (emitResult.isFailure()) {
            log.warn("Failed to emit value {} to {} with result {}", value, this, emitResult);
        }
    }

    /**
     * Disconnects from the current PIP and publishes a disconnection error.
     * <p>
     * The stream remains active to support potential reconnection via
     * {@link #connectToPolicyInformationPoint(AttributeFinder)}.
     * <p>
     * Use case: PIP becomes unavailable (unregistered, disabled, configuration
     * removed).
     */
    public void disconnectFromPolicyInformationPoint() {
        synchronized (connectionLock) {
            log.debug("Disconnecting PIP from {}", this);
            disconnected = true;
            disposeCurrentPip();

            if (!disconnectErrorAlreadyPublished) {
                disconnectErrorAlreadyPublished = true;
                publish(Val.error("No policy information point found for " + invocation + " PIP disconnected."));
            } else {
                log.debug("Disconnect error already published for {}, skipping duplicate", this);
            }
        }
    }

    /**
     * Configures the reactive stream for a PIP by adding resilience features.
     * <p>
     * Applied transformations (in order):
     * <ul>
     * <li>defaultIfEmpty: converts empty streams to UNDEFINED</li>
     * <li>addInitialTimeout: emits UNDEFINED if first value takes too long</li>
     * <li>retryOnError: exponential backoff retry on error signals</li>
     * <li>pollOnComplete: re-subscribes after completion with polling
     * interval</li>
     * <li>onErrorResume: converts error signals (including from polling) to
     * Val.error() values</li>
     * <li>filter: prevents values from disconnected PIPs reaching the sink</li>
     * <li>doOnNext: publishes all values (including converted errors) to sink</li>
     * </ul>
     * <p>
     * The order matters:
     * <ul>
     * <li>Timeout applies only to the initial attempt, not the entire retry
     * sequence</li>
     * <li>Retry works on error signals before conversion to values</li>
     * <li>onErrorResume is after polling to catch any errors from repeated
     * subscriptions</li>
     * </ul>
     * <p>
     * By converting errors to Val.error() values after all reactive operations,
     * the final stream only emits values, ensuring clean error handling without
     * Reactor warnings.
     *
     * @param attributeFinder the PIP to configure
     * @return configured Flux that publishes to sink
     */
    private Flux<Val> configureAttributeFinderStream(AttributeFinder attributeFinder) {
        return attributeFinder.invoke(invocation).defaultIfEmpty(Val.UNDEFINED).transform(this::addInitialTimeout)
                .transform(this::retryOnError).transform(this::pollOnComplete)
                .onErrorResume(error -> Flux.just(Val.error(error.getMessage()))).filter(value -> !disconnected)
                .doOnNext(this::publish);
    }

    /**
     * Hot-swaps the PIP connection to a new AttributeFinder.
     * <p>
     * Thread-safe operation that:
     * <ul>
     * <li>Resets disconnection state</li>
     * <li>Stores the new PIP configuration for subscription</li>
     * <li>Subscribes immediately if: there are active PIP subscribers (hot-swap),
     * reconnecting after explicit disconnect, or subscribers are waiting</li>
     * <li>Defers subscription if: no subscribers yet (waits for first
     * subscriber)</li>
     * </ul>
     * <p>
     * The stream continues operating without interruption. Subscribers see values
     * from the new PIP.
     * <p>
     * Use case: Configuration update, PIP replacement, or recovery from
     * disconnection.
     *
     * @param policyInformationPoint the new PIP to connect
     */
    public void connectToPolicyInformationPoint(AttributeFinder policyInformationPoint) {
        synchronized (connectionLock) {
            log.debug("Connecting {} to {}", policyInformationPoint, this);

            val wasDisconnected = disconnected;
            disconnected                    = false;
            disconnectErrorAlreadyPublished = false;

            val newPipStream = configureAttributeFinderStream(policyInformationPoint);
            configuredAttributeFinderStream.set(newPipStream);

            val existingSubscription = currentPipSubscription.get();

            if (existingSubscription != null || wasDisconnected || hasActiveSubscribers) {
                log.debug("Subscribing PIP immediately for {}: hot-swap={}, reconnect={}, subscribers-waiting={}", this,
                        existingSubscription != null, wasDisconnected, hasActiveSubscribers);
                currentPipSubscription.getAndUpdate(oldSubscription -> {
                    if (oldSubscription != null) {
                        log.debug("Disposing old PIP subscription while hot-swapping for {}", this);
                        oldSubscription.dispose();
                    }
                    return newPipStream.subscribe(value -> {}, error -> {}, () -> {});
                });
            } else {
                log.debug("Storing PIP config for {}, will subscribe on first subscriber", this);
            }
        }
    }

    /**
     * Adds initial timeout handling.
     * <p>
     * Emits UNDEFINED if the first value doesn't arrive within the configured
     * timeout. The subscription remains active and the actual value will be emitted
     * when it arrives.
     * <p>
     * Applied before retry logic to ensure timeout only applies to the initial
     * attempt, not to the entire retry sequence.
     */
    private Flux<Val> addInitialTimeout(Flux<Val> attributeStream) {
        return TimeOutWrapper.wrap(attributeStream, invocation.initialTimeOut());
    }

    /**
     * Adds polling behavior for streams that complete.
     * <p>
     * After the PIP completes, waits for the polling interval then re-subscribes to
     * fetch fresh values. This supports attributes that need periodic
     * re-evaluation.
     */
    private Flux<Val> pollOnComplete(Flux<Val> attributeStream) {
        return attributeStream.repeatWhen(repeat -> repeat.delayElements(invocation.pollInterval()));
    }

    /**
     * Adds retry behavior with exponential backoff.
     * <p>
     * If retries=0, errors propagate immediately. Otherwise, retries the specified
     * number of times with exponential backoff delay.
     */
    private Flux<Val> retryOnError(Flux<Val> attributeStream) {
        if (invocation.retries() == 0) {
            return attributeStream;
        }
        return attributeStream.retryWhen(Retry.backoff(invocation.retries(), invocation.backoff()));
    }

}
