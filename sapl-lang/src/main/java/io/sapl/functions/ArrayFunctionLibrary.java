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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.BiPredicate;

@UtilityClass
@FunctionLibrary(name = ArrayFunctionLibrary.NAME, description = ArrayFunctionLibrary.DESCRIPTION)
public class ArrayFunctionLibrary {

    public static final String NAME        = "array";
    public static final String DESCRIPTION = "A collection functions for array manipulation.";

    private static final String RETURNS_ARRAY = """
            {
                "type": "array"
            }
            """;

    @Function(docs = """
            ```concatenate(ARRAY...arrays)```: Creates a new array concatenating the all array parameters in ```...arrays```.
            It keeps the order of array parameters and the inner order of the arrays as provided.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.concatenate([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 3, 4, 3, 4, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val concatenate(@Array Val... arrays) {
        final var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            final var jsonArray        = array.getArrayNode();
            final var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                newArray.add(elementsIterator.next().deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```difference(ARRAY array1, ARRAY array2)```: Returns the set difference between the ```array1``` and ```array2```,
            removing duplicates. It creates a new array that has the same elements as array1 except those that are also elements of array2.
            *Attention*: numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.difference([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val difference(@Array Val array1, @Array Val array2) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array1.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (!contains(nextElement, array2.getArrayNode(), Object::equals)
                    && !contains(nextElement, newArray, Object::equals)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```union(ARRAY...arrays)```: Creates a new array with copies of all the array parameters in ```...arrays``` except the duplicate elements.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.union([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 3, 4, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val union(@Array Val... arrays) {
        final var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            final var jsonArray        = array.getArrayNode();
            final var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                final var nextElement = elementsIterator.next();
                if (!contains(nextElement, newArray, Object::equals)) {
                    newArray.add(nextElement.deepCopy());
                }
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```toSet(ARRAY array)```: Creates a copy of the ```array``` preserving the original order, but removing all
            duplicate elements.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` versus ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.toSet([1, 2, 3, 4, 3, 2, 1]) == [1, 2, 3, 4];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val toSet(@Array Val array) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (!contains(nextElement, newArray, Object::equals)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```intersect(ARRAY...arrays)```: Creates a new array only containing elements present in all
            parameter arrays from ```...arrays```, while removing all duplicate elements.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.intersect([1, 2, 3, 4], [3, 4, 5, 6]) == [3, 4];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val intersect(@Array Val... arrays) {
        return intersect(arrays, Object::equals);
    }

    private static Val intersect(Val[] arrays, BiPredicate<JsonNode, JsonNode> equalityValidator) {
        if (arrays.length == 0) {
            return Val.ofEmptyArray();
        }

        var intersection = Val.of(arrays[0].getArrayNode().deepCopy());
        for (var i = 1; i < arrays.length; i++) {
            intersection = intersect(intersection, arrays[i], equalityValidator);
        }
        return intersection;
    }

    private static Val intersect(Val array1, Val array2, BiPredicate<JsonNode, JsonNode> equalityValidator) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array1.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (contains(nextElement, array2.getArrayNode(), equalityValidator)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    private static boolean contains(JsonNode element, ArrayNode array,
            BiPredicate<JsonNode, JsonNode> equalityValidator) {
        final var elementsIterator = array.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (equalityValidator.test(element, nextElement)) {
                return true;
            }
        }
        return false;
    }

    @Function(docs = """
            ```flatten(ARRAY array)```: Flattens a nested array structure into one level.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.flatten([1, [2, 3] , 4, [3, 2], 1]) == [1, 2, 3, 4, 3, 2, 1];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val flatten(@Array Val array) {
        val newArray         = Val.JSON.arrayNode();
        val jsonArray        = array.getArrayNode();
        val elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (nextElement.isArray()) {
                val innerIterator = nextElement.elements();
                while (innerIterator.hasNext()) {
                    newArray.add(innerIterator.next().deepCopy());
                }
            } else {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```size(ARRAY value)```: Returns the number of elements in the array.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.size([1, 2, 3, 4]) == 4;
            ```
            """, schema = """
            {
              "type": "integer"
            }""")
    public static Val size(@Array Val value) {
        return Val.of(value.get().size());
    }

    @Function(docs = """
            ```reverse(ARRAY array)```: Returns the array with its elements in reversed order.

            **Example:**
            ```
            policy "example"
            permit
            where
              array.reverse([1, 2, 3, 4]) == [4, 3, 2, 1];
            ```
            """, schema = """
            {
              "type": "array"
            }""")
    public static Val reverse(@Array Val array) {
        val newArray  = Val.JSON.arrayNode();
        val jsonArray = array.getArrayNode();
        val size      = jsonArray.size();
        for (int i = size - 1; i >= 0; i--) {
            newArray.add(jsonArray.get(i).deepCopy());
        }
        return Val.of(newArray);
    }

}
