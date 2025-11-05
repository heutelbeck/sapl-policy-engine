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
package io.sapl.api.value;

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Array value implementing List semantics. The underlying list is immutable.
 * <p>
 * Elements from secret containers inherit the secret flag. Operations that
 * would
 * normally throw exceptions return ErrorValue instead, consistent with SAPL's
 * error-as-value pattern.
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
 * Value token = tokens.get(0); // Also secret
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
     *
     * @param elements the list elements (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ArrayValue(@NonNull List<Value> elements, boolean secret) {
        this.value  = List.copyOf(elements);
        this.secret = secret;
    }

    /**
     * Creates an ArrayValue.
     *
     * @param elements the list elements (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ArrayValue(@NonNull Value[] elements, boolean secret) {
        this.value  = List.of(elements);
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
     */
    public static final class Builder {
        private final ArrayList<Value> elements = new ArrayList<>();
        private boolean                secret   = false;

        /**
         * Adds a value to the array.
         *
         * @param value the value to add
         * @return this builder
         */
        public Builder add(Value value) {
            elements.add(value);
            return this;
        }

        /**
         * Adds multiple values to the array.
         *
         * @param values the values to add
         * @return this builder
         */
        public Builder addAll(Value... values) {
            Collections.addAll(elements, values);
            return this;
        }

        /**
         * Adds multiple values to the array.
         *
         * @param values the values to add
         * @return this builder
         */
        public Builder addAll(Collection<Value> values) {
            elements.addAll(values);
            return this;
        }

        /**
         * Marks the array as secret.
         * Elements from secret arrays inherit the secret flag.
         *
         * @return this builder
         */
        public Builder secret() {
            this.secret = true;
            return this;
        }

        /**
         * Builds the immutable ArrayValue.
         * Returns singleton for empty arrays.
         *
         * @return the constructed ArrayValue
         */
        public ArrayValue build() {
            if (elements.isEmpty() && !secret) {
                return Value.EMPTY_ARRAY;
            }
            return new ArrayValue(elements, secret);
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
     *
     * @param index index of the element
     * @return the element (with secret flag if container is secret),
     * or ErrorValue if index is out of bounds
     */
    @Override
    public @NotNull Value get(int index) {
        try {
            return applySecretFlag(value.get(index));
        } catch (IndexOutOfBoundsException e) {
            return new ErrorValue("Array index out of bounds: " + index + " (size: " + value.size() + ")", secret);
        }
    }

    /**
     * Returns the first element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException,
     * consistent with SAPL's error-as-value model.
     *
     * @return the first element (with secret flag if container is secret),
     * or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getFirst() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get first element of empty array", secret);
        }
        return applySecretFlag(value.getFirst());
    }

    /**
     * Returns the last element.
     * <p>
     * Returns ErrorValue if the array is empty instead of throwing
     * NoSuchElementException,
     * consistent with SAPL's error-as-value model.
     *
     * @return the last element (with secret flag if container is secret),
     * or ErrorValue if array is empty
     */
    @Override
    public @NotNull Value getLast() {
        if (value.isEmpty()) {
            return new ErrorValue("Cannot get last element of empty array", secret);
        }
        return applySecretFlag(value.getLast());
    }

    /**
     * Returns an iterator over the elements.
     * Elements from secret containers are marked as secret.
     *
     * @return an iterator with secret flag propagation
     */
    @Override
    public @NotNull Iterator<Value> iterator() {
        return new SecretPropagatingIterator(value.iterator());
    }

    /**
     * Returns a list iterator over the elements.
     * Elements from secret containers are marked as secret.
     *
     * @return a list iterator with secret flag propagation
     */
    @Override
    public @NotNull ListIterator<Value> listIterator() {
        return new SecretPropagatingListIterator(value.listIterator());
    }

    /**
     * Returns a list iterator starting at the specified position.
     * Elements from secret containers are marked as secret.
     *
     * @param index index of the first element
     * @return a list iterator with secret flag propagation
     */
    @Override
    public @NotNull ListIterator<Value> listIterator(int index) {
        return new SecretPropagatingListIterator(value.listIterator(index));
    }

    /**
     * Returns a view of the portion between the specified indices.
     * The returned list propagates the secret flag.
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
     * Returns an array containing all elements.
     * Elements from secret containers are marked as secret.
     *
     * @return an array with secret flag applied
     */
    @Override
    public Object @NotNull [] toArray() {
        return value.stream().map(this::applySecretFlag).toArray();
    }

    /**
     * Returns an array containing all elements.
     * Elements from secret containers are marked as secret.
     *
     * @param a the array into which elements are stored
     * @param <T> the component type
     * @return an array with secret flag applied
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T @NotNull [] toArray(T @NotNull [] a) {
        var result = value.stream().map(this::applySecretFlag).toArray(Value[]::new);
        if (a.length >= result.length) {
            System.arraycopy(result, 0, a, 0, result.length);
            if (a.length > result.length) {
                a[result.length] = null;
            }
            return a;
        }
        return (T[]) Arrays.copyOf(result, result.length, a.getClass());
    }

    /**
     * Returns a sequential Stream over the elements.
     * Elements from secret containers are marked as secret.
     *
     * @return a stream with secret flag propagation
     */
    @Override
    public @NotNull Stream<Value> stream() {
        return value.stream().map(this::applySecretFlag);
    }

    /**
     * Returns a parallel Stream over the elements.
     * Elements from secret containers are marked as secret.
     *
     * @return a parallel stream with secret flag propagation
     */
    @Override
    public @NotNull Stream<Value> parallelStream() {
        return value.parallelStream().map(this::applySecretFlag);
    }

    /**
     * Returns a Spliterator over the elements.
     *
     * @return a spliterator with secret flag propagation
     */
    @Override
    public @NotNull Spliterator<Value> spliterator() {
        return stream().spliterator();
    }

    /**
     * Performs the given action for each element.
     * Elements from secret containers are marked as secret.
     *
     * @param action the action to perform for each element
     */
    @Override
    public void forEach(java.util.function.Consumer<? super Value> action) {
        value.forEach(v -> action.accept(applySecretFlag(v)));
    }

    /**
     * Applies secret flag from the container to the value.
     *
     * @param v the value
     * @return the value, marked as secret if container is secret
     */
    private Value applySecretFlag(Value v) {
        return secret ? v.asSecret() : v;
    }

    /**
     * Iterator that propagates the secret flag to elements.
     * Immutable - modification operations throw UnsupportedOperationException.
     */
    private class SecretPropagatingIterator implements Iterator<Value> {
        private final Iterator<Value> delegate;

        SecretPropagatingIterator(Iterator<Value> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Value next() {
            return applySecretFlag(delegate.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("ArrayValue is immutable");
        }
    }

    /**
     * ListIterator that propagates the secret flag to elements.
     * Immutable - modification operations throw UnsupportedOperationException.
     */
    private class SecretPropagatingListIterator implements ListIterator<Value> {
        private final ListIterator<Value> delegate;

        SecretPropagatingListIterator(ListIterator<Value> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Value next() {
            return applySecretFlag(delegate.next());
        }

        @Override
        public boolean hasPrevious() {
            return delegate.hasPrevious();
        }

        @Override
        public Value previous() {
            return applySecretFlag(delegate.previous());
        }

        @Override
        public int nextIndex() {
            return delegate.nextIndex();
        }

        @Override
        public int previousIndex() {
            return delegate.previousIndex();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("ArrayValue is immutable");
        }

        @Override
        public void set(Value value) {
            throw new UnsupportedOperationException("ArrayValue is immutable");
        }

        @Override
        public void add(Value value) {
            throw new UnsupportedOperationException("ArrayValue is immutable");
        }
    }

    /**
     * Methods excluded from Lombok delegation to preserve Value semantics.
     * These methods require custom implementations to:
     * - Maintain equals/hashCode contracts
     * - Propagate secret flag during element access
     * - Apply error-as-value pattern for defensive operations
     * - Transform streaming operations to apply secret flag
     */
    private interface ExcludedMethods {
        boolean equals(Object obj);

        int hashCode();

        String toString();

        Value get(int index);

        Value getFirst();

        Value getLast();

        Iterator<Value> iterator();

        ListIterator<Value> listIterator();

        ListIterator<Value> listIterator(int index);

        List<Value> subList(int fromIndex, int toIndex);

        Object[] toArray();

        <T> T[] toArray(T[] a);

        Stream<Value> stream();

        Stream<Value> parallelStream();

        Spliterator<Value> spliterator();

        void forEach(java.util.function.Consumer<? super Value> action);
    }
}
