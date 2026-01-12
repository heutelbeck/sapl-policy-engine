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
package io.sapl.attributes;

import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides utility functions for wrapping attribute streams with timeout
 * behavior.
 */
@Slf4j
@UtilityClass
public class TimeOutWrapper {

    /**
     * Wraps a flux with timeout behavior using Value.UNDEFINED as fallback values.
     * <p>
     * The wrapped flux ensures:
     * <ul>
     * <li>Empty metadata flux emits Value.UNDEFINED immediately</li>
     * <li>Timeout before first value emits Value.UNDEFINED, but continues emitting
     * subsequent values from the
     * metadata</li>
     * <li>Early completion terminates immediately without waiting for timeout</li>
     * </ul>
     *
     * @param flux
     * the metadata flux to wrap
     * @param timeOut
     * duration before emitting timeout value
     *
     * @return wrapped flux with timeout behavior
     */
    public static Flux<Value> wrap(Flux<Value> flux, Duration timeOut) {
        return wrap(flux, timeOut, Value.UNDEFINED, Value.UNDEFINED);
    }

    /**
     * Wraps a flux with timeout behavior using custom fallback values.
     * <p>
     * The wrapped flux ensures:
     * <ul>
     * <li>Empty metadata flux emits emptyFluxValue immediately</li>
     * <li>Timeout before first value emits timeOutValue, but continues emitting
     * subsequent values from the metadata</li>
     * <li>Early completion terminates immediately without waiting for timeout</li>
     * </ul>
     *
     * @param flux
     * the metadata flux to wrap
     * @param timeOut
     * duration before emitting timeout value
     * @param timeOutValue
     * value emitted on timeout
     * @param emptyFluxValue
     * value emitted when metadata is empty
     *
     * @return wrapped flux with timeout behavior
     */
    public static Flux<Value> wrap(Flux<Value> flux, Duration timeOut, Value timeOutValue, Value emptyFluxValue) {
        return Flux.defer(() -> {
            val sink                = Sinks.many().unicast().<Value>onBackpressureBuffer();
            val hasEmittedValue     = new AtomicBoolean(false);
            val timeoutWasCanceled  = new AtomicBoolean(false);
            val timeoutSubscription = new AtomicReference<Disposable>();
            val sourceSubscription  = new AtomicReference<Disposable>();

            val sourceFlux = flux.defaultIfEmpty(emptyFluxValue).doOnNext(value -> {
                hasEmittedValue.set(true);
                sink.tryEmitNext(value);
            }).doOnError(sink::tryEmitError).doOnComplete(() -> {
                timeoutWasCanceled.set(true);
                sink.tryEmitComplete();
            });

            val timeoutFlux = Mono.delay(timeOut).filter(tick -> !hasEmittedValue.get() && !timeoutWasCanceled.get())
                    .doOnNext(tick -> sink.tryEmitNext(timeOutValue));

            return sink.asFlux().doOnSubscribe(subscription -> {
                timeoutSubscription.set(timeoutFlux.subscribe(value -> log.trace("timeout flux: {}", value), error -> {
                    log.trace("Error in timeout flux", error);
                    sink.tryEmitError(error);
                }, () -> {}));

                sourceSubscription.set(sourceFlux.subscribe(value -> log.trace("metadata flux: {}", value), error ->
                // Already handled by doOnError(sink::tryEmitError)
                log.trace("Error in metadata flux", error), () -> {}));
            }).doOnTerminate(() -> {
                disposeIfPresent(timeoutSubscription);
                disposeIfPresent(sourceSubscription);
            }).doOnCancel(() -> {
                disposeIfPresent(timeoutSubscription);
                disposeIfPresent(sourceSubscription);
            });
        });
    }

    private static void disposeIfPresent(AtomicReference<Disposable> ref) {
        val disposable = ref.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

}
