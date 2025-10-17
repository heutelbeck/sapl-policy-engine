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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

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
            ```
            import standard.*
            policy "example"
            permit
            where
              length([1, 2, 3, 4]) == 4;
              length("example") == 7;
              length({ "key1" : 1, "key2" : 2}) == 2;
            ```
            """, schema = """
            {
              "type": "integer"
            }""")
    public static Val length(@Array @Text @JsonObject Val value) {
        if (value.isTextual()) {
            return Val.of(value.getText().length());
        }
        return Val.of(value.get().size());
    }

    @Function(name = "toString", docs = """
            ```toString(value)```: Converts any ```value``` to a string representation.


            **Example:**
            ```
            import standard.*
            policy "example"
            permit
            where
              toString([1,2,3]) == "[1,2,3]";
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val asString(Val value) {
        if (value.isTextual()) {
            return Val.of(value.get().asText());
        }
        return Val.of(value.toString());
    }

    @Function(docs = """
            ```onErrorMap(guardedExpression, fallbackExpression)```: If evaluation of ```guardedExpression``` results in an error,
            the ```fallback``` is returned instead. Otherwise the result of ```guardedExpression``` is returned.

            **Example:**
            ```
            import standard.*
            policy "example"
            permit
            where
              onErrorMap(1/0,999) == 999;
            ```
            """)
    public static Val onErrorMap(Val guardedExpression, Val fallback) {
        if (guardedExpression.isError()) {
            return fallback;
        }
        return guardedExpression;
    }

}
