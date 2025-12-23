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
package io.sapl.compiler.operators;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.*;
import io.sapl.compiler.Error;
import lombok.NonNull;
import lombok.experimental.StandardException;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@UtilityClass
public class StepOperators {

    private static final int MAX_RECURSION_DEPTH = 500;

    private static final String RUNTIME_ERROR_EXPRESSION_STEP_TYPE_MISMATCH = "Expression in expression step must return a number or text, but got %s.";
    private static final String RUNTIME_ERROR_INDEX_OUT_OF_BOUNDS           = "Index %d out of bounds for array of size %d.";
    private static final String RUNTIME_ERROR_INDEX_UNION_REQUIRES_ARRAY    = "Index union steps can only be applied to arrays but got %s.";
    private static final String RUNTIME_ERROR_KEY_UNION_REQUIRES_OBJECT     = "Key union steps can only be applied to objects but got %s.";
    private static final String RUNTIME_ERROR_MAX_RECURSION_DEPTH_INDEX     = "Maximum nesting depth exceeded during recursive index step.";
    private static final String RUNTIME_ERROR_MAX_RECURSION_DEPTH_KEY       = "Maximum nesting depth exceeded during recursive key step.";
    private static final String RUNTIME_ERROR_MAX_RECURSION_DEPTH_WILDCARD  = "Maximum nesting depth exceeded during recursive wildcard step.";
    private static final String RUNTIME_ERROR_SLICING_STEP_ZERO             = "Step must not be zero.";
    private static final String RUNTIME_ERROR_WILDCARD_TYPE_MISMATCH        = "Wildcard steps '.*' can only be applied to arrays or objects but got %s.";

    /**
     * Performs an index or key step based on the expression result type. Uses the
     * expression result as an array index if numeric, or an object key if text.
     */
    public static Value indexOrKeyStep(ParserRuleContext astNode, Value value, Value expressionResult) {
        val metadata = ValueMetadata.merge(value, expressionResult);
        if (value instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (expressionResult instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        return switch (expressionResult) {
        case NumberValue numberValue -> indexStep(astNode, value, numberValue.value(), metadata);
        case TextValue textValue     -> keyStep(astNode, value, textValue.value()).withMetadata(metadata);
        default                      ->
            Error.at(astNode, metadata, RUNTIME_ERROR_EXPRESSION_STEP_TYPE_MISMATCH, expressionResult);
        };
    }

    /**
     * Accesses an object property by key. When applied to an array, projects the
     * key access across all elements, returning an array of results.
     * Returns UNDEFINED if key not found or parent is not an object/array.
     */
    public static Value keyStep(ParserRuleContext astNode, Value parent, String key) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof ArrayValue arrayValue) {
            val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
            for (val element : arrayValue) {
                val result = keyStep(astNode, element, key);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.add(result);
                }
            }
            return builder.build();
        }
        if (!(parent instanceof ObjectValue objectValue)) {
            return Value.UNDEFINED.withMetadata(parent.metadata());
        }
        val content = objectValue.get(key);
        if (content == null) {
            return Value.UNDEFINED.withMetadata(parent.metadata());
        }
        return content;
    }

    /**
     * Accesses an array element by index. Supports negative indices counting from
     * the end.
     * Returns UNDEFINED if parent is not an array, ERROR if index out of bounds.
     */
    public static Value indexStep(ParserRuleContext astNode, Value parent, @NonNull BigDecimal bigIndex,
            ValueMetadata metadata) {
        if (parent instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        int index;
        try {
            index = bigIndex.intValueExact();
        } catch (ArithmeticException e) {
            return Error.at(astNode, metadata, e.getMessage());
        }
        if (!(parent instanceof ArrayValue arrayValue)) {
            return Value.UNDEFINED.withMetadata(metadata);
        }
        var normalizedIndex = index < 0 ? index + arrayValue.size() : index;
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_INDEX_OUT_OF_BOUNDS, index, arrayValue.size());
        }
        return arrayValue.get(normalizedIndex).withMetadata(metadata);
    }

    /**
     * Selects all elements from an array or all values from an object.
     */
    public static Value wildcardStep(ParserRuleContext astNode, Value parent) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof ArrayValue) {
            return parent;
        }
        if (parent instanceof ObjectValue objectValue) {
            return Value.ofArray(objectValue.values().toArray(Value[]::new));
        }
        return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_WILDCARD_TYPE_MISMATCH, parent);
    }

    /**
     * Recursively searches for a key in nested objects and arrays, collecting all
     * matching values.
     */
    public static Value recursiveKeyStep(ParserRuleContext astNode, Value parent, String key, ValueMetadata metadata) {
        if (parent instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        val arrayBuilder = ArrayValue.builder();
        try {
            recursiveKeyStep(parent, key, 0, arrayBuilder);
            return arrayBuilder.build().withMetadata(metadata);
        } catch (MaxRecursionDepthException e) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_MAX_RECURSION_DEPTH_KEY);
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
                builder.add(potentialMatch);
            }
            for (val value : objectValue.values()) {
                recursiveKeyStep(value, key, depth + 1, builder);
            }
        } else if (parent instanceof ArrayValue arrayValue) {
            for (val value : arrayValue) {
                recursiveKeyStep(value, key, depth + 1, builder);
            }
        }
    }

    /**
     * Applies array slicing using SAPL semantics.
     */
    public static Value sliceArray(ParserRuleContext astNode, Value parent, BigDecimal bigIndex, BigDecimal bigTo,
            BigDecimal bigStep) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (!(parent instanceof ArrayValue arrayValue)) {
            return Value.UNDEFINED;
        }

        int from;
        int to;
        int step;
        try {
            from = bigIndex == null ? Integer.MIN_VALUE : bigIndex.intValueExact();
            to   = bigTo == null ? Integer.MAX_VALUE : bigTo.intValueExact();
            step = bigStep == null ? 1 : bigStep.intValueExact();
        } catch (ArithmeticException exception) {
            return Error.at(astNode, ValueMetadata.EMPTY, exception.getMessage());
        }

        if (step == 0) {
            return Error.at(astNode, ValueMetadata.EMPTY, RUNTIME_ERROR_SLICING_STEP_ZERO);
        }

        return sliceArrayWithParameters(arrayValue, from, to, step);
    }

    private static Value sliceArrayWithParameters(ArrayValue arrayValue, int rawFrom, int rawTo, int step) {
        val arraySize = arrayValue.size();

        var from  = rawFrom == Integer.MIN_VALUE ? 0 : rawFrom;
        var until = rawTo == Integer.MAX_VALUE ? arraySize : rawTo;

        from  = normalizeAndClampIndex(from, arraySize);
        until = normalizeAndClampIndex(until, arraySize);

        val resultBuilder = ArrayValue.builder();
        for (int i = from; i < until; i++) {
            if (isSelectedByStep(i, from, until, step)) {
                resultBuilder.add(arrayValue.get(i));
            }
        }

        return resultBuilder.build();
    }

    private static int normalizeAndClampIndex(int index, int arraySize) {
        val normalized = index < 0 ? index + arraySize : index;
        return Math.clamp(normalized, 0, arraySize);
    }

    private static boolean isSelectedByStep(int index, int from, int until, int step) {
        return step > 0 ? (index - from) % step == 0 : (until - index) % step == 0;
    }

    /**
     * Selects multiple array elements by index union.
     */
    public static Value indexUnion(ParserRuleContext astNode, Value parent, List<BigDecimal> bigIndexes) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        int[] indexes = new int[bigIndexes.size()];
        for (int i = 0; i < bigIndexes.size(); i++) {
            try {
                indexes[i] = bigIndexes.get(i).intValueExact();
            } catch (ArithmeticException exception) {
                return Error.at(astNode, parent.metadata(), exception.getMessage());
            }
        }
        if (!(parent instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_INDEX_UNION_REQUIRES_ARRAY, parent);
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
                return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_INDEX_OUT_OF_BOUNDS, pair[0], size);
            }

            if (pair[1] != lastIndex) {
                lastIndex = pair[1];
                arrayBuilder.add(arrayValue.get(pair[1]));
            }
        }
        return arrayBuilder.build();
    }

    /**
     * Selects multiple object values by key union.
     */
    public static Value attributeUnion(ParserRuleContext astNode, Value parent, List<String> keys) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (!(parent instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_KEY_UNION_REQUIRES_OBJECT, parent);
        }
        val requestedKeys = new HashSet<>(keys);
        val arrayBuilder  = ArrayValue.builder();
        for (val entry : objectValue.entrySet()) {
            if (requestedKeys.remove(entry.getKey())) {
                arrayBuilder.add(entry.getValue());
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
     */
    public static Value recursiveWildcardStep(ParserRuleContext astNode, Value parent) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        val arrayBuilder = ArrayValue.builder();
        try {
            recursiveWildcardStep(parent, 0, arrayBuilder);
            return arrayBuilder.build();
        } catch (MaxRecursionDepthException e) {
            return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_MAX_RECURSION_DEPTH_WILDCARD);
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
     */
    public static Value recursiveIndexStep(ParserRuleContext astNode, Value parent, BigDecimal index) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        try {
            int intIndex     = index.intValueExact();
            val arrayBuilder = ArrayValue.builder();
            recursiveIndexStep(parent, intIndex, 0, arrayBuilder);
            return arrayBuilder.build();
        } catch (ArithmeticException exception) {
            return Error.at(astNode, parent.metadata(), exception.getMessage());
        } catch (MaxRecursionDepthException e) {
            return Error.at(astNode, parent.metadata(), RUNTIME_ERROR_MAX_RECURSION_DEPTH_INDEX);
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
