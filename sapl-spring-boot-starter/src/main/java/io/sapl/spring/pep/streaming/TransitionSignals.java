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
package io.sapl.spring.pep.streaming;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Subscriber-side helpers for streams produced by a method annotated with
 * {@link io.sapl.spring.method.metadata.StreamEnforce
 * &#64;StreamEnforce(signalTransitions = true)}. The PEP surfaces every
 * suspend / resume boundary as a non-terminal exception on the error
 * channel ({@link AccessDeniedException} for suspend boundaries,
 * {@link AccessGrantedException} for grant boundaries) so subscribers can
 * react to them without their stream terminating on the first signal.
 * <p>
 * The methods here translate those non-terminal signals into ordinary
 * callbacks (and, optionally, into a substitute item to emit downstream
 * in place of the signal). Errors that are <strong>not</strong>
 * boundary signals propagate normally and terminate the stream.
 *
 * @since 4.1.0
 */
@UtilityClass
public class TransitionSignals {

    /**
     * Observes suspend boundaries via {@code onSuspend} and continues.
     * Grant boundaries are silently dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onSuspend side-effecting consumer invoked on suspend boundary
     * @return a flux that observes suspend boundaries and continues
     */
    public static <T> Flux<T> onSuspend(Flux<T> source, Consumer<AccessDeniedException> onSuspend) {
        return observeOnly(source, onSuspend, null);
    }

    /**
     * Observes suspend boundaries via {@code onSuspend} and emits the
     * value supplied by {@code emit} downstream in place of the signal.
     * Grant boundaries are silently dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onSuspend side-effecting consumer invoked on suspend boundary
     * @param emit supplies a substitute value to emit on suspend
     * @return a flux that observes suspend boundaries and emits the substitute
     */
    public static <T> Flux<T> onSuspend(Flux<T> source, Consumer<AccessDeniedException> onSuspend, Supplier<T> emit) {
        return observeAndSubstitute(source, onSuspend, emit, null, null);
    }

    /**
     * Observes grant boundaries via {@code onGranted} and continues.
     * Suspend boundaries are silently dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @return a flux that observes grant boundaries and continues
     */
    public static <T> Flux<T> onGranted(Flux<T> source, Consumer<AccessGrantedException> onGranted) {
        return observeOnly(source, null, onGranted);
    }

    /**
     * Observes grant boundaries via {@code onGranted} and emits the
     * value supplied by {@code emit} downstream in place of the signal.
     * Suspend boundaries are silently dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @param emit supplies a substitute value to emit on grant
     * @return a flux that observes grant boundaries and emits the substitute
     */
    public static <T> Flux<T> onGranted(Flux<T> source, Consumer<AccessGrantedException> onGranted, Supplier<T> emit) {
        return observeAndSubstitute(source, null, null, onGranted, emit);
    }

    /**
     * Observes both boundary directions in a single call. Other errors
     * propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onSuspend side-effecting consumer invoked on suspend boundary
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @return a flux that observes both boundaries and continues
     */
    public static <T> Flux<T> onTransitions(Flux<T> source, Consumer<AccessDeniedException> onSuspend,
            Consumer<AccessGrantedException> onGranted) {
        return observeOnly(source, onSuspend, onGranted);
    }

    /**
     * Observes both boundary directions and emits a substitute item per
     * direction. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onSuspend side-effecting consumer invoked on suspend boundary
     * @param emitOnSuspend supplies a substitute value to emit on suspend
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @param emitOnGranted supplies a substitute value to emit on grant
     * @return a flux that observes both boundaries and emits substitutes
     */
    public static <T> Flux<T> onTransitions(Flux<T> source, Consumer<AccessDeniedException> onSuspend,
            Supplier<T> emitOnSuspend, Consumer<AccessGrantedException> onGranted, Supplier<T> emitOnGranted) {
        return observeAndSubstitute(source, onSuspend, emitOnSuspend, onGranted, emitOnGranted);
    }

    /**
     * Observe-only path: a chained {@code onErrorContinue} on the
     * source. No extra {@code Flux.create}, no extra subscription,
     * full backpressure preserved. Suitable when no substitute item
     * needs to be injected on a boundary.
     */
    private static <T> Flux<T> observeOnly(Flux<T> source, Consumer<AccessDeniedException> onSuspend,
            Consumer<AccessGrantedException> onGranted) {
        // A single onErrorContinue covers both signal types because Reactor
        // stores the on-next-error strategy under one context key and chained
        // type-specific calls overwrite each other.
        return Flux.deferContextual(contextView -> source.contextWrite(contextView)
                .onErrorContinue(TransitionSignals::isBoundarySignal, (error, value) -> {
                    if (onSuspend != null && error instanceof AccessDeniedException suspended) {
                        onSuspend.accept(suspended);
                    } else if (onGranted != null && error instanceof AccessGrantedException granted) {
                        onGranted.accept(granted);
                    }
                }));
    }

    /**
     * Substitution path: a wrapping {@code Flux.create} sink so the
     * boundary-error callback can {@code sink.next(emit.get())} a
     * substitute item. This costs one extra subscription and
     * {@code OverflowStrategy.BUFFER} on the sink (the
     * {@code Flux.create} default); the inner subscription is bound
     * to the sink's lifetime via {@code sink.onDispose(...)}, so a
     * downstream cancel propagates upstream.
     */
    private static <T> Flux<T> observeAndSubstitute(Flux<T> source, Consumer<AccessDeniedException> onSuspend,
            Supplier<T> emitOnSuspend, Consumer<AccessGrantedException> onGranted, Supplier<T> emitOnGranted) {
        return Flux.deferContextual(contextView -> Flux.create(downstream -> {
            val subscription = source.contextWrite(contextView).doOnNext(downstream::next)
                    .onErrorContinue(TransitionSignals::isBoundarySignal, (error, value) -> {
                        if (error instanceof AccessDeniedException suspended) {
                            handleBoundary(suspended, onSuspend, emitOnSuspend, downstream);
                        } else if (error instanceof AccessGrantedException granted) {
                            handleBoundary(granted, onGranted, emitOnGranted, downstream);
                        }
                    }).doOnComplete(downstream::complete).doOnError(downstream::error).subscribe();
            downstream.onDispose(subscription);
        }));
    }

    private static <E extends Throwable, T> void handleBoundary(E signal, Consumer<E> observer, Supplier<T> emit,
            FluxSink<T> downstream) {
        if (observer != null) {
            observer.accept(signal);
        }
        if (emit != null) {
            downstream.next(emit.get());
        }
    }

    private static boolean isBoundarySignal(Throwable error) {
        return error instanceof AccessDeniedException || error instanceof AccessGrantedException;
    }
}
