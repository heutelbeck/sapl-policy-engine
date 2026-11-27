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

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

import lombok.val;

/**
 * A value as observed by operations that iterate object entries or inspect decimal scale.
 * The value and its immutable containers are shared.
 *
 * @param value the SAPL value
 */
public record ValueRepresentation(Value value) {
    @Override
    public boolean equals(Object other) {
        return other instanceof ValueRepresentation candidate && same(value, candidate.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Compares observable representations. Distinct error occurrences retain their own provenance.
     *
     * @param left the first value
     * @param right the second value
     * @return whether the values can share one representation
     */
    public static boolean same(Value left, Value right) {
        if (left == right) {
            return true;
        }
        if (left instanceof ObjectValue a && right instanceof ObjectValue b) {
            return sameEntries(a, b);
        }
        if (left instanceof ArrayValue a && right instanceof ArrayValue b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!same(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            return a.value().scale() == b.value().scale() && a.equals(b);
        }
        return !(left instanceof ErrorValue) && left.equals(right);
    }

    private static boolean sameEntries(ObjectValue left, ObjectValue right) {
        if (left.size() != right.size()) {
            return false;
        }
        val entries = right.entrySet().iterator();
        for (val entry : left.entrySet()) {
            val other = entries.next();
            if (!entry.getKey().equals(other.getKey()) || !same(entry.getValue(), other.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scans a value before it enters a compilation cache. A shared subtree is visited once.
     *
     * @param value the candidate value
     * @return whether any reachable value is an error
     */
    public static boolean containsError(Value value) {
        if (!(value instanceof ArrayValue || value instanceof ObjectValue)) {
            return value instanceof ErrorValue;
        }
        val seen    = new IdentityHashMap<Value, Boolean>();
        val pending = new ArrayDeque<Value>();
        pending.push(value);
        while (!pending.isEmpty()) {
            val current = pending.pop();
            if (current instanceof ErrorValue) {
                return true;
            }
            if (current instanceof ArrayValue array && seen.put(array, Boolean.TRUE) == null) {
                pending.addAll(array);
            } else if (current instanceof ObjectValue object && seen.put(object, Boolean.TRUE) == null) {
                pending.addAll(object.values());
            }
        }
        return false;
    }
}
