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
package io.sapl.spring.constraints;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FunctionUtil {

    /**
     * Creates a {@link Consumer} doing nothing.
     *
     * @param <T> payload type
     * @return A {@link Consumer} doing nothing.
     */
    public static <T> Consumer<T> sink() {
        return t -> {
        };
    }

    /**
     * Creates a {@link LongConsumer} doing nothing.
     *
     * @return A {@link LongConsumer} doing nothing.
     */
    public static LongConsumer longSink() {
        return t -> {
        };
    }

    /**
     * Creates a {@link Predicate} which always returns {@code true}.
     *
     * @param <T> the payload type
     * @return a predicate that always returns {@code true}
     */
    public static <T> Predicate<T> all() {
        return t -> true;
    }

    /**
     * Creates a {@link Runnable} doing nothing.
     *
     * @return A {@link Runnable} doing nothing.
     */
    public static Runnable noop() {
        return () -> {
        };
    }
}
