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

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Utility class for handling recoverable access denied errors in reactive
 * streams.
 * <p>
 * When using {@code @EnforceRecoverableIfDenied}, the PEP emits
 * {@link AccessDeniedException} errors that are designed to work with
 * {@code onErrorContinue}. However, {@code onErrorContinue} only supports
 * side-effects and cannot emit replacement values.
 * <p>
 * This utility bridges that gap by allowing errors to be transformed into
 * values while maintaining proper subscription lifecycle.
 */
@UtilityClass
public class RecoverableFluxes {

    /**
     * Recovers from {@link AccessDeniedException} by dropping the error and
     * continuing the subscription. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access denied errors
     * @return a flux that drops access denied errors and continues
     */
    public static <T> Flux<T> recover(Flux<T> source) {
        return doRecover(source, null, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} by executing a side-effect (e.g.,
     * logging) and dropping the error. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access denied errors
     * @param onDenied consumer for the access denied error (e.g., for logging)
     * @return a flux that handles access denied errors with the consumer and
     * continues
     */
    public static <T> Flux<T> recover(Flux<T> source, Consumer<AccessDeniedException> onDenied) {
        return doRecover(source, onDenied, null);
    }

    /**
     * Recovers from {@link AccessDeniedException} by emitting a replacement value.
     * Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access denied errors
     * @param replacement supplier for the replacement value to emit on access
     * denied
     * @return a flux that emits replacement values instead of access denied errors
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Supplier<T> replacement) {
        return doRecover(source, null, replacement);
    }

    /**
     * Recovers from {@link AccessDeniedException} by executing a side-effect and
     * emitting a replacement value. Other errors are propagated.
     *
     * @param <T> the element type
     * @param source the source flux with recoverable access denied errors
     * @param onDenied consumer for the access denied error (e.g., for logging)
     * @param replacement supplier for the replacement value to emit on access
     * denied
     * @return a flux that handles access denied errors and emits replacements
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement) {
        return doRecover(source, onDenied, replacement);
    }

    private static <T> Flux<T> doRecover(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement) {
        return Flux.create(sink -> {
            Disposable subscription = source.doOnNext(sink::next)
                    .onErrorContinue(AccessDeniedException.class, (error, value) -> {
                        if (onDenied != null) {
                            onDenied.accept((AccessDeniedException) error);
                        }
                        if (replacement != null) {
                            sink.next(replacement.get());
                        }
                    }).doOnComplete(sink::complete).doOnError(sink::error).subscribe();
            sink.onDispose(subscription);
        });
    }

}
