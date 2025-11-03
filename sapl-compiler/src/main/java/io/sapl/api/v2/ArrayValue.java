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
package io.sapl.api.v2;

import java.util.Collection;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.With;
import lombok.experimental.Delegate;

/**
 * Represents an array Value that implements List semantics.
 * The underlying list is immutable.
 *
 * Use directly as a List:
 * <pre>{@code
 * ArrayValue array = (ArrayValue) Value.ofArray(v1, v2, v3);
 * Value first = array.get(0);
 * int size = array.size();
 * for (Value v : array) {
 *     // process value
 * }
 * }</pre>
 *
 * Or use builder for fluent construction:
 * <pre>{@code
 * ArrayValue array = ArrayValue.builder()
 *     .add(Value.of("item1"))
 *     .add(Value.of("item2"))
 *     .secret()
 *     .build();
 * }</pre>
 */
@EqualsAndHashCode
public final class ArrayValue implements Value, List<Value> {

    @Delegate
    private final List<Value> delegate;

    @With
    private final boolean secret;

    /**
     * Creates an ArrayValue.
     *
     * @param elements the list elements (will be defensively copied)
     * @param secret whether this value is secret
     */
    public ArrayValue(List<Value> elements, boolean secret) {
        this.delegate = List.copyOf(elements);
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
        private final java.util.ArrayList<Value> elements = new java.util.ArrayList<>();
        private boolean secret = false;

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
            elements.addAll(List.of(values));
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
         *
         * @return this builder
         */
        public Builder secret() {
            this.secret = true;
            return this;
        }

        /**
         * Builds the ArrayValue.
         *
         * @return the constructed ArrayValue
         */
        public ArrayValue build() {
            return new ArrayValue(elements, secret);
        }
    }

    @Override
    public boolean secret() {
        return secret;
    }

    @Override
    public Value asSecret() {
        return Value.asSecretHelper(this, v -> v.withSecret(true));
    }

    @Override
    public String getValType() {
        return "ARRAY";
    }

    @Override
    public Object getTrace() {
        return null;
    }

    @Override
    public Object getErrorsFromTrace() {
        return null;
    }

    @Override
    public String toString() {
        return Value.formatToString("ArrayValue", secret, () -> {
            if (delegate.isEmpty()) {
                return "size=0";
            }
            return "size=" + delegate.size();
        });
    }
}