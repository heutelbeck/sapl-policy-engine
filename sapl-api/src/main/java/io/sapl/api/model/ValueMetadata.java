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
import io.sapl.api.pdp.internal.AttributeRecord;
import lombok.NonNull;
import lombok.val;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Metadata carried by every Value during policy evaluation. Tracks whether the
 * value is secret (should not be logged)
 * and which attribute invocations contributed to producing this value.
 * <p>
 * Metadata propagates through operator analogous to how secrets propagate:
 * binary operator merge metadata from both
 * operands, containers aggregate metadata from all elements then propagate the
 * merged result back to elements.
 * <p>
 * Example flow:
 *
 * <pre>{@code
 * var a = <subject.role>;     // metadata: [subject.role]
 * var b = <resource.owner>;   // metadata: [resource.owner]
 * var result = a == b;        // metadata: [subject.role, resource.owner]
 * }</pre>
 *
 * @param secret
 * whether this value contains sensitive data that should not be logged
 * @param attributeTrace
 * attribute invocations that contributed to this value
 */
public record ValueMetadata(boolean secret, @NonNull List<AttributeRecord> attributeTrace) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Empty metadata singleton for constants, literals, and function results.
     */
    public static final ValueMetadata EMPTY = new ValueMetadata(false, List.of());

    /**
     * Secret empty metadata singleton.
     */
    public static final ValueMetadata SECRET_EMPTY = new ValueMetadata(true, List.of());

    /**
     * Merges this metadata with another, combining secret flags and attribute
     * traces. Deduplicates attribute records by
     * reference identity.
     *
     * @param other
     * the metadata to merge with
     *
     * @return merged metadata, or one of the inputs if the other is empty
     */
    public ValueMetadata merge(ValueMetadata other) {
        val mergedSecret = secret || other.secret;
        val thisEmpty    = attributeTrace.isEmpty();
        val otherEmpty   = other.attributeTrace.isEmpty();

        if (thisEmpty && otherEmpty) {
            return mergedSecret ? SECRET_EMPTY : EMPTY;
        }
        if (thisEmpty) {
            return mergedSecret == other.secret ? other : new ValueMetadata(mergedSecret, other.attributeTrace);
        }
        if (otherEmpty) {
            return mergedSecret == secret ? this : new ValueMetadata(mergedSecret, attributeTrace);
        }
        return new ValueMetadata(mergedSecret, deduplicatedConcat(attributeTrace, other.attributeTrace));
    }

    /**
     * Merges multiple metadata instances.
     *
     * @param sources
     * the metadata instances to merge
     *
     * @return merged metadata
     */
    public static ValueMetadata merge(ValueMetadata... sources) {
        if (sources.length == 0) {
            return EMPTY;
        }
        if (sources.length == 1) {
            return sources[0];
        }
        var result = sources[0];
        for (int i = 1; i < sources.length; i++) {
            result = result.merge(sources[i]);
        }
        return result;
    }

    public static ValueMetadata merge(Collection<Value> sources) {
        if (sources.isEmpty()) {
            return EMPTY;
        }
        var result = EMPTY;
        for (val s : sources) {
            result = result.merge(s.metadata());
        }
        return result;
    }

    public static ValueMetadata merge(Value... sources) {
        if (sources.length == 0) {
            return EMPTY;
        }
        if (sources.length == 1) {
            return sources[0].metadata();
        }
        var result = sources[0].metadata();
        for (int i = 1; i < sources.length; i++) {
            result = result.merge(sources[i].metadata());
        }
        return result;
    }

    /**
     * Returns metadata with the secret flag set, preserving the attribute trace.
     *
     * @return secret metadata with same trace, or this if already secret
     */
    public ValueMetadata asSecret() {
        if (secret) {
            return this;
        }
        if (attributeTrace.isEmpty()) {
            return SECRET_EMPTY;
        }
        return new ValueMetadata(true, attributeTrace);
    }

    /**
     * Creates metadata for a single attribute invocation.
     *
     * @param attributeRecord
     * the attribute attributeRecord
     *
     * @return metadata containing just this attribute
     */
    public static ValueMetadata ofAttribute(AttributeRecord attributeRecord) {
        return new ValueMetadata(false, List.of(attributeRecord));
    }

    /**
     * Creates secret metadata for a single attribute invocation.
     *
     * @param attributeRecord
     * the attribute attributeRecord
     *
     * @return secret metadata containing just this attribute
     */
    public static ValueMetadata ofSecretAttribute(AttributeRecord attributeRecord) {
        return new ValueMetadata(true, List.of(attributeRecord));
    }

    /**
     * Concatenates two attribute lists with deduplication by reference identity.
     */
    private static List<AttributeRecord> deduplicatedConcat(List<AttributeRecord> first, List<AttributeRecord> second) {
        var seen = LinkedHashSet.<AttributeRecord>newLinkedHashSet(first.size() + second.size());
        seen.addAll(first);
        seen.addAll(second);
        return List.copyOf(seen);
    }
}
