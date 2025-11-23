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

/**
 * Functions for runtime type inspection and reflection on Value objects.
 */
@UtilityClass
@FunctionLibrary(name = ReflectionFunctionLibrary.NAME, description = ReflectionFunctionLibrary.DESCRIPTION)
public class ReflectionFunctionLibrary {

    public static final String NAME        = "reflect";
    public static final String DESCRIPTION = "Functions for runtime type inspection and reflection.";

    private static final String SCHEMA_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String SCHEMA_STRING = """
            {
                "type": "string"
            }
            """;

    /**
     * Checks if the value is a JSON array.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is an array, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isArray(ANY value)```: Returns ```true``` if the value is a JSON array, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "validate_permissions_array"
            permit
            where
              var permissions = subject.permissions;
              reflect.isArray(permissions);      // true if permissions is an array
              reflect.isArray([]);               // true (empty array)
              reflect.isArray(["read", "write"]);// true
              reflect.isArray({"role": "admin"});// false (object)
              reflect.isArray(undefined);        // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isArray(Value value) {
        return Value.of(value instanceof ArrayValue);
    }

    /**
     * Checks if the value is a JSON object.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is an object, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isObject(ANY value)```: Returns ```true``` if the value is a JSON object, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "validate_user_object"
            permit
            where
              var user = resource.owner;
              reflect.isObject(user);              // true if user is an object
              reflect.isObject({});                // true
              reflect.isObject(["admin", "user"]); // false
              reflect.isObject(null);              // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isObject(Value value) {
        return Value.of(value instanceof ObjectValue);
    }

    /**
     * Checks if the value is a text string.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is textual, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isText(ANY value)```: Returns ```true``` if the value is a text string, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "validate_username"
            permit
            where
              var username = subject.username;
              reflect.isText(username);          // true if username is a string
              reflect.isText("");                // true
              reflect.isText(123);               // false
              reflect.isText(undefined);         // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isText(Value value) {
        return Value.of(value instanceof TextValue);
    }

    /**
     * Checks if the value is a number.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is a number, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isNumber(ANY value)```: Returns ```true``` if the value is a number, ```false``` otherwise.
            All numbers are stored as arbitrary-precision decimals internally.

            **Example:**
            ```sapl
            policy "validate_age_threshold"
            permit
            where
              var ageLimit = resource.minimumAge;
              reflect.isNumber(ageLimit);        // true if numeric
              reflect.isNumber(18);              // true
              reflect.isNumber(99.5);            // true
              reflect.isNumber("18");            // false (text, not number)
              reflect.isNumber(null);            // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isNumber(Value value) {
        return Value.of(value instanceof NumberValue);
    }

    /**
     * Checks if the value is a boolean.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is a boolean, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isBoolean(ANY value)```: Returns ```true``` if the value is a boolean (```true``` or ```false```),
            ```false``` otherwise.

            **Example:**
            ```sapl
            policy "validate_flag"
            permit
            where
              var isActive = subject.isActive;
              reflect.isBoolean(isActive);       // true if isActive is boolean
              reflect.isBoolean(true);           // true
              reflect.isBoolean(false);          // true
              reflect.isBoolean(1);              // false
              reflect.isBoolean("true");         // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isBoolean(Value value) {
        return Value.of(value instanceof BooleanValue);
    }

    /**
     * Checks if the value is JSON null.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is null, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isNull(ANY value)```: Returns ```true``` if the value is JSON ```null```, ```false``` otherwise.
            This is distinct from ```undefined```.

            **Example:**
            ```sapl
            policy "check_optional_field"
            permit
            where
              var department = subject.department;
              reflect.isNull(department);        // true if explicitly set to null
              reflect.isNull(null);              // true
              reflect.isNull(undefined);         // false
              reflect.isNull("");                // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isNull(Value value) {
        return Value.of(value instanceof NullValue);
    }

    /**
     * Checks if the value is undefined.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is undefined, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isUndefined(ANY value)```: Returns ```true``` if the value is ```undefined```, ```false``` otherwise.
            This is distinct from ```null``` or an error.

            **Example:**
            ```sapl
            policy "check_missing_attribute"
            permit
            where
              var attribute = subject.optionalAttr;
              reflect.isUndefined(attribute);    // true if attribute not present
              reflect.isUndefined(undefined);    // true
              reflect.isUndefined(null);         // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isUndefined(Value value) {
        return Value.of(value instanceof UndefinedValue);
    }

    /**
     * Checks if the value is defined (not undefined and not an error).
     *
     * @param value the value to check
     * @return Value.TRUE if the value is defined, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isDefined(ANY value)```: Returns ```true``` if the value is defined (not ```undefined``` and not an error),
            ```false``` otherwise. ```null``` is considered defined.

            **Example:**
            ```sapl
            policy "require_attribute"
            permit
            where
              var role = subject.role;
              reflect.isDefined(role);           // true if role exists (even if null)
              reflect.isDefined(null);           // true
              reflect.isDefined(undefined);      // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isDefined(Value value) {
        return Value.of(!(value instanceof UndefinedValue) && !(value instanceof ErrorValue));
    }

    /**
     * Checks if the value is an error.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is an error, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isError(ANY value)```: Returns ```true``` if the value represents an error, ```false``` otherwise.

            **Example:**
            ```sapl
            policy "handle_computation_errors"
            permit
            where
              var result = resource.computedValue;
              !reflect.isError(result);          // deny if computation failed
              reflect.isError(10 / 0);           // true (division by zero)
              reflect.isError(100);              // false
              reflect.isError(undefined);        // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isError(Value value) {
        return Value.of(value instanceof ErrorValue);
    }

    /**
     * Checks if the value is marked as secret.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is marked as secret, Value.FALSE otherwise
     */
    @Function(docs = """
            ```reflect.isSecret(ANY value)```: Returns ```true``` if the value is marked as secret, ```false``` otherwise.
            Secret values are redacted in traces and logs for security purposes.

            **Example:**
            ```sapl
            policy "protect_sensitive_data"
            permit
            where
              var password = subject.credentials.password;
              reflect.isSecret(password);        // true if marked secret
              !reflect.isSecret(subject.username); // username not secret
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isSecret(Value value) {
        return Value.of(value.secret());
    }

    /**
     * Checks if an array or object is empty.
     *
     * @param value the value to check
     * @return Value.TRUE if the value is an empty array or object, Value.FALSE
     * otherwise
     */
    @Function(docs = """
            ```reflect.isEmpty(ANY value)```: Returns ```true``` if the value is an empty array or empty object,
            ```false``` otherwise. For non-container types, returns ```false```.

            **Example:**
            ```sapl
            policy "require_permissions"
            permit
            where
              var permissions = subject.permissions;
              !reflect.isEmpty(permissions);     // deny if no permissions
              reflect.isEmpty([]);               // true
              reflect.isEmpty({});               // true
              reflect.isEmpty(["read", "write"]); // false
            ```
            """, schema = SCHEMA_BOOLEAN)
    public static Value isEmpty(Value value) {
        return switch (value) {
        case ArrayValue array   -> Value.of(array.isEmpty());
        case ObjectValue object -> Value.of(object.isEmpty());
        case TextValue text     -> Value.of(text.value().isEmpty());
        case NullValue ignored  -> Value.TRUE;
        case NumberValue number -> Value.of(number.equals(Value.ZERO));
        default                 -> Value.FALSE;
        };
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
            policy "dynamic_type_validation"
            permit
            where
              var data = resource.metadata;
              reflect.typeOf(data.tags) == "ARRAY";
              reflect.typeOf(data) == "OBJECT";
              reflect.typeOf(data.name) == "STRING";
              reflect.typeOf(data.version) == "NUMBER";
              reflect.typeOf(data.enabled) == "BOOLEAN";
            ```
            """, schema = SCHEMA_STRING)
    public static Value typeOf(Value value) {
        return switch (value) {
        case ArrayValue ignored     -> Value.of("ARRAY");
        case ObjectValue ignored    -> Value.of("OBJECT");
        case TextValue ignored      -> Value.of("STRING");
        case NumberValue ignored    -> Value.of("NUMBER");
        case BooleanValue ignored   -> Value.of("BOOLEAN");
        case NullValue ignored      -> Value.of("NULL");
        case UndefinedValue ignored -> Value.of("undefined");
        case ErrorValue ignored     -> Value.of("ERROR");
        };
    }
}
