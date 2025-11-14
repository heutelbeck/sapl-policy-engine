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
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Object value implementing Map semantics. The underlying map is immutable.
 * <p>
 * Secret containers eagerly apply the secret flag to all contained values at
 * construction time, enabling zero-overhead reads and optimal performance for
 * recursive operations.
 * <p>
 * Direct map usage:
 *
 * <pre>{@code
 * ObjectValue user = Value.ofObject(
 *         Map.of("username", Value.of("alice"), "department", Value.of("engineering"), "clearance", Value.of(3)));
 * Value username = user.get("username");
 * for (Map.Entry<String, Value> entry : user.entrySet()) {
 *     processAttribute(entry.getKey(), entry.getValue());
 * }
 * }</pre>
 * <p>
 * Builder for fluent construction:
 *
 * <pre>{@code
 * ObjectValue metadata = ObjectValue.builder().put("resourceId", Value.of("doc-123")).put("owner", Value.of("bob"))
 *         .secret().build();
 * }</pre>
 * <p>
 * Secret propagation:
 *
 * <pre>{@code
 * ObjectValue credentials = obj.asSecret();
 * Value password = credentials.get("password"); // Already secret
 * }</pre>
 * <p>
 * Error handling:
 *
 * <pre>{@code
 * Value result = obj.get(null); // Returns ErrorValue
 * Value result2 = obj.get(123);  // Returns ErrorValue (not a String key)
 * }</pre>
 */
public final class ObjectValue implements Value, Map<String, Value> {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Singleton for secret empty object.
     */
    public static final ObjectValue SECRET_EMPTY_OBJECT = new ObjectValue(Map.of(), true);

    @Delegate(excludes = ExcludedMethods.class)
    private final Map<String, Value> value;
    private final boolean            secret;

    /**
     * Creates an ObjectValue.
     * <p>
     * If secret is true, applies the secret flag to all values at construction time
     * for optimal read performance.
     * <p>
     * Note: This does a defensive copy of the supplied Map. For better performance
     * without the need to copy use the Builder!
     *
     * @param properties the map properties (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ObjectValue(@NonNull Map<String, Value> properties, boolean secret) {
        if (secret) {
            var secretMap = new LinkedHashMap<String, Value>();
            for (var entry : properties.entrySet()) {
                secretMap.put(entry.getKey(), entry.getValue().asSecret());
            }
            this.value = Collections.unmodifiableMap(secretMap);
        } else {
            // Preserve insertion order by using LinkedHashMap
            // Map.copyOf() does NOT guarantee order preservation
            this.value = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }
        this.secret = secret;
    }

    /**
     * Zero-copy constructor for builder use only.
     * The supplied map is used directly without copying.
     *
     * @param secret whether this value is secret
     * @param properties the map properties (used directly, must be mutable until
     * wrapped)
     */
    private ObjectValue(boolean secret, Map<String, Value> properties) {
        this.value  = Collections.unmodifiableMap(properties);
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
     * <p>
     * Builders are single-use only. After calling build(), the builder cannot be
     * reused. This prevents immutability violations from the zero-copy
     * optimization.
     */
    public static final class Builder {
        private LinkedHashMap<String, Value> properties = new LinkedHashMap<>();
        private boolean                      secret     = false;

        /**
         * Adds a property to the object.
         *
         * @param key the property key
         * @param value the property value
         * @return this builder
         * @throws IllegalStateException if builder has already been used
         */
        public Builder put(String key, Value value) {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (secret) {
                value = value.asSecret();
            }
            properties.put(key, value);
            return this;
        }

        /**
         * Adds multiple properties to the object.
         *
         * @param entries the properties to add
         * @return this builder
         * @throws IllegalStateException if builder has already been used
         */
        public Builder putAll(Map<String, Value> entries) {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            for (val entry : entries.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Marks the object as secret.
         * Values from secret objects will have the secret flag applied at construction.
         * <p>
         * Note: For performance reasons, if possible call this method as early as
         * possible in the building process.
         *
         * @return this builder
         * @throws IllegalStateException if builder has already been used
         */
        public Builder secret() {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (!this.secret) {
                this.secret = true;
                properties.replaceAll((k, v) -> v.asSecret());
            }
            return this;
        }

        /**
         * Builds the immutable ObjectValue.
         * Returns singleton for empty non-secret objects.
         * <p>
         * After calling this method, the builder cannot be reused. Attempting to call
         * any builder methods after build() will throw IllegalStateException.
         *
         * @return the constructed ObjectValue
         * @throws IllegalStateException if builder has already been used
         */
        public ObjectValue build() {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (properties.isEmpty() && !secret) {
                properties = null;
                return Value.EMPTY_OBJECT;
            }
            if (properties.isEmpty()) {
                properties = null;
                return SECRET_EMPTY_OBJECT;
            }

            val result = new ObjectValue(secret, properties);
            properties = null;
            return result;
        }
    }

    @Override
    public boolean secret() {
        return secret;
    }

    @Override
    public Value asSecret() {
        return secret ? this : new ObjectValue(value, true);
    }

    @Override
    public @NotNull String toString() {
        if (secret) {
            return SECRET_PLACEHOLDER;
        }
        if (value.isEmpty()) {
            return "{}";
        }
        return '{' + value.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue().toString())
                .collect(Collectors.joining(", ")) + '}';
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof Map<?, ?> thatMap))
            return false;
        return value.equals(thatMap);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Returns the value for the specified key.
     * <p>
     * Returns ErrorValue for invalid key types instead of throwing
     * ClassCastException, consistent with SAPL's error-as-value model.
     * Values already have the secret flag applied if container is secret.
     *
     * @param key the key (must be a String)
     * @return the value, null if key not found, or ErrorValue if key is null or not
     * a String
     */
    @Override
    public @Nullable Value get(Object key) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null.", secret);
        }
        if (!(key instanceof String)) {
            return new ErrorValue("Invalid key type: expected String, got " + key.getClass().getSimpleName() + ".",
                    secret);
        }
        return value.get(key);
    }

    /**
     * Returns the value for the specified key, or defaultValue if not found.
     * <p>
     * Returns ErrorValue for invalid key types instead of throwing
     * ClassCastException. Values already have the secret flag applied if container
     * is secret.
     *
     * @param key the key (must be a String)
     * @param defaultValue the default value if key not found
     * @return the value, defaultValue if key not found, or ErrorValue if key is
     * null or not a String
     */
    @Override
    public @NotNull Value getOrDefault(Object key, Value defaultValue) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null.", secret);
        }
        if (!(key instanceof String)) {
            return new ErrorValue("Invalid key type: expected String, got " + key.getClass().getSimpleName() + ".",
                    secret);
        }
        val valueForKey = value.get(key);
        if (valueForKey == null) {
            return secret ? defaultValue.asSecret() : defaultValue;
        }
        return valueForKey;
    }

    /**
     * Returns true if this map contains the specified key.
     * <p>
     * Returns false for non-String keys instead of throwing ClassCastException.
     *
     * @param key key to test
     * @return true if map contains the key (which must be a String)
     */
    @Override
    public boolean containsKey(Object key) {
        return key instanceof String && value.containsKey(key);
    }

    /**
     * Methods excluded from Lombok delegation to preserve Value semantics.
     * These methods require custom implementations for error handling and type
     * safety.
     */
    private interface ExcludedMethods {
        boolean equals(Object obj);

        int hashCode();

        String toString();

        Value get(Object key);

        Value getOrDefault(Object key, Value defaultValue);

        boolean containsKey(Object key);
    }
}
