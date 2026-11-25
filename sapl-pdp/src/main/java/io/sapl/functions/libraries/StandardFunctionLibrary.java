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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.val;

@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION, libraryDocumentation = StandardFunctionLibrary.DOCUMENTATION)
public class StandardFunctionLibrary {

    public static final String NAME          = "standard";
    public static final String DESCRIPTION   = "Essential utility functions for measuring size, converting values, and handling errors.";
    public static final String DOCUMENTATION = """
            # Standard Functions

            Essential utility functions available in every SAPL policy for common operations
            like measuring collection sizes, converting values to strings, and handling errors
            gracefully.

            ## Error Handling

            Check the denominator before dividing in a policy.

            ```sapl
            policy "safe division"
            permit
                resource.total != 0;
                resource.count / resource.total > 0.5;
            ```

            ## Measuring Size

            The `length` function works uniformly across text, arrays, and objects:

            ```sapl
            policy "limit items"
            deny
                action == "add_item" & standard.length(resource.cart) >= 100;
            ```
            """;

    private static final String ERROR_ARGUMENT_MUST_BE_TEXT_ARRAY_OR_OBJECT = "Argument must be a text, array, or object.";

    /**
     * Returns the length of the given value.
     *
     * @param value
     * the value to measure (TEXT, ARRAY, or OBJECT)
     *
     * @return the codepoint count for text or collection size as a NumberValue, or an ErrorValue for unsupported types
     */
    @Function(docs = """
            ```length(ARRAY|TEXT|JSON value)```: For TEXT it counts Unicode code points, including combining marks separately.
            For ARRAY, it returns the number of elements in the array.
            For OBJECT, it returns the number of keys in the OBJECT.
            For NUMBER, BOOLEAN, or NULL, the function will return an error.

            **Example:**
            ```sapl
            policy "example"
            permit
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
        case TextValue text     -> Value.of(text.value().codePointCount(0, text.value().length()));
        case ObjectValue object -> Value.of(object.size());
        case ArrayValue array   -> Value.of(array.size());
        default                 -> Value.error(ERROR_ARGUMENT_MUST_BE_TEXT_ARRAY_OR_OBJECT);
        };

    }

    /**
     * Converts any value to its string representation.
     *
     * @param value
     * the value to convert to a string
     *
     * @return a TextValue containing the string representation
     */
    @Function(name = "toString", docs = """
            ```toString(value)```: Converts any ```value``` to a string representation.


            **Example:**
            ```sapl
            policy "example"
            permit
              standard.toString([1, 2, 3]) == "[1, 2, 3]";
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Value asString(Value value) {
        if (value instanceof TextValue(var text)) {
            return Value.of(text);
        }
        return Value.of(value.toString());
    }

    /**
     * Returns the fallback value if the guarded expression is an error.
     *
     * @param guardedExpression
     * the expression result to check for errors
     * @param fallback
     * the value to return if guardedExpression is an error
     *
     * @return the guardedExpression if not an error, otherwise the fallback
     */
    @Function(docs = """
            ```onErrorMap(guardedExpression, fallbackExpression)``` returns the fallback when the first argument is an error.
            Otherwise it returns the first argument.
            A SAPL policy propagates errors while evaluating the arguments, before it calls the function.
            """)
    public static Value onErrorMap(Value guardedExpression, Value fallback) {
        if (guardedExpression instanceof ErrorValue) {
            return fallback;
        }
        return guardedExpression;
    }

}
