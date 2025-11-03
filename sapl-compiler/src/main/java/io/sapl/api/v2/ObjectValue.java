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

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.With;
import lombok.experimental.Delegate;

/**
 * Represents an object Value that implements Map semantics.
 * The underlying map is immutable.
 *
 * Use directly as a Map:
 * <pre>{@code
 * ObjectValue obj = (ObjectValue) Value.ofObject(Map.of("key", value));
 * Value val = obj.get("key");
 * boolean hasKey = obj.containsKey("key");
 * for (Map.Entry<String, Value> entry : obj.entrySet()) {
 *     // process entry
 * }
 * }</pre>
 *
 * Or use builder for fluent construction:
 * <pre>{@code
 * ObjectValue obj = ObjectValue.builder()
 *     .put("username", Value.of("alice"))
 *     .put("role", Value.of("admin"))
 *     .secret()
 *     .build();
 * }</pre>
 */
@EqualsAndHashCode
public final class ObjectValue implements Value, Map<String, Value> {

    @Delegate
    private final Map<String, Value> delegate;

    @With
    private final boolean secret;

    /**
     * Creates an ObjectValue.
     *
     * @param properties the map properties (will be defensively copied)
     * @param secret whether this value is secret
     */
    public ObjectValue(Map<String, Value> properties, boolean secret) {
        this.delegate = Map.copyOf(properties);
        this.secret = secret;
    }

    /**
     * Creates a builder for fluent object construction.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for fluent ObjectValue construction.
     */
    public static final class Builder {
        private final java.util.HashMap<String, Value> properties = new java.util.HashMap<>();
        private boolean secret = false;

        /**
         * Adds a property to the object.
         *
         * @param key the property key
         * @param value the property value
         * @return this builder
         */
        public Builder put(String key, Value value) {
            properties.put(key, value);
            return this;
        }

        /**
         * Adds multiple properties to the object.
         *
         * @param entries the properties to add
         * @return this builder
         */
        public Builder putAll(Map<String, Value> entries) {
            properties.putAll(entries);
            return this;
        }

        /**
         * Marks the object as secret.
         *
         * @return this builder
         */
        public Builder secret() {
            this.secret = true;
            return this;
        }

        /**
         * Builds the ObjectValue.
         *
         * @return the constructed ObjectValue
         */
        public ObjectValue build() {
            return new ObjectValue(properties, secret);
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
        return "OBJECT";
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
        return Value.formatToString("ObjectValue", secret, () -> {
            if (delegate.isEmpty()) {
                return "keys=[]";
            }
            return "keys=" + delegate.keySet();
        });
    }
}