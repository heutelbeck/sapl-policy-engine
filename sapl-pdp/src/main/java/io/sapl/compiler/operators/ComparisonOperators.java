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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

/**
 * Comparison operations for SAPL expression evaluation.
 * <p>
 * Supports equality testing, containment checking in collections, and regular
 * expression matching.
 */
@UtilityClass
public class ComparisonOperators {

    private static final String ERROR_IN_TYPE_MISMATCH = "'in' operator supports value lookup in arrays or objects, "
            + "as well as substring matching with two strings. But got: %s in %s.";

    /**
     * Tests two values for equality using Value.equals() semantics.
     */
    public static Value equals(Value a, Value b) {
        return a.equals(b) ? Value.TRUE : Value.FALSE;
    }

    /**
     * Tests two values for inequality.
     */
    public static Value notEquals(Value a, Value b) {
        return !a.equals(b) ? Value.TRUE : Value.FALSE;
    }

    /**
     * Tests whether a value is contained in a collection or string.
     * <p>
     * Supports:
     * <ul>
     * <li>Value lookup in arrays (checks if needle equals any element)</li>
     * <li>Value lookup in objects (checks if needle equals any value)</li>
     * <li>Substring matching in strings</li>
     * </ul>
     */
    public static Value isContainedIn(Value needle, Value haystack, SourceLocation location) {
        return switch (haystack) {
        case ArrayValue array                                                      ->
            array.contains(needle) ? Value.TRUE : Value.FALSE;
        case ObjectValue object                                                    ->
            object.containsValue(needle) ? Value.TRUE : Value.FALSE;
        case TextValue textHaystack when needle instanceof TextValue(String value) ->
            textHaystack.value().contains(value) ? Value.TRUE : Value.FALSE;
        default                                                                    ->
            Value.errorAt(location, ERROR_IN_TYPE_MISMATCH, needle, haystack);
        };
    }

}
