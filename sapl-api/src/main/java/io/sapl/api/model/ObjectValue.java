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
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Object value implementing Map semantics. The underlying map is immutable.
 * <p>
 * Metadata (including secret flag and attribute traces) is aggregated from all
 * values at construction time and
 * propagated back to all values. This ensures:
 * <ul>
 * <li>Zero-overhead reads - values already have merged metadata</li>
 * <li>Audit completeness - operations preserve all source attribute traces</li>
 * <li>Consistent secret handling - if any value is secret, all become
 * secret</li>
 * </ul>
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
 * Value result2 = obj.get(123); // Returns ErrorValue (not a String key)
 * }</pre>
 */
public final class ObjectValue implements Value, Map<String, Value> {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Singleton for secret empty object.
     */
    private static final ObjectValue SECRET_EMPTY_OBJECT = new ObjectValue(Map.of(), ValueMetadata.SECRET_EMPTY);

    @Delegate(excludes = ExcludedMethods.class)
    private final Map<String, Value> value;
    private final ValueMetadata      metadata;

    /**
     * Creates an ObjectValue with specified metadata.
     * <p>
     * Aggregates metadata from all values and merges with the provided metadata,
     * then propagates the merged result back
     * to all values. This ensures operations preserve all source attribute traces.
     * <p>
     * Note: This does a defensive copy of the supplied Map. For better performance
     * without the need to copy use the
     * Builder!
     *
     * @param properties
     * the map properties (defensively copied, must not be null)
     * @param metadata
     * the container metadata to merge with value metadata
     */
    public ObjectValue(@NonNull Map<String, Value> properties, @NonNull ValueMetadata metadata) {
        val mergedMetadata = aggregateAndMerge(properties, metadata);
        this.value    = propagateMetadata(properties, mergedMetadata);
        this.metadata = mergedMetadata;
    }

    /**
     * Zero-copy constructor for builder use only. The supplied map is used directly
     * without copying. Assumes metadata
     * already propagated to values.
     *
     * @param metadata
     * the pre-computed container metadata
     * @param properties
     * the map properties (used directly, must be mutable until wrapped)
     */
    private ObjectValue(ValueMetadata metadata, Map<String, Value> properties) {
        this.value    = Collections.unmodifiableMap(properties);
        this.metadata = metadata;
    }

    private static ValueMetadata aggregateAndMerge(Map<String, Value> properties, ValueMetadata containerMetadata) {
        if (properties.isEmpty()) {
            return containerMetadata;
        }
        var merged = containerMetadata;
        for (Value propertyValue : properties.values()) {
            merged = merged.merge(propertyValue.metadata());
        }
        return merged;
    }

    private static Map<String, Value> propagateMetadata(Map<String, Value> properties, ValueMetadata targetMetadata) {
        if (properties.isEmpty()) {
            return Map.of();
        }
        // Preserve insertion order
        var result = new LinkedHashMap<String, Value>();
        for (var entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue().withMetadata(targetMetadata));
        }
        return Collections.unmodifiableMap(result);
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
     * reused. This prevents immutability
     * violations from the zero-copy optimization.
     * <p>
     * The builder aggregates metadata from all added values. At build time, the
     * merged metadata is propagated back to
     * all values for consistent access.
     */
    public static final class Builder {
        private LinkedHashMap<String, Value> properties = new LinkedHashMap<>();
        private ValueMetadata                metadata   = ValueMetadata.EMPTY;

        /**
         * Adds a property to the object.
         *
         * @param key
         * the property key
         * @param value
         * the property value
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder put(String key, Value value) {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            metadata = metadata.merge(value.metadata());
            properties.put(key, value);
            return this;
        }

        /**
         * Adds multiple properties to the object.
         *
         * @param entries
         * the properties to add
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
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
         * <p>
         * Note: For performance reasons, if possible call this method as early as
         * possible in the building process.
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder secret() {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            metadata = metadata.asSecret();
            return this;
        }

        /**
         * Merges additional metadata into the builder.
         *
         * @param additionalMetadata
         * the metadata to merge
         *
         * @return this builder
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public Builder withMetadata(ValueMetadata additionalMetadata) {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            metadata = metadata.merge(additionalMetadata);
            return this;
        }

        /**
         * Builds the immutable ObjectValue. Returns singleton for empty non-secret
         * objects.
         * <p>
         * At build time, the aggregated metadata is propagated to all values for
         * consistent access. After calling this
         * method, the builder cannot be reused.
         *
         * @return the constructed ObjectValue
         *
         * @throws IllegalStateException
         * if builder has already been used
         */
        public ObjectValue build() {
            if (properties == null) {
                throw new IllegalStateException("Builder has already been used.");
            }
            if (properties.isEmpty() && metadata == ValueMetadata.EMPTY) {
                properties = null;
                return Value.EMPTY_OBJECT;
            }
            if (properties.isEmpty() && metadata.secret() && metadata.attributeTrace().isEmpty()) {
                properties = null;
                return SECRET_EMPTY_OBJECT;
            }
            if (properties.isEmpty()) {
                properties = null;
                return new ObjectValue(metadata, Map.of());
            }
            // Propagate merged metadata to all values
            properties.replaceAll((key, propertyValue) -> propertyValue.withMetadata(metadata));
            val result = new ObjectValue(metadata, properties);
            properties = null;
            return result;
        }
    }

    @Override
    public ValueMetadata metadata() {
        return metadata;
    }

    @Override
    public Value withMetadata(ValueMetadata newMetadata) {
        if (newMetadata == metadata) {
            return this;
        }
        return new ObjectValue(value, newMetadata);
    }

    @Override
    public @NotNull String toString() {
        if (isSecret()) {
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
     * ClassCastException, consistent with SAPL's
     * error-as-value model. Values already have the container's metadata applied.
     *
     * @param key
     * the key (must be a String)
     *
     * @return the value, null if key not found, or ErrorValue if key is null or not
     * a String
     */
    @Override
    public @Nullable Value get(Object key) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null.", metadata);
        }
        if (!(key instanceof String)) {
            return new ErrorValue(
                    "Invalid key type: expected String, got %s.".formatted(key.getClass().getSimpleName()), metadata);
        }
        return value.get(key);
    }

    /**
     * Returns the value for the specified key, or defaultValue if not found.
     * <p>
     * Returns ErrorValue for invalid key types instead of throwing
     * ClassCastException. Values already have the
     * container's metadata applied.
     *
     * @param key
     * the key (must be a String)
     * @param defaultValue
     * the default value if key not found
     *
     * @return the value, defaultValue if key not found, or ErrorValue if key is
     * null or not a String
     */
    @Override
    public @NotNull Value getOrDefault(Object key, Value defaultValue) {
        if (key == null) {
            return new ErrorValue("Object key cannot be null.", metadata);
        }
        if (!(key instanceof String)) {
            return new ErrorValue(
                    "Invalid key type: expected String, got %s.".formatted(key.getClass().getSimpleName()), metadata);
        }
        val valueForKey = value.get(key);
        if (valueForKey == null) {
            return defaultValue.withMetadata(metadata);
        }
        return valueForKey;
    }

    /**
     * Returns true if this map contains the specified key.
     * <p>
     * Returns false for non-String keys instead of throwing ClassCastException.
     *
     * @param key
     * key to test
     *
     * @return true if map contains the key (which must be a String)
     */
    @Override
    public boolean containsKey(Object key) {
        return key instanceof String && value.containsKey(key);
    }

    /**
     * Methods excluded from Lombok delegation to preserve Value semantics. These
     * methods require custom implementations
     * for error handling and type safety.
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
