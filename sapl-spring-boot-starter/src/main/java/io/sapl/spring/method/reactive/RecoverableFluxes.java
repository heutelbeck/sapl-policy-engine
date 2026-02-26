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
package io.sapl.spring.method.reactive;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class for handling recoverable access state signals in reactive
 * streams.
 * <p>
 * When using {@code @EnforceRecoverableIfDenied}, the PEP emits
 * {@link AccessDeniedException} on deny and optionally
 * {@link AccessRecoveredException} on recovery (when
 * {@code signalAccessRecovery = true}). Both are delivered through
 * {@code onErrorContinue}. However, {@code onErrorContinue} only supports
 * side-effects and cannot emit replacement values.
 * <p>
 * This utility bridges that gap by allowing errors to be transformed into
 * values while maintaining proper subscription lifecycle.
 */
@UtilityClass
public class RecoverableFluxes {

    /**
     * Recovers from {@link AccessDeniedException} and
     * {@link AccessRecoveredException} by dropping the signal and continuing the
     * subscription. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @return a flux that drops access state signals and continues
     */
    public static <T> Flux<T> recover(Flux<T> source) {
        return doRecover(source, null, null, null, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} by executing a side-effect
     * (e.g., logging) and dropping the error. {@link AccessRecoveredException}
     * signals are silently dropped. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @param onDenied consumer for the access denied error (e.g., for logging)
     * @return a flux that handles access denied errors with the consumer and
     * continues
     */
    public static <T> Flux<T> recover(Flux<T> source, Consumer<AccessDeniedException> onDenied) {
        return doRecover(source, onDenied, null, null, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} and
     * {@link AccessRecoveredException} by executing side-effects and dropping
     * the signals. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @param onDenied consumer for the access denied signal (e.g., for logging)
     * @param onRecovered consumer for the access recovered signal
     * @return a flux that handles both access state signals and continues
     */
    public static <T> Flux<T> recover(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Consumer<AccessRecoveredException> onRecovered) {
        return doRecover(source, onDenied, null, onRecovered, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} by emitting a replacement
     * value. {@link AccessRecoveredException} signals are silently dropped.
     * Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @param replacement supplier for the replacement value to emit on access
     * denied
     * @return a flux that emits replacement values instead of access denied
     * errors
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Supplier<T> replacement) {
        return doRecover(source, null, replacement, null, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} by executing a side-effect
     * and emitting a replacement value. {@link AccessRecoveredException} signals
     * are silently dropped. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @param onDenied consumer for the access denied error (e.g., for logging)
     * @param replacement supplier for the replacement value to emit on access
     * denied
     * @return a flux that handles access denied errors and emits replacements
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement) {
        return doRecover(source, onDenied, replacement, null, null);
    }

    /**
     * Recovers from both {@link AccessDeniedException} and
     * {@link AccessRecoveredException} by executing side-effects and emitting
     * replacement values. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access state signals
     * @param onDenied consumer for the access denied signal (e.g., for logging)
     * @param deniedReplacement supplier for the replacement value on access
     * denied
     * @param onRecovered consumer for the access recovered signal
     * @param recoveredReplacement supplier for the replacement value on access
     * recovered
     * @return a flux that handles both signals with consumers and replacements
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> deniedReplacement, Consumer<AccessRecoveredException> onRecovered,
            Supplier<T> recoveredReplacement) {
        return doRecover(source, onDenied, deniedReplacement, onRecovered, recoveredReplacement);
    }

    private static <T> Flux<T> doRecover(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> deniedReplacement, Consumer<AccessRecoveredException> onRecovered,
            Supplier<T> recoveredReplacement) {
        // A single onErrorContinue is required because Reactor uses one context key
        // (OnNextFailureStrategy.KEY_ON_NEXT_ERROR_STRATEGY) for all onErrorContinue
        // handlers. Chaining multiple type-specific onErrorContinue calls causes the
        // inner one to replace the outer, silently losing error handling for one type.
        return Flux.deferContextual(contextView -> Flux.create(sink -> {
            val subscription = source.contextWrite(contextView).doOnNext(sink::next)
                    .onErrorContinue(RecoverableFluxes::isAccessStateSignal, (error, value) -> {
                        if (error instanceof AccessDeniedException ade) {
                            handleDenied(ade, onDenied, deniedReplacement, sink);
                        } else if (error instanceof AccessRecoveredException are) {
                            handleRecovered(are, onRecovered, recoveredReplacement, sink);
                        }
                    }).doOnComplete(sink::complete).doOnError(sink::error).subscribe();
            sink.onDispose(subscription);
        }));
    }

    private static <T> void handleDenied(AccessDeniedException ade, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement, FluxSink<T> sink) {
        if (onDenied != null) {
            onDenied.accept(ade);
        }
        if (replacement != null) {
            sink.next(replacement.get());
        }
    }

    private static <T> void handleRecovered(AccessRecoveredException are,
            Consumer<AccessRecoveredException> onRecovered, Supplier<T> replacement, FluxSink<T> sink) {
        if (onRecovered != null) {
            onRecovered.accept(are);
        }
        if (replacement != null) {
            sink.next(replacement.get());
        }
    }

    private static boolean isAccessStateSignal(Throwable error) {
        return error instanceof AccessDeniedException || error instanceof AccessRecoveredException;
    }

}
