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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION)
public class StandardFunctionLibrary {

    public static final String NAME        = "standard";
    public static final String DESCRIPTION = "This the standard function library for SAPL.";

    @Function(docs = """
            ```length(ARRAY|TEXT|JSON value)```: For TEXT it returns the length of the text string.
            For ARRAY, it returns the number of elements in the array.
            For OBJECT, it returns the number of keys in the OBJECT.
            For NUMBER, BOOLEAN, or NULL, the function will return an error.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              standard.length([1, 2, 3, 4]) == 4;
              standard.length("example") == 7;
              standard.length({ "key1" : 1, "key2" : 2}) == 2;
            ```
            """, schema = """
            {
              "type": "integer"
            }""")
    public static Value length(Value value) {
        return switch (value) {
        case TextValue text     -> Value.of(text.toString().length() - 2L);
        case ObjectValue object -> Value.of(object.size());
        case ArrayValue array   -> Value.of(array.size());
        default                 -> Value.error("Argument must be a text, array, or object.");
        };

    }

    @Function(name = "toString", docs = """
            ```toString(value)```: Converts any ```value``` to a string representation.


            **Example:**
            ```sapl
            policy "example"
            permit
            where
              standard.asString([1,2,3]) == "[1,2,3]";
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Value asString(Value value) {
        if (value instanceof TextValue text) {
            val str = text.toString();
            return Value.of(str.substring(1, str.length() - 1));
        }
        return Value.of(value.toString());
    }

    @Function(docs = """
            ```onErrorMap(guardedExpression, fallbackExpression)```: If evaluation of ```guardedExpression``` results in an error,
            the ```fallback``` is returned instead. Otherwise the result of ```guardedExpression``` is returned.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              standard.onErrorMap(1/0,999) == 999;
            ```
            """)
    public static Value onErrorMap(Value guardedExpression, Value fallback) {
        if (guardedExpression instanceof ErrorValue) {
            return fallback;
        }
        return guardedExpression;
    }

}
