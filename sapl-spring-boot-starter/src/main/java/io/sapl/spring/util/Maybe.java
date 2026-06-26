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
package io.sapl.spring.util;

/**
 * Container that distinguishes {@link Absent} from {@link Present} where the
 * present value may itself be {@code null}.
 */
public sealed interface Maybe<T> {

    record Present<T>(T value) implements Maybe<T> {}

    record Absent<T>() implements Maybe<T> {}

    /**
     * Wraps {@code value} as {@link Present}; {@code null} is a legal payload
     * distinct from {@link Absent}.
     */
    static <T> Maybe<T> of(T value) {
        return new Present<>(value);
    }

    /**
     * Returns the {@link Absent} sentinel: no value is present (semantically
     * distinct from {@code Present(null)}).
     */
    static <T> Maybe<T> absent() {
        return new Absent<>();
    }
}
