/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.NoSuchElementException;

import javax.annotation.Nonnull;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

/**
 * A wrapper class to enable onErrorContinue with in protected Flux processing.
 *
 * @param <P> Payload type
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class ProtectedPayload<P> {
    private final P         payload;
    private final Throwable error;

    /**
     * Creates a ProtectedPayload
     *
     * @param <T> the payload type.
     * @param payload a payload
     * @return a ProtectedPayload containing the payload value
     */
    public static <T> ProtectedPayload<T> withPayload(@NonNull T payload) {
        return new ProtectedPayload<>(payload, null);
    }

    /**
     * Creates a ProtectedPayload
     *
     * @param <T> the payload type.
     * @param exception an Exception
     * @return a ProtectedPayload containing the exception
     */
    public static <T> ProtectedPayload<T> withError(@NonNull Throwable exception) {
        return new ProtectedPayload<>(null, exception);
    }

    /**
     * Get the payload or throw Exception.
     * <p>
     * Explanation: Why is this a Mono<>? Answer: Because onErrorContinue does no
     * longer work with map but only with flatMap
     *
     * @return a Mono of th payload
     */
    @SneakyThrows
    public Mono<P> getPayload() {
        if (error != null)
            throw error;
        return Mono.just(payload);
    }

    /**
     * @return true, if this wraps an Exception and not a payload.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * @return true, if this wraps a payload.
     */
    public boolean hasPayload() {
        return payload != null;
    }

    /**
     * @return the wrapped exception, or NoSuchElementException
     */
    @Nonnull
    public Throwable getError() {
        if (error == null)
            throw new NoSuchElementException("Protected payload does not wrap an exception.");
        return error;
    }

}
