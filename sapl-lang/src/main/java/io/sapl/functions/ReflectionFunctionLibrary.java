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
import lombok.experimental.UtilityClass;

/**
 * Functions for runtime type inspection and reflection on Val objects.
 */
@UtilityClass
@FunctionLibrary(name = ReflectionFunctionLibrary.NAME, description = ReflectionFunctionLibrary.DESCRIPTION)
public class ReflectionFunctionLibrary {

    public static final String NAME        = "reflect";
    public static final String DESCRIPTION = "Functions for runtime type inspection and reflection.";

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_STRING = """
            {
                "type": "string"
            }
            """;

    /**
     * Checks if the value is a JSON array.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is an array, VAL.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isArray(ANY value)```: Returns ```true``` if the value is a JSON array, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isArray([1, 2, 3]);        // true
              reflect.isArray([]);               // true
              reflect.isArray({"key": "val"});   // false
              reflect.isArray(undefined);        // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isArray(Val value) {
        return Val.of(value.isArray());
    }

    /**
     * Checks if the value is a JSON object.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is an object, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isObject(ANY value)```: Returns ```true``` if the value is a JSON object, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isObject({"key": "val"});  // true
              reflect.isObject({});              // true
              reflect.isObject([1, 2, 3]);       // false
              reflect.isObject(null);            // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isObject(Val value) {
        return Val.of(value.isObject());
    }

    /**
     * Checks if the value is a text string.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is textual, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isText(ANY value)```: Returns ```true``` if the value is a text string, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isText("hello");           // true
              reflect.isText("");                // true
              reflect.isText(123);               // false
              reflect.isText(undefined);         // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isText(Val value) {
        return Val.of(value.isTextual());
    }

    /**
     * Checks if the value is a number (integer or floating-point).
     *
     * @param value the value to check
     * @return Val.TRUE if the value is a number, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isNumber(ANY value)```: Returns ```true``` if the value is a number (integer or floating-point),
            ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isNumber(42);              // true
              reflect.isNumber(3.14);            // true
              reflect.isNumber(5.0);             // true
              reflect.isNumber("123");           // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isNumber(Val value) {
        return Val.of(value.isNumber());
    }

    /**
     * Checks if the value is a boolean.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is a boolean, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isBoolean(ANY value)```: Returns ```true``` if the value is a boolean (```true``` or ```false```),
            ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isBoolean(true);           // true
              reflect.isBoolean(false);          // true
              reflect.isBoolean(1);              // false
              reflect.isBoolean("true");         // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isBoolean(Val value) {
        return Val.of(value.isBoolean());
    }

    /**
     * Checks if the value is an integer number (no decimal point).
     * Numbers like 5.0 are considered integers as they are mathematically
     * equivalent to 5.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is an integer number, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isInteger(ANY value)```: Returns ```true``` if the value is an integer number (no fractional part),
            ```false``` otherwise. Numbers like ```5.0``` are considered integers as they are mathematically equivalent to ```5```.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isInteger(42);             // true
              reflect.isInteger(5.0);            // false
              reflect.isInteger(3.14);           // false
              reflect.isInteger("5");            // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isInteger(Val value) {
        if (!value.isNumber()) {
            return Val.FALSE;
        }
        return Val.of(value.get().isIntegralNumber());
    }

    /**
     * Checks if the value is a floating-point number (has a decimal point or
     * fractional part).
     *
     * @param value the value to check
     * @return Val.TRUE if the value is a floating-point number, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isFloat(ANY value)```: Returns ```true``` if the value is a floating-point number with a fractional part,
            ```false``` otherwise. Integral numbers like ```5``` or ```5.0``` return ```false```.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isFloat(3.14);             // true
              reflect.isFloat(0.5);              // true
              reflect.isFloat(5.0);              // true
              reflect.isFloat(42);               // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isFloat(Val value) {
        if (!value.isNumber()) {
            return Val.FALSE;
        }
        return Val.of(value.isFloatingPointNumber());
    }

    /**
     * Checks if the value is JSON null.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is null, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isNull(ANY value)```: Returns ```true``` if the value is JSON ```null```, ```false``` otherwise.
            This is distinct from ```undefined```.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isNull(null);              // true
              reflect.isNull(undefined);         // false
              reflect.isNull(0);                 // false
              reflect.isNull("");                // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isNull(Val value) {
        return Val.of(value.isNull());
    }

    /**
     * Checks if the value is undefined.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is undefined, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isUndefined(ANY value)```: Returns ```true``` if the value is ```undefined```, ```false``` otherwise.
            This is distinct from ```null``` or an error.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isUndefined(undefined);    // true
              reflect.isUndefined(null);         // false
              reflect.isUndefined(0);            // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isUndefined(Val value) {
        return Val.of(value.isUndefined());
    }

    /**
     * Checks if the value is defined (not undefined and not an error).
     *
     * @param value the value to check
     * @return Val.TRUE if the value is defined, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isDefined(ANY value)```: Returns ```true``` if the value is defined (not ```undefined``` and not an error),
            ```false``` otherwise. ```null``` is considered defined.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isDefined(42);             // true
              reflect.isDefined(null);           // true
              reflect.isDefined(undefined);      // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isDefined(Val value) {
        return Val.of(value.isDefined());
    }

    /**
     * Checks if the value is an error.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is an error, Val.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isError(ANY value)```: Returns ```true``` if the value represents an error, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var result = 10 / 0;  // Produces an error
              reflect.isError(result);           // true
              reflect.isError(42);               // false
              reflect.isError(undefined);        // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isError(Val value) {
        return Val.of(value.isError());
    }

    /**
     * Checks if the value is marked as secret.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is marked as secret, Val.TRUE otherwise
     */
    @Function(docs = """
            ```reflect.isSecret(ANY value)```: Returns ```true``` if the value is marked as secret, ```false``` otherwise.
            Secret values are redacted in traces and logs for security purposes.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isSecret(secretData);      // true if marked as secret in the variables. Only in EE Server.
              reflect.isSecret("public data");   // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isSecret(Val value) {
        return Val.of(value.isSecret());
    }

    /**
     * Checks if an array or object is empty.
     *
     * @param value the value to check
     * @return Val.TRUE if the value is an empty array or object, Val.FALSE
     * otherwise
     */
    @Function(docs = """
            ```reflect.isEmpty(ANY value)```: Returns ```true``` if the value is an empty array or empty object,
            ```false``` otherwise. For non-container types, returns ```false```.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.isEmpty([]);               // true
              reflect.isEmpty({});               // true
              reflect.isEmpty([1, 2]);           // false
              reflect.isEmpty({"a": 1});         // false
              reflect.isEmpty(null);             // true
              reflect.isEmpty("");               // true
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isEmpty(Val value) {
        return Val.of(value.isEmpty());
    }

    /**
     * Returns a string describing the type of the value.
     *
     * @param value the value to inspect
     * @return a text value describing the type
     */
    @Function(docs = """
            ```reflect.typeOf(ANY value)```: Returns a text string describing the type of the value.
            Possible return values are: ```"ARRAY"```, ```"OBJECT"```, ```"STRING"```, ```"NUMBER"```,
            ```"BOOLEAN"```, ```"NULL"```, ```"undefined"```, or ```"ERROR"```.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              reflect.typeOf([1, 2, 3]) == "ARRAY";
              reflect.typeOf({"key": "val"}) == "OBJECT";
              reflect.typeOf("hello") == "STRING";
              reflect.typeOf(42) == "NUMBER";
              reflect.typeOf(true) == "BOOLEAN";
              reflect.typeOf(null) == "NULL";
              reflect.typeOf(undefined) == "undefined";
            ```
            """, schema = RETURNS_STRING)
    public static Val typeOf(Val value) {
        return Val.of(value.getValType());
    }
}
