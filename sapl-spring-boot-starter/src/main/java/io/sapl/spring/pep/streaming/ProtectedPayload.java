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

import org.jspecify.annotations.Nullable;

import reactor.core.publisher.Mono;

/**
 * Internal wrapper used by {@link StreamingPipeline} to carry either a data
 * value or a non-terminal error through the same {@code Flux.create} sink.
 * The pipeline pushes wrapped instances onto the sink; the chain terminates
 * with {@code .flatMap(ProtectedPayload::unwrap)} which either re-emits the
 * value via {@link Mono#just} or raises the error via {@link Mono#error}.
 * <p>
 * The {@code flatMap}-with-{@code Mono.error} pattern is what makes the
 * subscriber's {@code onErrorContinue} actually catch the error and continue
 * the subscription. Errors that surface from the upstream sink directly
 * (e.g., {@code FluxSink.error}) are terminal and not recoverable; only
 * errors raised inside an operator's per-item processing are eligible for
 * {@code onErrorContinue}. Wrapping the error as a value, then re-throwing
 * it inside {@code flatMap}, satisfies that requirement.
 *
 * @since 4.1.0
 */
record ProtectedPayload<T>(@Nullable T value, @Nullable Throwable error) {

    static <T> ProtectedPayload<T> ofValue(T value) {
        return new ProtectedPayload<>(value, null);
    }

    static <T> ProtectedPayload<T> ofError(Throwable error) {
        return new ProtectedPayload<>(null, error);
    }

    Mono<T> unwrap() {
        if (error != null) {
            return Mono.error(error);
        }
        return Mono.justOrEmpty(value);
    }
}
