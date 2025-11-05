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

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Object value implementing Map semantics. The underlying map is immutable.
 * <p>
 * Values from secret containers inherit the secret flag. Invalid key types
 * return ErrorValue instead of throwing exceptions, consistent with SAPL's
 * error-as-value pattern.
 * <p>
 * Direct map usage:
 * <pre>{@code
 * ObjectValue user = Value.ofObject(Map.of(
 *     "username", Value.of("alice"),
 *     "department", Value.of("engineering"),
 *     "clearance", Value.of(3)
 * ));
 * Value username = user.get("username");
 * for (Map.Entry<String, Value> entry : user.entrySet()) {
 *     processAttribute(entry.getKey(), entry.getValue());
 * }
 * }</pre>
 * <p>
 * Builder for fluent construction:
 * <pre>{@code
 * ObjectValue metadata = ObjectValue.builder()
 *     .put("resourceId", Value.of("doc-123"))
 *     .put("owner", Value.of("bob"))
 *     .secret()
 *     .build();
 * }</pre>
 * <p>
 * Secret propagation:
 * <pre>{@code
 * ObjectValue credentials = obj.asSecret();
 * Value password = credentials.get("password"); // Also secret
 * }</pre>
 * <p>
 * Error handling:
 * <pre>{@code
 * Value result = obj.get(null); // Returns ErrorValue
 * Value result2 = obj.get(123);  // Returns ErrorValue (not a String key)
 * }</pre>
 */
public final class ObjectValue implements Value, Map<String, Value> {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    @Delegate(excludes = ExcludedMethods.class)
    private final Map<String, Value> value;
    private final boolean secret;

    /**
     * Creates an ObjectValue.
     *
     * @param properties the map properties (defensively copied, must not be null)
     * @param secret whether this value is secret
     */
    public ObjectValue(@NonNull Map<String, Value> properties, boolean secret) {
        this.value = Map.copyOf(properties);
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
        private final HashMap<String, Value> properties = new HashMap<>();
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
         * Values from secret objects inherit the secret flag.
         *
         * @return this builder
         */
        public Builder secret() {
            this.secret = true;
            return this;
        }

        /**
         * Builds the immutable ObjectValue.
         * Returns singleton for empty objects.
         *
         * @return the constructed ObjectValue
         */
        public ObjectValue build() {
            if (properties.isEmpty() && !secret) {
                return Value.EMPTY_OBJECT;
            }
            return new ObjectValue(properties, secret);
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
        return '{' + value.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue().toString())
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
     *
     * @param key the key (must be a String)
     * @return the value (with secret flag if container is secret),
     *         null if key not found,
     *         or ErrorValue if key is null or not a String
     */
    @Override
    public @Nullable Value get(Object key) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null", secret);
        }
        if (!(key instanceof String)) {
            return new ErrorValue("Invalid key type: expected String, got " + key.getClass().getSimpleName(), secret);
        }
        var v = value.get(key);
        return v == null ? null : applySecretFlag(v);
    }

    /**
     * Returns the value for the specified key, or defaultValue if not found.
     * <p>
     * Returns ErrorValue for invalid key types instead of throwing ClassCastException.
     *
     * @param key the key (must be a String)
     * @param defaultValue the default value if key not found
     * @return the value (with secret flag if container is secret),
     *         defaultValue (with secret flag) if key not found,
     *         or ErrorValue if key is null or not a String
     */
    @Override
    public @NotNull Value getOrDefault(Object key, Value defaultValue) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null", secret);
        }
        if (!(key instanceof String)) {
            return new ErrorValue("Invalid key type: expected String, got " + key.getClass().getSimpleName(), secret);
        }
        var v = value.getOrDefault(key, defaultValue);
        return applySecretFlag(v);
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
     * Returns true if this map contains the specified value.
     *
     * @param value value to test
     * @return true if map contains the value
     */
    @Override
    public boolean containsValue(Object value) {
        return this.value.containsValue(value);
    }

    /**
     * Returns a collection of values in this map.
     * Values from secret containers are marked as secret.
     *
     * @return an immutable collection with secret flag applied
     */
    @Override
    public @NotNull Collection<Value> values() {
        return value.values().stream()
                .map(this::applySecretFlag)
                .toList();
    }

    /**
     * Returns a set of entries in this map.
     * Values from secret containers are marked as secret.
     *
     * @return an immutable set with secret flag applied to values
     */
    @Override
    public @NotNull Set<Entry<String, Value>> entrySet() {
        return value.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), applySecretFlag(e.getValue())))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Performs the given action for each entry.
     * Values have secret flag applied if container is secret.
     *
     * @param action the action for each entry
     */
    @Override
    public void forEach(BiConsumer<? super String, ? super Value> action) {
        value.forEach((k, v) -> action.accept(k, applySecretFlag(v)));
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
     * Methods excluded from Lombok delegation to preserve Value semantics.
     * These methods require custom implementations to:
     * - Maintain equals/hashCode contracts
     * - Propagate secret flag during value access
     * - Apply error-as-value pattern for invalid keys (null, wrong type)
     * - Transform collection views to apply secret flag
     */
    private interface ExcludedMethods {
        boolean equals(Object obj);
        int hashCode();
        String toString();
        Value get(Object key);
        Value getOrDefault(Object key, Value defaultValue);
        boolean containsKey(Object key);
        boolean containsValue(Object value);
        Collection<Value> values();
        Set<Entry<String, Value>> entrySet();
        void forEach(BiConsumer<? super String, ? super Value> action);
    }
}