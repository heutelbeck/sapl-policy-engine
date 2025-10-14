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
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BiPredicate;

/**
 * Collection of functions for array manipulation.
 */
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

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
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
            ```sapl
            policy "example"
            permit
            where
              array.difference([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2];
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
            ```sapl
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
            ```sapl
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
            ```sapl
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

    @Function(docs = """
            ```containsAny(ARRAY array, ARRAY elements)```: Returns ```true``` if the ```array``` contains at least one element
            from the ```elements``` array. Returns ```false``` if no elements are found or if the ```elements``` array is empty.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.containsAny([1, 2, 3, 4], [3, 5, 6]);  // true, because 3 is in both arrays
              array.containsAny([1, 2, 3, 4], [5, 6, 7]);  // false, no common elements
              array.containsAny([1, 2, 3, 4], []);         // false, elements array is empty
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val containsAny(@Array Val array, @Array Val elements) {
        final var elementsArray    = elements.getArrayNode();
        final var elementsIterator = elementsArray.elements();
        while (elementsIterator.hasNext()) {
            final var element = elementsIterator.next();
            if (contains(element, array.getArrayNode(), Object::equals)) {
                return Val.TRUE;
            }
        }
        return Val.FALSE;
    }

    @Function(docs = """
            ```containsAll(ARRAY array, ARRAY elements)```: Returns ```true``` if the ```array``` contains all elements
            from the ```elements``` array. The elements do not need to appear in the same order. Returns ```true``` if the
            ```elements``` array is empty.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.containsAll([1, 2, 3, 4, 5], [3, 1, 5]);  // true, all elements present (order doesn't matter)
              array.containsAll([1, 2, 3, 4], [3, 5, 6]);     // false, 5 and 6 are not in array
              array.containsAll([1, 2, 3, 4], []);            // true, empty elements array
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val containsAll(@Array Val array, @Array Val elements) {
        final var elementsArray    = elements.getArrayNode();
        final var elementsIterator = elementsArray.elements();
        while (elementsIterator.hasNext()) {
            final var element = elementsIterator.next();
            if (!contains(element, array.getArrayNode(), Object::equals)) {
                return Val.FALSE;
            }
        }
        return Val.TRUE;
    }

    @Function(docs = """
            ```containsAllInOrder(ARRAY array, ARRAY elements)```: Returns ```true``` if the ```array``` contains all elements
            from the ```elements``` array in the same sequential order (though not necessarily consecutively). Returns ```true```
            if the ```elements``` array is empty.
            *Attention:* numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-equivalent.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.containsAllInOrder([1, 2, 3, 4, 5], [2, 4, 5]);     // true, elements appear in order
              array.containsAllInOrder([1, 2, 3, 4, 5], [2, 5, 4]);     // false, 5 appears before 4 in array
              array.containsAllInOrder([1, 2, 3, 4, 5], [1, 1, 2]);     // false, only one occurrence of 1
              array.containsAllInOrder([1, 2, 3, 4], []);               // true, empty elements array
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val containsAllInOrder(@Array Val array, @Array Val elements) {
        final var arrayElements    = array.getArrayNode();
        final var requiredElements = elements.getArrayNode();

        if (requiredElements.isEmpty()) {
            return Val.TRUE;
        }

        var arrayIndex           = 0;
        var requiredElementIndex = 0;

        while (arrayIndex < arrayElements.size() && requiredElementIndex < requiredElements.size()) {
            if (arrayElements.get(arrayIndex).equals(requiredElements.get(requiredElementIndex))) {
                requiredElementIndex++;
            }
            arrayIndex++;
        }

        return Val.of(requiredElementIndex == requiredElements.size());
    }

    @Function(docs = """
            ```sort(ARRAY array)```: Returns a new array with elements sorted in ascending order. The function determines
            the sort order based on the type of the first element:

            - **Numeric arrays**: Sorted numerically (e.g., ```[1, 2, 10, 20]```)
            - **String arrays**: Sorted lexicographically (e.g., ```["a", "b", "c"]```)

            **Requirements:**
            - All elements must be of the same type (all numeric or all text)
            - Array must not be empty
            - If elements have mixed types or the first element is neither numeric nor text, an error is returned

            **Implementation Note:**
            Numeric sorting uses floating-point comparison (```double```) for performance, which is appropriate for SAPL's
            authorization policy use cases. This approach is consistent with other array functions and prioritizes fast
            policy evaluation. Very large integers beyond 2^53 may lose precision during comparison.

            *Attention:* numerically equivalent but differently written numbers, i.e., ```0``` vs ```0.000```, may be
            treated as distinct values during sorting.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.sort([3, 1, 4, 1, 5, 9, 2, 6]) == [1, 1, 2, 3, 4, 5, 6, 9];  // numeric sort
              array.sort(["dog", "cat", "bird", "ant"]) == ["ant", "bird", "cat", "dog"];  // string sort

              // These would return errors:
              // array.sort([1, "two", 3])  // mixed types
              // array.sort([])              // empty array
              // array.sort([true, false])   // unsupported type
            ```
            """, schema = RETURNS_ARRAY)
    public static Val sort(@Array Val array) {
        final var jsonArray = array.getArrayNode();

        if (jsonArray.isEmpty()) {
            return Val.error("Cannot sort an empty array");
        }

        final var firstElement = jsonArray.get(0);

        if (firstElement.isNumber()) {
            return sortNumeric(jsonArray);
        } else if (firstElement.isTextual()) {
            return sortTextual(jsonArray);
        } else {
            return Val.error("Array elements must be numeric or text. First element is: " + firstElement.getNodeType());
        }
    }

    /**
     * Sorts numeric array elements.
     */
    private static Val sortNumeric(ArrayNode jsonArray) {
        final var elements = new ArrayList<JsonNode>();
        final var iterator = jsonArray.elements();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isNumber()) {
                return Val.error("All array elements must be numeric. Found non-numeric element: " + element);
            }
            elements.add(element);
        }

        elements.sort(Comparator.comparingDouble(JsonNode::asDouble));

        final var sortedArray = Val.JSON.arrayNode();
        for (var element : elements) {
            sortedArray.add(element.deepCopy());
        }

        return Val.of(sortedArray);
    }

    /**
     * Sorts textual array elements.
     */
    private static Val sortTextual(ArrayNode jsonArray) {
        final var elements = new ArrayList<JsonNode>();
        final var iterator = jsonArray.elements();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isTextual()) {
                return Val.error("All array elements must be text. Found non-text element: " + element);
            }
            elements.add(element);
        }

        elements.sort(Comparator.comparing(JsonNode::asText));

        final var sortedArray = Val.JSON.arrayNode();
        for (var element : elements) {
            sortedArray.add(element.deepCopy());
        }

        return Val.of(sortedArray);
    }

    /**
     * Checks if element is contained in array using equality validator.
     */
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
            ```sapl
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
            ```sapl
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
            ```sapl
            policy "example"
            permit
            where
              array.reverse([1, 2, 3, 4]) == [4, 3, 2, 1];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val reverse(@Array Val array) {
        val newArray  = Val.JSON.arrayNode();
        val jsonArray = array.getArrayNode();
        val size      = jsonArray.size();
        for (int i = size - 1; i >= 0; i--) {
            newArray.add(jsonArray.get(i).deepCopy());
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```isSet(ARRAY array)```: Returns ```true``` if the array contains only distinct elements (no duplicates),
            ```false``` otherwise. An empty array is considered a set.
            *Attention:* numerically equivalent but differently written numbers, i.e., ```0``` vs ```0.000```, may be
            interpreted as non-equivalent.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.isSet([1, 2, 3, 4]);           // true, all elements are unique
              array.isSet([1, 2, 3, 2]);           // false, 2 appears twice
              array.isSet([]);                     // true, empty array is a set
              array.isSet([1, "1", 2]);            // true, 1 and "1" are different types
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isSet(@Array Val array) {
        final var jsonArray        = array.getArrayNode();
        final var seen             = Val.JSON.arrayNode();
        final var elementsIterator = jsonArray.elements();

        while (elementsIterator.hasNext()) {
            final var element = elementsIterator.next();
            if (contains(element, seen, Object::equals)) {
                return Val.FALSE;
            }
            seen.add(element.deepCopy());
        }
        return Val.TRUE;
    }

    @Function(docs = """
            ```isEmpty(ARRAY array)```: Returns ```true``` if the array is empty (has no elements), ```false``` otherwise.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.isEmpty([]);           // true
              array.isEmpty([1]);          // false
              array.isEmpty([1, 2, 3]);    // false
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isEmpty(@Array Val array) {
        return Val.of(array.getArrayNode().isEmpty());
    }
}
