/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
 * Secret containers eagerly apply the secret flag to all contained values at
 * construction time, enabling zero-overhead reads and optimal performance for
 * recursive operations.
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
 * ArrayValue roles = ArrayValue.builder().add(Value.of("admin")).add(Value.of("user")).secret().build();
 * }</pre>
 * <p>
 * Secret propagation:
 *
 * <pre>{@code
 * ArrayValue tokens = array.asSecret();
 * Value token = tokens.get(0); // Already secret
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
    private final boolean     secret;

    /**
     * Creates an ArrayValue.
     * <p>
     * If secret is true, applies the secret flag to all elements at construction
     * time for optimal read performance.
     * <p>
     * Note: This does a defensive copy of the supplied List. For better performance
     * without the need to copy use the Builder!
     *
     * @param elements the list elements (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ArrayValue(@NonNull List<Value> elements, boolean secret) {
        if (secret) {
            var secretList = new ArrayList<Value>();
            for (Value element : elements) {
                secretList.add(element.asSecret());
            }
            this.value = Collections.unmodifiableList(secretList);
        } else {
            this.value = List.copyOf(elements);
        }
        this.secret = secret;
    }

    /**
     * Creates an ArrayValue.
     * <p>
     * If secret is true, applies the secret flag to all elements at construction
     * time for optimal read performance.
     *
     * @param elements the list elements (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ArrayValue(@NonNull Value[] elements, boolean secret) {
        if (secret) {
            var secretList = new ArrayList<Value>();
            for (Value element : elements) {
                secretList.add(element.asSecret());
            }
            this.value = Collections.unmodifiableList(secretList);
        } else {
            this.value = List.of(elements);
        }
        this.secret = secret;
    }

    /**
     * Zero-copy constructor for builder use only.
     * The supplied list is used directly without copying.
     *
     * @param secret whether this value is secret
     * @param elements the list elements (used directly, must be mutable until
     * wrapped)
     */
    private ArrayValue(boolean secret, List<Value> elements) {
        this.value  = Collections.unmodifiableList(elements);
        this.secret = secret;
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
     * reused. This prevents immutability violations from the zero-copy
     * optimization.
     */
    public static final class Builder {
        private ArrayList<Value> elements = new ArrayList<>();
        private boolean          secret   = false;

        /**
         * Adds a value to the array.
         *
         * @param value the value to add
         * @return this builder
         * @throws IllegalStateException if builder has already been used
         */
        public Builder add(Value value) {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (secret) {
                value = value.asSecret();
            }
            elements.add(value);
            return this;
        }

        /**
         * Adds multiple values to the array.
         *
         * @param values the values to add
         * @return this builder
         * @throws IllegalStateException if builder has already been used
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
         * @param values the values to add
         * @return this builder
         * @throws IllegalStateException if builder has already been used
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
         * Marks the array as secret.
         * Elements will have the secret flag applied at construction.
         * <p>
         * Note: For performance reasons, if possible call this method as early as
         * possible in the building process.
         *
         * @return this builder
         * @throws IllegalStateException if builder has already been used
         */
        public Builder secret() {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (!this.secret) {
                this.secret = true;
                elements.replaceAll(Value::asSecret);
            }
            return this;
        }

        /**
         * Builds the immutable ArrayValue.
         * Returns singleton for empty non-secret arrays.
         * <p>
         * After calling this method, the builder cannot be reused. Attempting to call
         * any builder methods after build() will throw IllegalStateException.
         *
         * @return the constructed ArrayValue
         * @throws IllegalStateException if builder has already been used
         */
        public ArrayValue build() {
            if (elements == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (elements.isEmpty() && !secret) {
                elements = null;
                return Value.EMPTY_ARRAY;
            }
            var result = new ArrayValue(secret, elements);
            elements = null;
            return result;
        }
    }

    @Override
    public boolean secret() {
        return secret;
    }

    @Override
    public Value asSecret() {
        return secret ? this : new ArrayValue(value, true);
    }

    @Override
    public @NotNull String toString() {
        if (secret) {
            return SECRET_PLACEHOLDER;
        }
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
     * IndexOutOfBoundsException, consistent with SAPL's error-as-value model.
     * Elements already have the secret flag applied if container is secret.
     *
     * @param index index of the element
     * @return the element, or ErrorValue if index is out of bounds
     */
    @Override
    public @NotNull Value get(int index) {
        if (index < 0 || index >= value.size()) {
            return new ErrorValue("Array index out of bounds: " + index + " (size: " + value.size() + ").", secret);
        }
        return value.get(index);
    }

    /**
     * Returns the first element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException, consistent with SAPL's error-as-value model.
     * Elements already have the secret flag applied if container is secret.
     *
     * @return the first element, or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getFirst() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get first element of empty array.", secret);
        }
        return value.getFirst();
    }

    /**
     * Returns the last element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException, consistent with SAPL's error-as-value model.
     * Elements already have the secret flag applied if container is secret.
     *
     * @return the last element, or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getLast() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get last element of empty array.", secret);
        }
        return value.getLast();
    }

    /**
     * Returns a view of the portion between the specified indices.
     * The returned sublist propagates the secret flag.
     *
     * @param fromIndex low endpoint (inclusive)
     * @param toIndex high endpoint (exclusive)
     * @return a sublist as ArrayValue with secret flag propagated
     */
    @Override
    public @NotNull List<Value> subList(int fromIndex, int toIndex) {
        return new ArrayValue(value.subList(fromIndex, toIndex), secret);
    }

    /**
     * Methods excluded from Lombok delegation to preserve Value semantics.
     * These methods require custom implementations for error handling.
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
