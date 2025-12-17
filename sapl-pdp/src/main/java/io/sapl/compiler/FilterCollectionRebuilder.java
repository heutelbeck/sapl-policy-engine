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
package io.sapl.compiler;

import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.*;

/**
 * Utility class for rebuilding arrays and objects after filter operations.
 * <p>
 * Handles the common pattern of applying transformations to specific elements
 * while preserving the structure and
 * filtering out undefined values.
 */
@UtilityClass
class FilterCollectionRebuilder {

    /**
     * Rebuilds an array by applying a transformation to specific indices.
     * <p>
     * Elements at matching indices are transformed. Undefined results are filtered
     * out. Elements at non-matching
     * indices are preserved unchanged.
     * <p>
     * The original array's metadata is preserved in the rebuilt array.
     *
     * @param original
     * the original array
     * @param matcher
     * predicate determining which indices to transform
     * @param transformer
     * function to transform matched elements
     *
     * @return rebuilt array with transformations applied
     */
    static ArrayValue rebuildArray(ArrayValue original, IntPredicate matcher,
            java.util.function.IntFunction<Value> transformer) {
        val builder = ArrayValue.builder().withMetadata(original.metadata());

        for (int i = 0; i < original.size(); i++) {
            if (matcher.test(i)) {
                val transformed = transformer.apply(i);
                if (!(transformed instanceof UndefinedValue)) {
                    builder.add(transformed);
                }
            } else {
                builder.add(original.get(i));
            }
        }

        return builder.build();
    }

    /**
     * Rebuilds an array by applying a transformation to all elements.
     * <p>
     * All elements are transformed. Undefined results are filtered out.
     *
     * @param original
     * the original array
     * @param transformer
     * function to transform each element
     *
     * @return rebuilt array with transformations applied
     */
    static ArrayValue rebuildArrayAll(ArrayValue original, java.util.function.IntFunction<Value> transformer) {
        return rebuildArray(original, i -> true, transformer);
    }

    /**
     * Rebuilds an object by applying a transformation to specific keys.
     * <p>
     * Fields with matching keys are transformed. Undefined results are filtered out
     * (field removed). Fields with
     * non-matching keys are preserved unchanged.
     * <p>
     * The original object's metadata is preserved in the rebuilt object.
     *
     * @param original
     * the original object
     * @param matcher
     * predicate determining which keys to transform
     * @param transformer
     * function to transform matched field values (receives key)
     *
     * @return rebuilt object with transformations applied
     */
    static ObjectValue rebuildObject(ObjectValue original, Predicate<String> matcher,
            java.util.function.Function<String, Value> transformer) {
        val builder = ObjectValue.builder().withMetadata(original.metadata());

        for (val entry : original.entrySet()) {
            if (matcher.test(entry.getKey())) {
                val transformed = transformer.apply(entry.getKey());
                if (!(transformed instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), transformed);
                }
            } else {
                builder.put(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    /**
     * Rebuilds an object by applying a transformation to all fields.
     * <p>
     * All field values are transformed. Undefined results are filtered out (field
     * removed).
     *
     * @param original
     * the original object
     * @param transformer
     * function to transform each field value (receives key)
     *
     * @return rebuilt object with transformations applied
     */
    static ObjectValue rebuildObjectAll(ObjectValue original, Function<String, Value> transformer) {
        return rebuildObject(original, key -> true, transformer);
    }

    /**
     * Traverses array elements with early error return.
     * <p>
     * Applies transformer to each element, returning early if an error is
     * encountered. Does not filter undefined
     * values.
     * <p>
     * The original array's metadata is preserved in the rebuilt array.
     *
     * @param array
     * source array
     * @param transformer
     * function to transform each element
     *
     * @return rebuilt array or error if any transformation fails
     */
    static Value traverseArray(ArrayValue array, UnaryOperator<Value> transformer) {
        val builder = ArrayValue.builder().withMetadata(array.metadata());
        for (val element : array) {
            val transformed = transformer.apply(element);
            if (transformed instanceof ErrorValue) {
                return transformed;
            }
            builder.add(transformed);
        }
        return builder.build();
    }

    /**
     * Traverses object fields with early error return.
     * <p>
     * Applies transformer to each field value, returning early if an error is
     * encountered. Does not filter undefined
     * values.
     * <p>
     * The original object's metadata is preserved in the rebuilt object.
     *
     * @param object
     * source object
     * @param transformer
     * function to transform each field value
     *
     * @return rebuilt object or error if any transformation fails
     */
    static Value traverseObject(ObjectValue object, UnaryOperator<Value> transformer) {
        val builder = ObjectValue.builder().withMetadata(object.metadata());
        for (val entry : object.entrySet()) {
            val transformed = transformer.apply(entry.getValue());
            if (transformed instanceof ErrorValue) {
                return transformed;
            }
            builder.put(entry.getKey(), transformed);
        }
        return builder.build();
    }

    /**
     * Traverses selected array elements with early error return.
     * <p>
     * Applies transformer to matching elements, preserving non-matching elements
     * unchanged. Does not filter undefined
     * values.
     * <p>
     * The original array's metadata is preserved in the rebuilt array.
     *
     * @param array
     * source array
     * @param selector
     * predicate identifying indices to transform
     * @param transformer
     * function to transform selected elements
     *
     * @return rebuilt array or error if any transformation fails
     */
    static Value traverseArraySelective(ArrayValue array, IntPredicate selector, IntFunction<Value> transformer) {
        val builder = ArrayValue.builder().withMetadata(array.metadata());
        for (int index = 0; index < array.size(); index++) {
            if (selector.test(index)) {
                val transformed = transformer.apply(index);
                if (transformed instanceof ErrorValue) {
                    return transformed;
                }
                builder.add(transformed);
            } else {
                builder.add(array.get(index));
            }
        }
        return builder.build();
    }

    /**
     * Traverses selected object fields with early error return.
     * <p>
     * Applies transformer to matching fields, preserving non-matching fields
     * unchanged. Does not filter undefined
     * values.
     *
     * @param object
     * source object
     * @param selector
     * predicate identifying keys to transform
     * @param transformer
     * function to transform selected field values
     *
     * @return rebuilt object or error if any transformation fails
     */
    static Value traverseObjectSelective(ObjectValue object, Predicate<String> selector,
            Function<String, Value> transformer) {
        return traverseObjectSelective(object, selector, transformer, object::get);
    }

    /**
     * Traverses object fields with different transformers for matching and
     * non-matching keys.
     * <p>
     * Applies matchingTransformer to fields matching the selector, and
     * nonMatchingTransformer to other fields. Returns
     * early on error from either transformer.
     * <p>
     * The original object's metadata is preserved in the rebuilt object.
     *
     * @param object
     * source object
     * @param selector
     * predicate identifying keys for matching transformer
     * @param matchingTransformer
     * function to transform matching field values
     * @param nonMatchingTransformer
     * function to transform non-matching field values
     *
     * @return rebuilt object or error if any transformation fails
     */
    static Value traverseObjectSelective(ObjectValue object, Predicate<String> selector,
            Function<String, Value> matchingTransformer, Function<String, Value> nonMatchingTransformer) {
        val builder = ObjectValue.builder().withMetadata(object.metadata());
        for (val entry : object.entrySet()) {
            val transformer = selector.test(entry.getKey()) ? matchingTransformer : nonMatchingTransformer;
            val transformed = transformer.apply(entry.getKey());
            if (transformed instanceof ErrorValue) {
                return transformed;
            }
            builder.put(entry.getKey(), transformed);
        }
        return builder.build();
    }

    /**
     * Normalizes array index to handle negative indices.
     *
     * @param index
     * raw index (may be negative)
     * @param arraySize
     * array size
     *
     * @return normalized non-negative index
     */
    static int normalizeIndex(int index, int arraySize) {
        return index < 0 ? arraySize + index : index;
    }

    /**
     * Creates slice predicate for array slicing filter operations.
     *
     * @param from
     * start index (inclusive)
     * @param to
     * end index (exclusive)
     * @param step
     * step value (non-zero)
     *
     * @return predicate matching indices in slice range
     */
    static IntPredicate slicePredicate(int from, int to, int step) {
        return index -> {
            if (index < from || index >= to) {
                return false;
            }
            if (step > 0) {
                return (index - from) % step == 0;
            }
            return (to - index) % step == 0;
        };
    }
}
