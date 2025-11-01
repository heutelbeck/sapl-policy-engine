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
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
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
            Use these functions to extract keys and values, check object size, verify key existence,
            and test for empty objects.
            """;

    private static final String RETURNS_ARRAY = """
            {
                "type": "array"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_INTEGER = """
            {
                "type": "integer"
            }
            """;

    /**
     * Extracts all keys from a JSON object and returns them as an array of strings.
     * The order of keys matches the iteration order of the object.
     *
     * @param object the JSON object to extract keys from
     * @return a Val containing an array of all keys as text values
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
            where
              var user = {"name": "Alice", "role": "admin", "active": true};
              var fields = object.keys(user);
              // Returns ["name", "role", "active"]

              object.keys({}) == [];
            ```

            Check for admin permissions:

            ```sapl
            policy "check-admin-access"
            permit
            where
              var permissions = object.keys(subject.permissions);
              "admin:write" in permissions;
            ```
            """, schema = RETURNS_ARRAY)
    public static Val keys(@JsonObject Val object) {
        val objectNode = object.getObjectNode();
        val keysArray  = Val.JSON.arrayNode();

        val fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            keysArray.add(fieldNames.next());
        }

        return Val.of(keysArray);
    }

    /**
     * Extracts all values from a JSON object and returns them as an array.
     * The order of values matches the iteration order of the object.
     *
     * @param object the JSON object to extract values from
     * @return a Val containing an array of all values
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
            where
              var user = {"name": "Alice", "role": "admin", "active": true};
              var data = object.values(user);
              // Returns ["Alice", "admin", true]

              object.values({}) == [];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val values(@JsonObject Val object) {
        val objectNode  = object.getObjectNode();
        val valuesArray = Val.JSON.arrayNode();

        val fieldValues = objectNode.elements();
        while (fieldValues.hasNext()) {
            valuesArray.add(fieldValues.next().deepCopy());
        }

        return Val.of(valuesArray);
    }

    /**
     * Returns the number of key-value pairs in the object.
     *
     * @param object the JSON object to count properties of
     * @return a Val containing the count as an integer
     */
    @Function(docs = """
            ```object.size(OBJECT object)```: Returns the number of key-value pairs in the given object.

            ## Parameters

            - object: JSON object to measure

            ## Returns

            - Integer representing the number of properties in the object

            ## Example

            ```sapl
            policy "example"
            permit
            where
              var user = {"name": "Alice", "role": "admin", "active": true};
              object.size(user) == 3;

              object.size({}) == 0;
            ```
            """, schema = RETURNS_INTEGER)
    public static Val size(@JsonObject Val object) {
        return Val.of(object.getObjectNode().size());
    }

    /**
     * Checks whether the object contains a specific key.
     *
     * @param object the JSON object to check
     * @param key the key name to look for
     * @return Val.TRUE if the key exists, Val.FALSE otherwise
     */
    @Function(docs = """
            ```object.hasKey(OBJECT object, TEXT key)```: Returns true if the object contains the specified key,
            false otherwise. Checks for key existence regardless of the associated value.

            ## Parameters

            - object: JSON object to search
            - key: String key name to check for

            ## Returns

            - true if the key exists in the object
            - false if the key does not exist

            ## Alternative approach

            Key existence can also be checked using: ```object["key"] != undefined```

            However, hasKey provides better readability and makes intent explicit.

            ## Example

            ```sapl
            policy "example"
            permit
            where
              var user = {"name": "Alice", "role": "admin", "active": null};

              object.hasKey(user, "name");    // true
              object.hasKey(user, "role");    // true
              object.hasKey(user, "active");  // true, even though value is null
              object.hasKey(user, "email");   // false
            ```

            Check optional attributes before using them:

            ```sapl
            policy "check-optional-field"
            permit
            where
              object.hasKey(subject, "department");
              subject.department == "sales";
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val hasKey(@JsonObject Val object, @Text Val key) {
        val objectNode = object.getObjectNode();
        val keyText    = key.getText();
        return Val.of(objectNode.has(keyText));
    }

    /**
     * Checks whether the object is empty (has no properties).
     *
     * @param object the JSON object to check
     * @return Val.TRUE if the object is empty, Val.FALSE otherwise
     */
    @Function(docs = """
            ```object.isEmpty(OBJECT object)```: Returns true if the object is empty (has no key-value pairs),
            false otherwise.

            ## Parameters

            - object: JSON object to check

            ## Returns

            - true if the object has zero properties
            - false if the object has one or more properties

            ## Example

            ```sapl
            policy "example"
            permit
            where
              object.isEmpty({});                          // true
              object.isEmpty({"name": "Alice"});           // false
              object.isEmpty({"a": 1, "b": 2});            // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isEmpty(@JsonObject Val object) {
        return Val.of(object.getObjectNode().isEmpty());
    }
}
