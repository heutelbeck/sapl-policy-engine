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
 * Subscriber-side helper for streams produced by the
 * {@link io.sapl.spring.method.metadata.StreamMode#ACCESS_AWARE} streaming
 * PEP. Wraps the {@code onErrorContinue} mechanics so callers can observe
 * boundary signals ({@link AccessDeniedException} on entry to denial,
 * {@link AccessGrantedException} on entry to permitting when
 * {@code signalAccessGranted} is enabled) without their stream terminating
 * on the first deny.
 * <p>
 * Errors that are <strong>not</strong> access-state signals propagate
 * normally: the stream terminates with the original error.
 *
 * @since 4.1.0
 */
@UtilityClass
public class RecoverableFluxes {

    /**
     * Drops both {@link AccessDeniedException} and
     * {@link AccessGrantedException} silently and continues the
     * subscription. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @return a flux that swallows access-state signals and continues
     */
    public static <T> Flux<T> recover(Flux<T> source) {
        return doRecover(source, null, null, null, null);
    }

    /**
     * Observes denial via {@code onDenied} (e.g., for logging) and continues.
     * Grant signals are silently dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onDenied side-effecting consumer invoked on denial boundary
     * @return a flux that observes denial and continues
     */
    public static <T> Flux<T> recover(Flux<T> source, Consumer<AccessDeniedException> onDenied) {
        return doRecover(source, onDenied, null, null, null);
    }

    /**
     * Observes both boundary directions and continues. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onDenied side-effecting consumer invoked on denial boundary
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @return a flux that observes both boundaries and continues
     */
    public static <T> Flux<T> recover(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Consumer<AccessGrantedException> onGranted) {
        return doRecover(source, onDenied, null, onGranted, null);
    }

    /**
     * Replaces the dropped item with a value supplied by {@code replacement}
     * on every denial boundary. Grant signals are silently dropped. Other
     * errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param replacement supplies a substitute value to emit on denial
     * @return a flux that emits the substitute value on denial
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Supplier<T> replacement) {
        return doRecover(source, null, replacement, null, null);
    }

    /**
     * Observes denial via {@code onDenied} and replaces the dropped item with
     * a value supplied by {@code replacement}. Grant signals are silently
     * dropped. Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onDenied side-effecting consumer invoked on denial boundary
     * @param replacement supplies a substitute value to emit on denial
     * @return a flux that observes denial and emits the substitute value
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement) {
        return doRecover(source, onDenied, replacement, null, null);
    }

    /**
     * Observes both boundaries and emits substitute values for each.
     * Other errors propagate.
     *
     * @param <T> the element type
     * @param source the streaming-PEP-protected source
     * @param onDenied side-effecting consumer invoked on denial boundary
     * @param deniedReplacement supplies a substitute value to emit on denial
     * @param onGranted side-effecting consumer invoked on grant boundary
     * @param grantedReplacement supplies a substitute value to emit on grant
     * @return a flux that observes both boundaries and emits substitutes
     */
    public static <T> Flux<T> recoverWith(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> deniedReplacement, Consumer<AccessGrantedException> onGranted, Supplier<T> grantedReplacement) {
        return doRecover(source, onDenied, deniedReplacement, onGranted, grantedReplacement);
    }

    private static <T> Flux<T> doRecover(Flux<T> source, Consumer<AccessDeniedException> onDenied,
            Supplier<T> deniedReplacement, Consumer<AccessGrantedException> onGranted, Supplier<T> grantedReplacement) {
        // A single onErrorContinue covers both signal types because Reactor
        // stores the on-next-error strategy under one context key and chained
        // type-specific calls overwrite each other.
        return Flux.deferContextual(contextView -> Flux.<T>create(downstream -> {
            val subscription = source.contextWrite(contextView).doOnNext(downstream::next)
                    .onErrorContinue(RecoverableFluxes::isAccessStateSignal, (error, value) -> {
                        if (error instanceof AccessDeniedException denial) {
                            handleDenied(denial, onDenied, deniedReplacement, downstream);
                        } else if (error instanceof AccessGrantedException grant) {
                            handleGranted(grant, onGranted, grantedReplacement, downstream);
                        }
                    }).doOnComplete(downstream::complete).doOnError(downstream::error).subscribe();
            downstream.onDispose(subscription);
        }));
    }

    private static <T> void handleDenied(AccessDeniedException denial, Consumer<AccessDeniedException> onDenied,
            Supplier<T> replacement, FluxSink<T> downstream) {
        if (onDenied != null) {
            onDenied.accept(denial);
        }
        if (replacement != null) {
            downstream.next(replacement.get());
        }
    }

    private static <T> void handleGranted(AccessGrantedException grant, Consumer<AccessGrantedException> onGranted,
            Supplier<T> replacement, FluxSink<T> downstream) {
        if (onGranted != null) {
            onGranted.accept(grant);
        }
        if (replacement != null) {
            downstream.next(replacement.get());
        }
    }

    private static boolean isAccessStateSignal(Throwable error) {
        return error instanceof AccessDeniedException || error instanceof AccessGrantedException;
    }
}
