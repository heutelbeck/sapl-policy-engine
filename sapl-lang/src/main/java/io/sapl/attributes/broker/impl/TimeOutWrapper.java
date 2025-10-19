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
import lombok.experimental.UtilityClass;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds utility functions for handling attribute time out events.
 */
@UtilityClass
class TimeOutWrapper {
    /**
     * This function wraps a raw Flux of Val and ensures the following invariants:
     * <p/>
     * a) If the source Flux is empty, the wrapped flux immediately emits a
     * Val.UNDIEFINED.
     * <p/>
     * b) If the source Flux does not emit a value before the provided time out
     * expires, the wrapped flux emits a Val.UNDEFINED but stays subscribed to the
     * source Flux and emits subsequent values whenever the source emits them, this
     * includes errors.
     * <p/>
     * c) If the source Flux emits values and completes the wrapped FLux also
     * immediately completes and does not stay alive until the time-out expires.
     * <p/>
     * @param flux a Flux of Val values.
     * @param timeOut The time-out before the wrapped flux emits an undefined value.
     * @return a flux identical to the original flux, that emits a Val.UNDEFINED
     * after the given time out expired if the original flux did not emit any value
     * until then.
     */
    public static Flux<Val> wrap(Flux<Val> flux, Duration timeOut) {
        return wrap(flux, timeOut, Val.UNDEFINED, Val.UNDEFINED);
    }

    /**
     * This function wraps a raw Flux of Val and ensures the following invariants:
     * <p/>
     * a) If the source Flux is empty, the wrapped flux immediately emits the
     * provided emptyFluxValue.
     * <p/>
     * b) If the source Flux does not emit a value before the provided time out
     * expires, the wrapped flux emits the provided timeOutValue but stays
     * subscribed to the source Flux and emits subsequent values whenever the source
     * emits them, this includes errors.
     * <p/>
     * c) If the source Flux emits values and completes the wrapped FLux also
     * immediately completes and does not stay alive until the time-out expires.
     *
     * @param flux a Flux of Val values.
     * @param timeOut The time-out before the wrapped flux emits an undefined value.
     * @param timeOutValue the value emitted when a time-out occurs. E.g.,
     * Val.UNDEFINED or a Val.error(...).
     * @param emptyFluxValue the value emitted when the original flux is empty.
     * E.g., Val.UNDEFINED or a Val.error(...).
     * @return a flux identical to the original flux, that emits a Val.UNDEFINED
     * after the given time out expired if the original flux did not emit any value
     * until then.
     */
    public static Flux<Val> wrap(Flux<Val> flux, Duration timeOut, Val timeOutValue, Val emptyFluxValue) {
        Many<Event> mergedSink          = Sinks.many().unicast().onBackpressureBuffer();
        final var   timeoutSubscription = new AtomicReference<Disposable>(null);
        final var   valuesSubscription  = new AtomicReference<Disposable>(null);

        final var timeout = Mono.just(Event.TIMEOUT).delayElement(timeOut).doOnNext(mergedSink::tryEmitNext);
        // @formatter:off
        final var values  = flux.defaultIfEmpty(emptyFluxValue)
                                .doOnNext(v -> dispose(timeoutSubscription))
                                .doOnError(e -> dispose(timeoutSubscription))
                                .doOnComplete(() -> dispose(timeoutSubscription))
                                .doOnTerminate(() -> dispose(timeoutSubscription))
                                .map(Event::new)
                                .doOnNext(mergedSink::tryEmitNext)
                                .doOnError(mergedSink::tryEmitError)
                                .doOnComplete(mergedSink::tryEmitComplete)
                                .onErrorComplete();
        // @formatter:on

        // Do not use Flux.merge. If doing so, one cannot cancel the time-out if the
        // original Flux ends before the time-out happens. This way the disposable is
        // accessible and the time-out can be disposed.
        final var mergedFlux = mergedSink.asFlux().doOnSubscribe(s -> {
            timeoutSubscription.set(timeout.subscribe());
            valuesSubscription.set(values.subscribe());
        }).doOnTerminate(() -> {
            dispose(timeoutSubscription);
            dispose(valuesSubscription);
        }).doOnCancel(() -> {
            dispose(timeoutSubscription);
            dispose(valuesSubscription);
        });

        return mergedFlux
                .scan(new LastAndCurrent(null, null),
                        (previous, timeOutOrValue) -> new LastAndCurrent(previous.current(), timeOutOrValue))
                .filter(t -> t.current != null && (!t.current.isTimeOut() || t.last == null))
                .map(LastAndCurrent::current)
                .map(timeOutOrValue -> timeOutOrValue.isTimeOut() ? timeOutValue : timeOutOrValue.value);
    }

    /**
     * Dispose the references Disposable if present and not yet disposed.
     *
     * @param atomicDisposable a reference to a disposable.
     */
    private static void dispose(AtomicReference<Disposable> atomicDisposable) {
        var disposable = atomicDisposable.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private record Event(Val value) {
        public static final Event TIMEOUT = new Event(null);

        public boolean isTimeOut() {
            return value == null;
        }
    }

    private record LastAndCurrent(Event last, Event current) {}

}
