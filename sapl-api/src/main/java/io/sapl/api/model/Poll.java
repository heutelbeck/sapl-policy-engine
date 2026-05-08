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
package io.sapl.api.model;

/**
 * Result of a non-blocking poll on a {@link Stream}. Distinguishes
 * the three observable outcomes: a value was available, no value was
 * available right now but the stream is still open, or the stream
 * has completed and no further values will arrive.
 *
 * @param <T> the value type carried by the polled stream
 *
 * @since 4.1.0
 */
public sealed interface Poll<T> {

    record Value<T>(T value) implements Poll<T> {}

    record Empty<T>() implements Poll<T> {}

    record Done<T>() implements Poll<T> {}

    /**
     * Wraps {@code value} as {@link Value}.
     */
    static <T> Poll<T> value(T value) {
        return new Value<>(value);
    }

    /**
     * Returns the {@link Empty} marker: no value was available at the
     * time of the call, but the stream is still open and may yield a
     * value in the future.
     */
    static <T> Poll<T> empty() {
        return new Empty<>();
    }

    /**
     * Returns the {@link Done} marker: the stream has completed and
     * no further values will be produced.
     */
    static <T> Poll<T> done() {
        return new Done<>();
    }
}
