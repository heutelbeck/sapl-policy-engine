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
import io.sapl.api.validation.Number;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;
    public static final String MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT = "All array elements must be numeric. Found non-numeric element: ";

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
            return sortArray(jsonArray, JsonNode::isNumber, "numeric", Comparator.comparingDouble(JsonNode::asDouble));
        } else if (firstElement.isTextual()) {
            return sortArray(jsonArray, JsonNode::isTextual, "text", Comparator.comparing(JsonNode::asText));
        } else {
            return Val.error("Array elements must be numeric or text. First element is: " + firstElement.getNodeType());
        }
    }

    /**
     * Sorts array elements using provided type predicate and comparator.
     */
    private static Val sortArray(ArrayNode jsonArray, Predicate<JsonNode> typePredicate, String typeName,
                                 Comparator<JsonNode> comparator) {
        final var elements = new ArrayList<JsonNode>();
        final var iterator = jsonArray.elements();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!typePredicate.test(element)) {
                return Val.error("All array elements must be " + typeName + ". Found non-" + typeName + " element: "
                        + element);
            }
            elements.add(element);
        }

        elements.sort(comparator);

        return createArrayFromElements(elements);
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

    @Function(docs = """
            ```head(ARRAY array)```: Returns the first element of the array. Returns an error if the array is empty.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.head([1, 2, 3, 4]) == 1;
              array.head(["apple", "banana"]) == "apple";
            ```
            """)
    public static Val head(@Array Val array) {
        final var jsonArray = array.getArrayNode();
        if (jsonArray.isEmpty()) {
            return Val.error("Cannot get head of an empty array");
        }
        return Val.of(jsonArray.get(0).deepCopy());
    }

    @Function(docs = """
            ```last(ARRAY array)```: Returns the last element of the array. Returns an error if the array is empty.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              array.last([1, 2, 3, 4]) == 4;
              array.last(["apple", "banana"]) == "banana";
            ```
            """)
    public static Val last(@Array Val array) {
        final var jsonArray = array.getArrayNode();
        if (jsonArray.isEmpty()) {
            return Val.error("Cannot get last of an empty array");
        }
        return Val.of(jsonArray.get(jsonArray.size() - 1).deepCopy());
    }

    @Function(docs = """
            ```max(ARRAY array)```: Returns the maximum value from the array. For numeric arrays, returns the largest number.
            For string arrays, returns the last string in lexicographic order.

            **Requirements:**
            - Array must not be empty
            - All elements must be of the same type (all numeric or all text)
            - Returns error if types are mixed or if first element is neither numeric nor text

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.max([3, 1, 4, 1, 5, 9]) == 9;
              array.max(["dog", "cat", "bird"]) == "dog";
            
              // These would return errors:
              // array.max([])              // empty array
              // array.max([1, "two", 3])   // mixed types
            ```
            """, schema = RETURNS_NUMBER)
    public static Val max(@Array Val array) {
        return findExtremum(array, "max", (a, b) -> a > b);
    }

    @Function(docs = """
            ```min(ARRAY array)```: Returns the minimum value from the array. For numeric arrays, returns the smallest number.
            For string arrays, returns the first string in lexicographic order.

            **Requirements:**
            - Array must not be empty
            - All elements must be of the same type (all numeric or all text)
            - Returns error if types are mixed or if first element is neither numeric nor text

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.min([3, 1, 4, 1, 5, 9]) == 1;
              array.min(["dog", "cat", "bird"]) == "bird";
            
              // These would return errors:
              // array.min([])              // empty array
              // array.min([1, "two", 3])   // mixed types
            ```
            """, schema = RETURNS_NUMBER)
    public static Val min(@Array Val array) {
        return findExtremum(array, "min", (a, b) -> a < b);
    }

    /**
     * Finds extremum (min or max) in array based on comparison predicate.
     */
    private static Val findExtremum(Val array, String operationName, BiPredicate<Double, Double> numericComparator) {
        final var jsonArray = array.getArrayNode();

        if (jsonArray.isEmpty()) {
            return Val.error("Cannot find " + operationName + " of an empty array");
        }

        final var firstElement = jsonArray.get(0);

        if (firstElement.isNumber()) {
            return findNumericExtremum(jsonArray, numericComparator);
        } else if (firstElement.isTextual()) {
            return findTextualExtremum(jsonArray, numericComparator);
        } else {
            return Val.error("Array elements must be numeric or text. First element is: " + firstElement.getNodeType());
        }
    }

    /**
     * Finds extremum value among numeric elements.
     */
    private static Val findNumericExtremum(ArrayNode jsonArray, BiPredicate<Double, Double> comparator) {
        final var iterator      = jsonArray.elements();
        var       extremumValue = iterator.next().asDouble();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isNumber()) {
                return Val.error(MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT + element);
            }
            final var value = element.asDouble();
            if (comparator.test(value, extremumValue)) {
                extremumValue = value;
            }
        }

        return Val.of(extremumValue);
    }

    /**
     * Finds extremum value among textual elements.
     */
    private static Val findTextualExtremum(ArrayNode jsonArray, BiPredicate<Double, Double> numericComparator) {
        final var iterator      = jsonArray.elements();
        var       extremumValue = iterator.next().asText();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isTextual()) {
                return Val.error("All array elements must be text. Found non-text element: " + element);
            }
            final var text       = element.asText();
            final var comparison = text.compareTo(extremumValue);
            if (numericComparator.test((double) comparison, 0.0)) {
                extremumValue = text;
            }
        }

        return Val.of(extremumValue);
    }

    @Function(docs = """
            ```sum(ARRAY array)```: Returns the sum of all numeric elements in the array. Returns ```0``` for an empty array.

            **Requirements:**
            - All elements must be numeric
            - Returns error if any non-numeric element is encountered

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.sum([1, 2, 3, 4, 5]) == 15;
              array.sum([]) == 0;
              array.sum([-5, 5]) == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val sum(@Array Val array) {
        return aggregateNumericArray(array, 0.0, Double::sum);
    }

    @Function(docs = """
            ```multiply(ARRAY array)```: Returns the product of all numeric elements in the array. Returns ```1``` for an empty array.

            **Requirements:**
            - All elements must be numeric
            - Returns error if any non-numeric element is encountered

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.multiply([2, 3, 4]) == 24;
              array.multiply([]) == 1;
              array.multiply([5, 0]) == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val multiply(@Array Val array) {
        return aggregateNumericArray(array, 1.0, (a, b) -> a * b);
    }

    /**
     * Aggregates numeric array using provided identity and accumulator function.
     */
    private static Val aggregateNumericArray(Val array, double identityValue,
                                             java.util.function.DoubleBinaryOperator accumulator) {
        final var jsonArray = array.getArrayNode();

        if (jsonArray.isEmpty()) {
            return Val.of(identityValue);
        }

        final var iterator = jsonArray.elements();
        var       result   = identityValue;

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isNumber()) {
                return Val.error(MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT + element);
            }
            result = accumulator.applyAsDouble(result, element.asDouble());
        }

        return Val.of(result);
    }

    @Function(docs = """
            ```avg(ARRAY array)```: Returns the arithmetic mean (average) of all numeric elements in the array.

            **Requirements:**
            - Array must not be empty
            - All elements must be numeric
            - Returns error if array is empty or if any non-numeric element is encountered

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.avg([1, 2, 3, 4, 5]) == 3.0;
              array.avg([10, 20]) == 15.0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val avg(@Array Val array) {
        final var jsonArray = array.getArrayNode();

        if (jsonArray.isEmpty()) {
            return Val.error("Cannot calculate average of an empty array");
        }

        final var iterator = jsonArray.elements();
        var       sum      = 0.0;
        var       count    = 0;

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isNumber()) {
                return Val.error(MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT + element);
            }
            sum += element.asDouble();
            count++;
        }

        return Val.of(sum / count);
    }

    @Function(docs = """
            ```median(ARRAY array)```: Returns the median value of all numeric elements in the array. The median is the middle
            value when the numbers are sorted. For arrays with an even number of elements, returns the average of the two
            middle values.

            **Requirements:**
            - Array must not be empty
            - All elements must be numeric
            - Returns error if array is empty or if any non-numeric element is encountered

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.median([1, 2, 3, 4, 5]) == 3;          // odd count: middle value
              array.median([1, 2, 3, 4]) == 2.5;           // even count: average of two middle values
              array.median([5, 1, 3, 2, 4]) == 3;          // unsorted input is sorted first
            ```
            """, schema = RETURNS_NUMBER)
    public static Val median(@Array Val array) {
        final var jsonArray = array.getArrayNode();

        if (jsonArray.isEmpty()) {
            return Val.error("Cannot calculate median of an empty array");
        }

        final var numbers  = new ArrayList<Double>();
        final var iterator = jsonArray.elements();

        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (!element.isNumber()) {
                return Val.error(MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT + element);
            }
            numbers.add(element.asDouble());
        }

        numbers.sort(Double::compareTo);

        final var size = numbers.size();
        if (size % 2 == 1) {
            return Val.of(numbers.get(size / 2));
        } else {
            final var middle1 = numbers.get(size / 2 - 1);
            final var middle2 = numbers.get(size / 2);
            return Val.of((middle1 + middle2) / 2.0);
        }
    }

    @Function(docs = """
        ```range(NUMBER from, NUMBER to)```: Creates an array containing all integers from ```from``` to ```to``` (both inclusive).
        Returns an empty array if the range is invalid (e.g., from > to with implied positive step).

        **Requirements:**
        - Both parameters must be integers
        - Range is inclusive on both ends

        **Examples:**
        ```sapl
        policy "example"
        permit
        where
          array.range(1, 5) == [1, 2, 3, 4, 5];
          array.range(5, 5) == [5];
          array.range(5, 2) == [];  // invalid range returns empty array
        ```
        """, schema = RETURNS_ARRAY)
    public static Val range(@Number Val from, @Number Val to) {
        return range(from, to, Val.of(1));
    }

    @Function(docs = """
        ```range(NUMBER from, NUMBER to, NUMBER step)```: Creates an array containing integers from ```from``` to ```to```
        (both inclusive), incrementing by ```step```. The step can be positive or negative.

        **Requirements:**
        - All parameters must be integers
        - ```step``` must not be zero (returns error)
        - If ```step``` is positive, ```from``` must be less than or equal to ```to```
        - If ```step``` is negative, ```from``` must be greater than or equal to ```to```
        - Range is inclusive on both ends

        **Examples:**
        ```sapl
        policy "example"
        permit
        where
          array.range(1, 10, 2) == [1, 3, 5, 7, 9];
          array.range(10, 1, -2) == [10, 8, 6, 4, 2];
          array.range(5, 2, 1) == [];    // positive step but from > to returns empty
          array.range(1, 5, -1) == [];   // negative step but from < to returns empty
        ```
        """, schema = RETURNS_ARRAY)
    public static Val range(@Number Val from, @Number Val to, @Number Val step) {
        final var fromNode = from.get();
        final var toNode   = to.get();
        final var stepNode = step.get();

        if (!fromNode.isIntegralNumber() || !toNode.isIntegralNumber() || !stepNode.isIntegralNumber()) {
            return Val.error("All parameters must be integers");
        }

        final var fromValue = fromNode.asLong();
        final var toValue   = toNode.asLong();
        final var stepValue = stepNode.asLong();

        if (stepValue == 0) {
            return Val.error("Step must not be zero");
        }

        final var result = Val.JSON.arrayNode();

        if (stepValue > 0) {
            if (fromValue > toValue) {
                return Val.of(result);
            }
            buildRangeWithPositiveStep(result, fromValue, toValue, stepValue);
        } else {
            if (fromValue < toValue) {
                return Val.of(result);
            }
            buildRangeWithNegativeStep(result, fromValue, toValue, stepValue);
        }

        return Val.of(result);
    }

    /**
     * Builds range array with positive step value. Calculates iteration count upfront to ensure
     * explicit loop termination.
     */
    private static void buildRangeWithPositiveStep(ArrayNode result, long fromValue, long toValue, long stepValue) {
        final var totalRange      = toValue - fromValue;
        final var numberOfSteps   = totalRange / stepValue;
        final var iterationCount  = numberOfSteps + 1;

        for (long iteration = 0; iteration < iterationCount; iteration++) {
            final var currentValue = fromValue + (iteration * stepValue);
            addNumberNode(result, currentValue);
        }
    }

    /**
     * Builds range array with negative step value. Calculates iteration count upfront to ensure
     * explicit loop termination.
     */
    private static void buildRangeWithNegativeStep(ArrayNode result, long fromValue, long toValue, long stepValue) {
        final var totalRange      = fromValue - toValue;
        final var stepMagnitude   = -stepValue;
        final var numberOfSteps   = totalRange / stepMagnitude;
        final var iterationCount  = numberOfSteps + 1;

        for (long iteration = 0; iteration < iterationCount; iteration++) {
            final var currentValue = fromValue + (iteration * stepValue);
            addNumberNode(result, currentValue);
        }
    }

    /**
     * Adds number node to result array, using int representation if value fits in int range.
     */
    private static void addNumberNode(ArrayNode result, long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            result.add(Val.JSON.numberNode((int) value));
        } else {
            result.add(Val.JSON.numberNode(value));
        }
    }

    @Function(docs = """
            ```crossProduct(ARRAY array1, ARRAY array2)```: Returns the Cartesian product of two arrays. The result is an array
            of 2-element arrays, where each element contains one item from ```array1``` paired with one item from ```array2```.
            Returns an empty array if either input array is empty.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.crossProduct([1, 2], ["a", "b"]) == [[1, "a"], [1, "b"], [2, "a"], [2, "b"]];
              array.crossProduct([1], ["x", "y", "z"]) == [[1, "x"], [1, "y"], [1, "z"]];
              array.crossProduct([], [1, 2]) == [];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val crossProduct(@Array Val array1, @Array Val array2) {
        final var jsonArray1 = array1.getArrayNode();
        final var jsonArray2 = array2.getArrayNode();

        final var result = Val.JSON.arrayNode();

        if (jsonArray1.isEmpty() || jsonArray2.isEmpty()) {
            return Val.of(result);
        }

        final var iterator1 = jsonArray1.elements();
        while (iterator1.hasNext()) {
            final var element1  = iterator1.next();
            final var iterator2 = jsonArray2.elements();
            while (iterator2.hasNext()) {
                final var element2 = iterator2.next();
                final var pair     = Val.JSON.arrayNode();
                pair.add(element1.deepCopy());
                pair.add(element2.deepCopy());
                result.add(pair);
            }
        }

        return Val.of(result);
    }

    @Function(docs = """
            ```zip(ARRAY array1, ARRAY array2)```: Combines two arrays element-wise into an array of 2-element arrays (pairs).
            The resulting array has length equal to the shorter of the two input arrays.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              array.zip([1, 2, 3], ["a", "b", "c"]) == [[1, "a"], [2, "b"], [3, "c"]];
              array.zip([1, 2, 3, 4], ["a", "b"]) == [[1, "a"], [2, "b"]];
              array.zip([], [1, 2, 3]) == [];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val zip(@Array Val array1, @Array Val array2) {
        final var jsonArray1 = array1.getArrayNode();
        final var jsonArray2 = array2.getArrayNode();

        final var result     = Val.JSON.arrayNode();
        final var minSize    = Math.min(jsonArray1.size(), jsonArray2.size());

        for (int i = 0; i < minSize; i++) {
            final var pair = Val.JSON.arrayNode();
            pair.add(jsonArray1.get(i).deepCopy());
            pair.add(jsonArray2.get(i).deepCopy());
            result.add(pair);
        }

        return Val.of(result);
    }

    /**
     * Creates ArrayNode from list of elements.
     */
    private static Val createArrayFromElements(List<JsonNode> elements) {
        final var resultArray = Val.JSON.arrayNode();
        for (var element : elements) {
            resultArray.add(element.deepCopy());
        }
        return Val.of(resultArray);
    }
}