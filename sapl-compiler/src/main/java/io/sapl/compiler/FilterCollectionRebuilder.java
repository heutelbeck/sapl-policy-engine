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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

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
        val builder = ArrayValue.builder();

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
        val builder = ObjectValue.builder();

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
    static ObjectValue rebuildObjectAll(ObjectValue original, java.util.function.Function<String, Value> transformer) {
        return rebuildObject(original, key -> true, transformer);
    }

}
