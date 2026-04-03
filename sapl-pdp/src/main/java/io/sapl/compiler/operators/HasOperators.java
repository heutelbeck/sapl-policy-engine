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
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

/**
 * Key membership operations for the {@code has} operator.
 * <p>
 * Left-hand side is permissive: non-object types return {@code false}.
 * Right-hand side is strict: non-string (for ONE) or non-array-of-strings
 * (for ANY/ALL) produces an error.
 */
@UtilityClass
public class HasOperators {

    private static final String ERROR_HAS_KEY_MUST_BE_STRING        = "has: key must be a string, but got: %s.";
    private static final String ERROR_HAS_KEYS_MUST_BE_ARRAY        = "has any/all: keys must be an array, but got: %s.";
    private static final String ERROR_HAS_KEYS_MUST_BE_STRING_ARRAY = "has any/all: array elements must be strings, but got: %s at index %d.";

    /**
     * {@code obj has "key"} - checks if a single key exists in the object.
     */
    public static Value hasOne(Value base, Value key, SourceLocation location) {
        if (key instanceof UndefinedValue || base instanceof UndefinedValue) {
            return Value.FALSE;
        }
        if (!(key instanceof TextValue(var k))) {
            return Value.errorAt(location, ERROR_HAS_KEY_MUST_BE_STRING, key.getClass().getSimpleName());
        }
        if (!(base instanceof ObjectValue obj)) {
            return Value.FALSE;
        }
        return obj.containsKey(k) ? Value.TRUE : Value.FALSE;
    }

    /**
     * {@code obj has any ["a", "b"]} - checks if at least one key exists.
     */
    public static Value hasAny(Value base, Value keys, SourceLocation location) {
        if (keys instanceof UndefinedValue || base instanceof UndefinedValue) {
            return Value.FALSE;
        }
        if (!(keys instanceof ArrayValue arr)) {
            return Value.errorAt(location, ERROR_HAS_KEYS_MUST_BE_ARRAY, keys.getClass().getSimpleName());
        }
        if (!(base instanceof ObjectValue obj)) {
            return Value.FALSE;
        }
        for (int i = 0; i < arr.size(); i++) {
            if (!(arr.get(i) instanceof TextValue(var k))) {
                return Value.errorAt(location, ERROR_HAS_KEYS_MUST_BE_STRING_ARRAY,
                        arr.get(i).getClass().getSimpleName(), i);
            }
            if (obj.containsKey(k)) {
                return Value.TRUE;
            }
        }
        return Value.FALSE;
    }

    /**
     * {@code obj has all ["a", "b"]} - checks if all keys exist.
     */
    public static Value hasAll(Value base, Value keys, SourceLocation location) {
        if (keys instanceof UndefinedValue || base instanceof UndefinedValue) {
            return Value.FALSE;
        }
        if (!(keys instanceof ArrayValue arr)) {
            return Value.errorAt(location, ERROR_HAS_KEYS_MUST_BE_ARRAY, keys.getClass().getSimpleName());
        }
        if (!(base instanceof ObjectValue obj)) {
            return Value.FALSE;
        }
        for (int i = 0; i < arr.size(); i++) {
            if (!(arr.get(i) instanceof TextValue(var k))) {
                return Value.errorAt(location, ERROR_HAS_KEYS_MUST_BE_STRING_ARRAY,
                        arr.get(i).getClass().getSimpleName(), i);
            }
            if (!obj.containsKey(k)) {
                return Value.FALSE;
            }
        }
        return Value.TRUE;
    }

}
