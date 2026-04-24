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
package io.sapl.spring.pep.constraints;

import java.util.Optional;
import java.util.Set;

import org.springframework.core.ResolvableType;

import lombok.val;

/**
 * Reified key identifying a {@link Signal} for plan lookup. Two signal types
 * are equal when their signal class and (for value signals) their
 * {@link ResolvableType} match. Using {@link ResolvableType} preserves generic
 * information so that providers may dispatch on
 * {@code Mono<String>} differently from {@code Mono<User>}.
 */
sealed public interface SignalType permits SignalType.VoidSignalType, SignalType.ValueSignalType {

    record VoidSignalType(Class<? extends Signal.VoidSignal> type) implements SignalType {}

    record ValueSignalType<T>(Class<? extends Signal.ValueSignal<T>> type, ResolvableType valueType)
            implements SignalType {}

    /**
     * Returns the first {@link ValueSignalType} in {@code supported} whose
     * signal class equals {@code signalClass}, or {@link Optional#empty()} when
     * the PEP does not fire that signal. Used by providers that need to bind a
     * handler to a specific value signal contributed by the surrounding PEP.
     */
    static Optional<ValueSignalType<?>> findIn(Set<SignalType> supported, Class<? extends Signal> signalClass) {
        for (val signal : supported) {
            if (signal instanceof ValueSignalType<?> v && signalClass.equals(v.type())) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }
}
