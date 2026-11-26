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

import static io.sapl.api.model.ValueRepresentation.containsError;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import lombok.val;
import org.jspecify.annotations.Nullable;

/** Successful pure function values retained only for one evaluation round. */
public final class FunctionEvaluationCache {
    private @Nullable Map<Object, Value> values;

    FunctionEvaluationCache() {
    }

    /**
     * Computes a reached call, or returns its successful value from this round.
     *
     * @param identity compile-time identity of the repeated computation
     * @param evaluate the original evaluation at this source occurrence
     * @return the value, preserving errors at their original occurrence
     */
    Value evaluate(Object identity, Supplier<Value> evaluate) {
        if (values != null) {
            val known = values.get(identity);
            if (known != null) {
                return known;
            }
        }
        val value = evaluate.get();
        if (!containsError(value)) {
            if (values == null) {
                values = new IdentityHashMap<>();
            }
            values.put(identity, value);
        }
        return value;
    }
}
