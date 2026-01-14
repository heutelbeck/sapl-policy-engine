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

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Array value implementing List semantics. The underlying list is immutable.
 * <p>
 * Direct list usage:
 *
 * <pre>{@code
 * ArrayValue permissions = Value.ofArray(Value.of("read"), Value.of("write"), Value.of("delete"));
 * Value first = permissions.get(0);
 * for (Value perm : permissions) {
 *     grantPermission(perm);
 * }
 * }</pre>
 * <p>
 * Builder for fluent construction:
 *
 * <pre>{@code
 * ArrayValue roles = ArrayValue.builder().add(Value.of("admin")).add(Value.of("user")).build();
 * }</pre>
 * <p>
 * Error handling:
 *
 * <pre>{@code
 * Value result = array.get(999);
 * if (result instanceof ErrorValue error) {
 *     log.warn("Index out of bounds: {}", error.message());
 * }
 * }</pre>
 */
public final class ArrayValue implements Value, List<Value> {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    @Delegate(excludes = ExcludedMethods.class)
    private final List<Value> value;

    public ArrayValue(@NonNull List<Value> elements) {
        this.value = Collections.unmodifiableList(elements);
    }

    public ArrayValue(@NonNull Value[] elements) {
        this(List.of(elements));
    }

    /**
     * Creates a builder for fluent array construction.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for fluent ArrayValue construction.
     * <p>
     * Builders are single-use only. After calling build(), the builder cannot be
     * reused. This prevents immutability
     * violations from the zero-copy optimization.
     */
    public static final class Builder {
        private ArrayList<Value> elements = new ArrayList<>();

        /**
         * Adds a value to the array.
         *
         * @param value
         * the value to add
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder add(Value value) {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            elements.add(value);
            return this;
        }

        /**
         * Adds multiple values to the array.
         *
         * @param values
         * the values to add
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder addAll(Value... values) {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            for (var value : values) {
                add(value);
            }
            return this;
        }

        /**
         * Adds multiple values to the array.
         *
         * @param values
         * the values to add
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder addAll(Collection<? extends Value> values) {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            for (var value : values) {
                add(value);
            }
            return this;
        }

        /**
         * Builds the immutable ArrayValue. Returns singleton for empty non-secret
         * arrays.
         * <p>
         * At build time, the aggregated metadata is propagated to all elements for
         * consistent access. After calling
         * this method, the builder cannot be reused.
         *
         * @return the constructed ArrayValue
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public ArrayValue build() {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (elements.isEmpty()) {
                elements = null;
                return Value.EMPTY_ARRAY;
            }
            val result = new ArrayValue(elements);
            elements = null;
            return result;
        }
    }

    @Override
    public @NotNull String toString() {
        return '[' + value.stream().map(Value::toString).collect(Collectors.joining(", ")) + ']';
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof List<?> thatList))
            return false;
        return value.equals(thatList);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Returns the element at the specified position.
     * <p>
     * Returns ErrorValue for invalid indices instead of throwing
     * IndexOutOfBoundsException, consistent with SAPL's
     * error-as-value model. Elements already have the container's metadata applied.
     *
     * @param index
     * index of the element
     *
     * @return the element, or ErrorValue if index is out of bounds
     */
    @Override
    public @NotNull Value get(int index) {
        if (index < 0 || index >= value.size()) {
            return new ErrorValue("Array index out of bounds: %d (size: %d).".formatted(index, value.size()));
        }
        return value.get(index);
    }

    /**
     * Returns the first element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException, consistent with SAPL's
     * error-as-value model. Elements already have the container's metadata applied.
     *
     * @return the first element, or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getFirst() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get first element of empty array.");
        }
        return value.getFirst();
    }

    /**
     * Returns the last element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException, consistent with SAPL's
     * error-as-value model. Elements already have the container's metadata applied.
     *
     * @return the last element, or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getLast() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get last element of empty array.");
        }
        return value.getLast();
    }

    /**
     * Returns a view of the portion between the specified indices. The returned
     * sublist preserves the container's
     * metadata.
     *
     * @param fromIndex
     * low endpoint (inclusive)
     * @param toIndex
     * high endpoint (exclusive)
     *
     * @return a sublist as ArrayValue with metadata propagated
     */
    @Override
    public @NotNull List<Value> subList(int fromIndex, int toIndex) {
        return new ArrayValue(value.subList(fromIndex, toIndex));
    }

    /**
     * Methods excluded from Lombok delegation to preserve Value semantics. These
     * methods require custom implementations
     * for error handling.
     */
    private interface ExcludedMethods {
        boolean equals(Object obj);

        int hashCode();

        String toString();

        Value get(int index);

        Value getFirst();

        Value getLast();

        List<Value> subList(int fromIndex, int toIndex);
    }
}
