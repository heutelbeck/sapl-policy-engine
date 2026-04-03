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
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Functions for JSON object manipulation and inspection.
 */
@UtilityClass
@FunctionLibrary(name = ObjectFunctionLibrary.NAME, description = ObjectFunctionLibrary.DESCRIPTION, libraryDocumentation = ObjectFunctionLibrary.DOCUMENTATION)
public class ObjectFunctionLibrary {

    public static final String NAME          = "object";
    public static final String DESCRIPTION   = "Functions for JSON object manipulation and inspection.";
    public static final String DOCUMENTATION = """
            # Object Function Library (name: object)

            This library provides basic functions for inspecting JSON objects in authorization policies.
            Use these functions to extract keys and values, check object size.
            """;

    private static final String SCHEMA_RETURNS_ARRAY = """
            {
                "type": "array"
            }
            """;

    /**
     * Extracts all keys from a JSON object and returns them as an array of strings.
     * The order of keys matches the
     * iteration order of the object.
     *
     * @param object
     * the JSON object to extract keys from
     *
     * @return an ArrayValue containing all keys as TextValues
     */
    @Function(docs = """
            ```object.keys(OBJECT object)```: Returns an array containing all the keys of the given object.

            ## Parameters

            - object: JSON object to extract keys from

            ## Returns

            - Array of strings representing all keys in the object
            - Empty array for empty objects

            ## Example

            ```sapl
            policy "example"
            permit
              var user = {"name": "Alice", "role": "admin", "active": true};
              var fields = object.keys(user);
              // Returns ["name", "role", "active"]

              object.keys({}) == [];
            ```

            Check for admin permissions:

            ```sapl
            policy "check-admin-access"
            permit
              var permissions = object.keys(subject.permissions);
              "admin:write" in permissions;
            ```
            """, schema = SCHEMA_RETURNS_ARRAY)
    public static Value keys(ObjectValue object) {
        val resultBuilder = ArrayValue.builder();
        for (val key : object.keySet()) {
            resultBuilder.add(Value.of(key));
        }
        return resultBuilder.build();
    }

    /**
     * Extracts all values from a JSON object and returns them as an array. The
     * order of values matches the iteration
     * order of the object.
     *
     * @param object
     * the JSON object to extract values from
     *
     * @return an ArrayValue containing all values
     */
    @Function(docs = """
            ```object.values(OBJECT object)```: Returns an array containing all the values of the given object.

            ## Parameters

            - object: JSON object to extract values from

            ## Returns

            - Array containing all values from the object
            - Empty array for empty objects

            ## Example

            ```sapl
            policy "example"
            permit
              var user = {"name": "Alice", "role": "admin", "active": true};
              var data = object.values(user);
              // Returns ["Alice", "admin", true]

              object.values({}) == [];
            ```
            """, schema = SCHEMA_RETURNS_ARRAY)
    public static Value values(ObjectValue object) {
        val resultBuilder = ArrayValue.builder();
        for (val value : object.values()) {
            resultBuilder.add(value);
        }
        return resultBuilder.build();
    }

}
