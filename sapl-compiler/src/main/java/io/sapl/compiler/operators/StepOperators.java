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
package io.sapl.compiler.operators;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.NonNull;
import lombok.experimental.StandardException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@UtilityClass
public class StepOperators {

    private static final int MAX_RECURSION_DEPTH = 500;

    /**
     * Accesses an object property by key.
     *
     * @param parent the object value
     * @param key the property key
     * @return the property value, UNDEFINED if key not found, or error if parent is
     * not an object
     */
    public static Value keyStep(Value parent, String key) {
        if (!(parent instanceof ObjectValue objectValue)) {
            return Value.error(
                    "Cannot access contents of a non-object value using a key. Expected an ObjectValue but got %s."
                            .formatted(parent));
        }
        val content = objectValue.get(key);
        if (content == null) {
            return Value.UNDEFINED;
        }
        return content;
    }

    /**
     * Accesses an array element by index. Supports negative indices counting from
     * the end.
     *
     * @param parent the array value
     * @param bigIndex the element index (negative values count from end)
     * @return the array element, or error if parent is not an array or index out of
     * bounds
     */
    public static Value indexStep(Value parent, @NonNull BigDecimal bigIndex) {
        int index;
        try {
            index = bigIndex.intValueExact();
        } catch (ArithmeticException e) {
            return Value.error(e);
        }
        if (!(parent instanceof ArrayValue arrayValue)) {
            return Value
                    .error("Cannot access contents of a non-array value using an index. Expected an Array but got %s."
                            .formatted(parent));
        }
        var normalizedIndex = index < 0 ? index + arrayValue.size() : index;
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return Value.error("Index %d out of bounds for array of size %d.".formatted(index, arrayValue.size()));
        }
        return arrayValue.get(normalizedIndex);
    }

    /**
     * Selects all elements from an array or all values from an object.
     *
     * @param parent the array or object value
     * @return the array itself if parent is array, array of object values if parent
     * is object, or error otherwise
     */
    public static Value wildcardStep(Value parent) {
        if (parent instanceof ArrayValue) {
            return parent;
        }
        if (parent instanceof ObjectValue objectValue) {
            val arrayBuilder = ArrayValue.builder();
            for (val value : objectValue.values()) {
                arrayBuilder.add(value);
            }
            return arrayBuilder.build();
        }
        return Value
                .error("Wildcard steps '.*' can only be applied to arrays or objects but got %s.".formatted(parent));
    }

    /**
     * Recursively searches for a key in nested objects and arrays, collecting all
     * matching values.
     *
     * @param parent the value to search
     * @param key the key to search for
     * @return array of all found values, or error if maximum recursion depth
     * exceeded
     */
    public static Value recursiveKeyStep(Value parent, String key) {
        val arrayBuilder = ArrayValue.builder();
        try {
            recursiveKeyStep(parent, key, 0, arrayBuilder);
            return arrayBuilder.build();
        } catch (MaxRecursionDepthException e) {
            return Value.error("Maximum nesting depth exceeded during recursive key step.");
        }
    }

    private static void recursiveKeyStep(Value parent, String key, int depth, ArrayValue.Builder builder)
            throws MaxRecursionDepthException {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new MaxRecursionDepthException();
        }
        if (parent instanceof ObjectValue objectValue) {
            val potentialMatch = objectValue.get(key);
            if (potentialMatch != null) {
                builder.add(potentialMatch);  // Direct add, no intermediate arrays
            }
            for (val value : objectValue.values()) {
                recursiveKeyStep(value, key, depth + 1, builder);  // No result to check!
            }
        } else if (parent instanceof ArrayValue arrayValue) {
            for (val value : arrayValue) {
                recursiveKeyStep(value, key, depth + 1, builder);
            }
        }
    }

    /**
     * Applies array slicing using SAPL semantics.
     * <p>
     * SAPL slicing differs from Python: negative step affects SELECTION pattern,
     * not iteration direction. Iteration is always forward through array indices.
     * <p>
     * Slicing syntax: {@code array[start:end:step]}
     * <ul>
     * <li>{@code start}: Starting index (inclusive), defaults to 0. Negative values
     * count from end.</li>
     * <li>{@code end}: Ending index (exclusive), defaults to array length. Negative
     * values count from end.</li>
     * <li>{@code step}: Step size (defaults to 1). Negative step affects selection
     * via modulo.</li>
     * </ul>
     * <p>
     * Selection logic:
     * <ul>
     * <li>Positive step: select index i if {@code (i - start) % step == 0}</li>
     * <li>Negative step: select index i if {@code (end - i) % step == 0}</li>
     * <li>Range: {@code start <= i < end} (assumes start &lt; end)</li>
     * </ul>
     * <p>
     * Examples (SAPL semantics):
     * <ul>
     * <li>{@code [0,1,2,3,4][1:4:1]} → {@code [1,2,3]}</li>
     * <li>{@code [0,1,2,3,4,5,6,7,8,9][::3]} → {@code [0,3,6,9]}</li>
     * <li>{@code [0,1,2,3,4,5,6,7,8,9][::-1]} → {@code [0,1,2,3,4,5,6,7,8,9]} (all
     * elements!)</li>
     * <li>{@code [0,1,2,3,4,5,6,7,8,9][::-3]} → {@code [1,4,7]}</li>
     * <li>{@code [0,1,2,3,4,5,6,7,8,9][1:5:-1]} → {@code [1,2,3,4]}</li>
     * <li>{@code [0,1,2,3,4,5,6,7,8,9][-2:6:-1]} → {@code []} (from &gt; to)</li>
     * </ul>
     *
     * @param parent the array to slice
     * @param bigIndex the starting index (null defaults to 0)
     * @param bigTo the ending index (null defaults to array.length)
     * @param bigStep the step size (null defaults to 1)
     * @return the sliced array, or an error if parent is not an array or step is
     * zero
     */
    public static Value sliceArray(Value parent, BigDecimal bigIndex, BigDecimal bigTo, BigDecimal bigStep) {
        Integer index = null;
        Integer to    = null;
        Integer step  = null;
        try {
            if (bigIndex != null) {
                index = bigIndex.intValueExact();
            }
            if (bigTo != null) {
                to = bigTo.intValueExact();
            }
            if (bigStep != null) {
                step = bigStep.intValueExact();
            }
        } catch (ArithmeticException exception) {
            return Value.error(exception);
        }

        if (!(parent instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot slice a non-array value. Expected an Array but got %s.".formatted(parent));
        }

        val arraySize  = arrayValue.size();
        val actualStep = step == null ? 1 : step;

        if (actualStep == 0) {
            return Value.error("Step must not be zero.");
        }

        // SAPL always uses same defaults regardless of step direction
        var from  = index == null ? 0 : index;
        var until = to == null ? arraySize : to;

        // Normalize negative indices
        if (from < 0) {
            from += arraySize;
        }
        if (until < 0) {
            until += arraySize;
        }

        // Clamp to valid range
        from  = Math.clamp(from, 0, arraySize);
        until = Math.clamp(until, 0, arraySize);

        val resultBuilder = ArrayValue.builder();

        // SAPL semantics: always iterate forward, use modulo for selection
        for (int i = 0; i < arraySize; i++) {
            // Range check: assumes from < until
            if (i < from || i >= until) {
                continue;
            }

            // Selection based on step direction
            boolean selected;
            if (actualStep > 0) {
                selected = (i - from) % actualStep == 0;
            } else {
                // Negative step uses (until - i) % step for selection
                selected = (until - i) % actualStep == 0;
            }

            if (selected) {
                resultBuilder.add(arrayValue.get(i));
            }
        }

        return resultBuilder.build();
    }

    /**
     * Selects multiple array elements by index union. Returns elements in array
     * order with duplicates removed.
     * Supports negative indices counting from the end.
     *
     * @param parent the array value
     * @param bigIndexes the indices to select (negative values count from end)
     * @return array containing selected elements in array order, or error if parent
     * is not an array or any index out of bounds
     */
    public static Value indexUnion(Value parent, List<BigDecimal> bigIndexes) {
        int[] indexes = new int[bigIndexes.size()];
        for (int i = 0; i < bigIndexes.size(); i++) {
            try {
                indexes[i] = bigIndexes.get(i).intValueExact();
            } catch (ArithmeticException exception) {
                return Value.error(exception);
            }
        }
        if (!(parent instanceof ArrayValue arrayValue)) {
            return Value.error("Index union steps can only be applied to arrays but got %s.".formatted(parent));
        }
        val size  = arrayValue.size();
        val pairs = new int[indexes.length][2];
        for (int i = 0; i < indexes.length; i++) {
            pairs[i][0] = indexes[i];
            pairs[i][1] = indexes[i] < 0 ? indexes[i] + size : indexes[i];
        }
        Arrays.sort(pairs, Comparator.comparingInt(p -> p[1]));
        val arrayBuilder = ArrayValue.builder();
        int lastIndex    = -1;
        for (val pair : pairs) {
            if (pair[1] < 0 || pair[1] >= size) {
                return Value.error("Index %d out of bounds for array of size %d.".formatted(pair[0], size));
            }

            if (pair[1] != lastIndex) {
                lastIndex = pair[1];
                arrayBuilder.add(arrayValue.get(pair[1]));
            }
        }
        return arrayBuilder.build();
    }

    /**
     * Selects multiple object values by key union. Returns values in object's
     * insertion order with duplicates removed.
     * Missing keys are silently skipped.
     *
     * @param parent the object value
     * @param keys the keys to select
     * @return array containing selected values in object's insertion order, or
     * error if parent is not an object
     */
    public static Value attributeUnion(Value parent, List<String> keys) {
        if (!(parent instanceof ObjectValue objectValue)) {
            return Value.error("Key union steps can only be applied to objects but got %s.".formatted(parent));
        }
        val requestedKeys = new HashSet<>(keys);
        val arrayBuilder  = ArrayValue.builder();
        // Loop this way to preserve inner order ob object.
        for (val key : objectValue.keySet()) {
            if (requestedKeys.remove(key)) {
                arrayBuilder.add(objectValue.get(key));
            }
            if (requestedKeys.isEmpty()) {
                break;
            }
        }
        return arrayBuilder.build();
    }

    /**
     * Recursively collects all values from nested arrays and objects into a flat
     * array.
     * Traverses the entire structure depth-first, collecting values in pre-order.
     *
     * @param parent the value to search
     * @return array of all found values at all nesting levels, or error if maximum
     * recursion depth exceeded
     */
    public static Value recursiveWildcardStep(Value parent) {
        val arrayBuilder = ArrayValue.builder();
        try {
            recursiveWildcardStep(parent, 0, arrayBuilder);
            return arrayBuilder.build();
        } catch (MaxRecursionDepthException e) {
            return Value.error("Maximum nesting depth exceeded during recursive wildcard step.");
        }
    }

    private static void recursiveWildcardStep(Value parent, int depth, ArrayValue.Builder builder)
            throws MaxRecursionDepthException {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new MaxRecursionDepthException();
        }
        if (parent instanceof ObjectValue objectValue) {
            for (val value : objectValue.values()) {
                builder.add(value);
                recursiveWildcardStep(value, depth + 1, builder);
            }
        } else if (parent instanceof ArrayValue arrayValue) {
            for (val value : arrayValue) {
                builder.add(value);
                recursiveWildcardStep(value, depth + 1, builder);
            }
        }
    }

    /**
     * Recursively searches for an index in nested arrays, collecting all matching
     * values.
     * Supports negative indices counting from the end.
     *
     * @param parent the value to search
     * @param index the index to search for (negative values count from end)
     * @return array of all found values, or error if maximum recursion depth
     * exceeded
     */
    public static Value recursiveIndexStep(Value parent, BigDecimal index) {
        try {
            int intIndex     = index.intValueExact();
            val arrayBuilder = ArrayValue.builder();
            recursiveIndexStep(parent, intIndex, 0, arrayBuilder);
            return arrayBuilder.build();
        } catch (ArithmeticException exception) {
            return Value.error(exception);
        } catch (MaxRecursionDepthException e) {
            return Value.error("Maximum nesting depth exceeded during recursive index step.");
        }
    }

    private static void recursiveIndexStep(Value parent, int index, int depth, ArrayValue.Builder builder)
            throws MaxRecursionDepthException {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new MaxRecursionDepthException();
        }
        if (parent instanceof ObjectValue objectValue) {
            for (val value : objectValue.values()) {
                recursiveIndexStep(value, index, depth + 1, builder);
            }
        } else if (parent instanceof ArrayValue arrayValue) {
            var normalizedIndex = index < 0 ? index + arrayValue.size() : index;
            if (normalizedIndex >= 0 && normalizedIndex < arrayValue.size()) {
                builder.add(arrayValue.get(normalizedIndex));
            }
            for (val value : arrayValue) {
                recursiveIndexStep(value, index, depth + 1, builder);
            }
        }
    }

    @StandardException
    private static class MaxRecursionDepthException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;
    }
}
