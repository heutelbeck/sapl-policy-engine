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
 * Array manipulation functions for authorization policies.
 */
@UtilityClass
@FunctionLibrary(name = ArrayFunctionLibrary.NAME, description = ArrayFunctionLibrary.DESCRIPTION, libraryDocumentation = ArrayFunctionLibrary.DOCUMENTATION)
public class ArrayFunctionLibrary {

    public static final String NAME          = "array";
    public static final String DESCRIPTION   = "Array manipulation functions for authorization policies.";
    public static final String DOCUMENTATION = """
            # Array Functions

            Array operations for building authorization policies that work with collections of values.
            Test membership, combine sets of permissions, aggregate numeric data, and transform
            attribute lists.

            ## Core Principles

            Array functions treat inputs as immutable collections and return new arrays. Equality
            comparison uses JSON value equality - numerically equivalent but differently formatted
            numbers (e.g., 0 versus 0.000) may not match. Empty arrays are valid inputs and follow
            mathematical conventions: empty union returns empty, empty intersection returns empty,
            sum of empty returns 0, product of empty returns 1.

            ## Access Control Patterns

            Check if a user possesses required permissions from a set. Verify that subjects hold
            all mandatory roles before granting access.

            ```sapl
            policy "require_admin_or_editor"
            permit action == "modify_content"
            where
                var required = ["admin", "editor"];
                array.containsAny(subject.roles, required);
            ```

            Combine permissions from multiple sources when evaluating group memberships or
            inherited roles.

            ```sapl
            policy "aggregate_permissions"
            permit
            where
                var direct = subject.directPermissions;
                var inherited = subject.groupPermissions;
                var all = array.union(direct, inherited);
                array.containsAll(all, ["read", "write"]);
            ```

            Find common capabilities between user privileges and resource requirements to
            determine allowed operations.

            ```sapl
            policy "intersection_access"
            permit
            where
                var allowed = array.intersect(subject.capabilities, resource.requirements);
                !array.isEmpty(allowed);
            obligation
                {
                    "type": "limit_operations",
                    "operations": allowed
                }
            ```

            Validate approval workflows by checking that signatories appear in the correct
            sequence. Enforce that approvals happen in order without gaps.

            ```sapl
            policy "approval_sequence"
            permit action == "finalize_transaction"
            where
                var required = ["manager", "director", "cfo"];
                array.containsAllInOrder(resource.approvals, required);
            ```

            Filter sensitive attributes before releasing data. Remove fields that exceed the
            user's clearance level.

            ```sapl
            policy "filter_classified"
            permit action == "read_document"
            where
                subject.clearance >= resource.classification;
            transform
                var allowed = subject.viewableFields;
                var actual = resource.fieldNames;
                var permitted = array.intersect(allowed, actual);
                resource |- { @.fields : permitted }
            ```

            Calculate aggregate metrics for rate limiting or quota enforcement. Sum request
            counts or average response times across time windows.

            ```sapl
            policy "rate_limit"
            deny action == "api_call"
            where
                var counts = subject.requestsPerMinute;
                array.sum(counts) > 100;
            ```
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

    private static final String RETURNS_NUMBER                            = """
            {
                "type": "number"
            }
            """;
    public static final String  MUST_BE_NUMERIC_FOUND_NON_NUMERIC_ELEMENT = "All array elements must be numeric. Found non-numeric element: ";

    @Function(docs = """
            ```array.concatenate(ARRAY...arrays)```

            Creates a new array by appending all parameter arrays in order. Preserves element
            order within each array and the order of array parameters. Duplicates are retained.

            Parameters:
            - arrays: Arrays to concatenate

            Returns: New array containing all elements in order

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.concatenate([1, 2], [3, 4], [5]) == [1, 2, 3, 4, 5];
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
            ```array.difference(ARRAY array1, ARRAY array2)```

            Returns the set difference between array1 and array2, removing duplicates. Creates a
            new array containing elements from array1 that do not appear in array2.

            Parameters:
            - array1: Array to subtract from
            - array2: Array of elements to remove

            Returns: New array with elements in array1 but not in array2

            Example - remove revoked permissions:
            ```sapl
            policy "example"
            permit
            where
                var granted = subject.grantedPermissions;
                var revoked = subject.revokedPermissions;
                var effective = array.difference(granted, revoked);
                array.containsAll(effective, resource.requiredPermissions);
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
            ```array.union(ARRAY...arrays)```

            Creates a new array containing all unique elements from all parameter arrays.
            Removes duplicates while preserving the first occurrence of each element.

            Parameters:
            - arrays: Arrays to combine

            Returns: New array with all unique elements

            Example - combine permissions from multiple sources:
            ```sapl
            policy "example"
            permit
            where
                var all = array.union(subject.directPermissions, subject.groupPermissions);
                array.containsAll(all, ["read", "write"]);
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
            ```array.toSet(ARRAY array)```

            Creates a copy of the array preserving the original order but removing all
            duplicate elements. Keeps the first occurrence of each element.

            Parameters:
            - array: Array to deduplicate

            Returns: New array with unique elements

            Example:
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
            ```array.intersect(ARRAY...arrays)```

            Creates a new array containing only elements present in all parameter arrays.
            Removes duplicates from the result.

            Parameters:
            - arrays: Arrays to intersect

            Returns: New array with common elements

            Example - find permissions shared across all roles:
            ```sapl
            policy "example"
            permit
            where
                var rolePerms = subject.roles.map(role -> role.permissions);
                var common = array.intersect(rolePerms);
                array.containsAll(common, resource.minimumPermissions);
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
            ```array.containsAny(ARRAY array, ARRAY elements)```

            Returns true if the array contains at least one element from the elements array.
            Returns false if no elements are found or if the elements array is empty.

            Parameters:
            - array: Array to search in
            - elements: Elements to search for

            Returns: Boolean indicating whether any element was found

            Example - check if user has any admin role:
            ```sapl
            policy "example"
            permit action == "admin_panel"
            where
                var adminRoles = ["superadmin", "admin", "moderator"];
                array.containsAny(subject.roles, adminRoles);
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
            ```array.containsAll(ARRAY array, ARRAY elements)```

            Returns true if the array contains all elements from the elements array. The elements
            do not need to appear in the same order. Returns true if the elements array is empty.

            Parameters:
            - array: Array to search in
            - elements: Elements that must all be present

            Returns: Boolean indicating whether all elements were found

            Example - verify user has all required permissions:
            ```sapl
            policy "example"
            permit action == "publish_article"
            where
                var required = ["write", "publish", "notify"];
                array.containsAll(subject.permissions, required);
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
            ```array.containsAllInOrder(ARRAY array, ARRAY elements)```

            Returns true if the array contains all elements from the elements array in the same
            sequential order, though not necessarily consecutively. Returns true if the elements
            array is empty.

            Parameters:
            - array: Array to search in
            - elements: Elements that must appear in this order

            Returns: Boolean indicating whether elements appear in order

            Example - verify approval workflow sequence:
            ```sapl
            policy "example"
            permit action == "finalize_contract"
            where
                var required = ["legal_review", "manager_approval", "director_signature"];
                array.containsAllInOrder(resource.approvalSteps, required);
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
            ```array.sort(ARRAY array)```

            Returns a new array with elements sorted in ascending order. The function determines
            the sort order based on the type of the first element. Numeric arrays are sorted
            numerically, string arrays are sorted lexicographically. All elements must be of the
            same type. Returns an error for empty arrays, mixed types, or unsupported types.

            Numeric sorting uses floating-point comparison for performance, which is appropriate
            for SAPL's authorization policy use cases. Very large integers beyond 2^53 may lose
            precision during comparison.

            Parameters:
            - array: Array to sort

            Returns: New sorted array

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.sort([3, 1, 4, 1, 5, 9, 2, 6]) == [1, 1, 2, 3, 4, 5, 6, 9];
                array.sort(["dog", "cat", "bird", "ant"]) == ["ant", "bird", "cat", "dog"];
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
                return Val.error(
                        "All array elements must be " + typeName + ". Found non-" + typeName + " element: " + element);
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
            ```array.flatten(ARRAY array)```

            Flattens a nested array structure by one level. Takes an array that may contain
            other arrays and returns a new array with all nested arrays expanded into the
            top level.

            Parameters:
            - array: Array to flatten

            Returns: New flattened array

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.flatten([1, [2, 3], 4, [5]]) == [1, 2, 3, 4, 5];
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
            ```array.size(ARRAY value)```

            Returns the number of elements in the array.

            Parameters:
            - value: Array to measure

            Returns: Integer count of elements

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.size(subject.roles) >= 2;
            ```
            """, schema = """
            {
              "type": "integer"
            }""")
    public static Val size(@Array Val value) {
        return Val.of(value.get().size());
    }

    @Function(docs = """
            ```array.reverse(ARRAY array)```

            Returns the array with its elements in reversed order.

            Parameters:
            - array: Array to reverse

            Returns: New array with elements in reverse order

            Example:
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
            ```array.isSet(ARRAY array)```

            Returns true if the array contains only distinct elements with no duplicates.
            An empty array is considered a set.

            Parameters:
            - array: Array to test

            Returns: Boolean indicating whether array is a set

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.isSet([1, 2, 3, 4]);
                !array.isSet([1, 2, 3, 2]);
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
            ```array.isEmpty(ARRAY array)```

            Returns true if the array has no elements.

            Parameters:
            - array: Array to test

            Returns: Boolean indicating whether array is empty

            Example:
            ```sapl
            policy "example"
            permit
            where
                !array.isEmpty(subject.permissions);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isEmpty(@Array Val array) {
        return Val.of(array.getArrayNode().isEmpty());
    }

    @Function(docs = """
            ```array.head(ARRAY array)```

            Returns the first element of the array. Returns an error if the array is empty.

            Parameters:
            - array: Array to extract from

            Returns: First element

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.head(subject.roles) == "admin";
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
            ```array.last(ARRAY array)```

            Returns the last element of the array. Returns an error if the array is empty.

            Parameters:
            - array: Array to extract from

            Returns: Last element

            Example:
            ```sapl
            policy "example"
            permit action == "finalize"
            where
                array.last(resource.approvals) == "cfo_signature";
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
            ```array.max(ARRAY array)```

            Returns the maximum value from the array. For numeric arrays, returns the largest
            number. For string arrays, returns the last string in lexicographic order. All
            elements must be of the same type. Returns an error for empty arrays, mixed types,
            or unsupported types.

            Parameters:
            - array: Array to find maximum in

            Returns: Maximum value

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.max([3, 1, 4, 1, 5, 9]) == 9;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val max(@Array Val array) {
        return findExtremum(array, "max", (a, b) -> a > b);
    }

    @Function(docs = """
            ```array.min(ARRAY array)```

            Returns the minimum value from the array. For numeric arrays, returns the smallest
            number. For string arrays, returns the first string in lexicographic order. All
            elements must be of the same type. Returns an error for empty arrays, mixed types,
            or unsupported types.

            Parameters:
            - array: Array to find minimum in

            Returns: Minimum value

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.min(subject.securityLevels) >= 3;
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
            ```array.sum(ARRAY array)```

            Returns the sum of all numeric elements in the array. Returns 0 for an empty array.
            All elements must be numeric or an error is returned.

            Parameters:
            - array: Array of numbers to sum

            Returns: Sum of all elements

            Example - enforce rate limit:
            ```sapl
            policy "example"
            deny action == "api_call"
            where
                array.sum(subject.requestCounts) > 1000;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val sum(@Array Val array) {
        return aggregateNumericArray(array, 0.0, Double::sum);
    }

    @Function(docs = """
            ```array.multiply(ARRAY array)```

            Returns the product of all numeric elements in the array. Returns 1 for an empty
            array. All elements must be numeric or an error is returned.

            Parameters:
            - array: Array of numbers to multiply

            Returns: Product of all elements

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.multiply([2, 3, 4]) == 24;
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
            ```array.avg(ARRAY array)```

            Returns the arithmetic mean (average) of all numeric elements in the array. Returns
            an error for empty arrays. All elements must be numeric.

            Parameters:
            - array: Array of numbers to average

            Returns: Average value

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.avg(subject.performanceScores) >= 8.0;
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
            ```array.median(ARRAY array)```

            Returns the median value of all numeric elements in the array. The median is the
            middle value when the numbers are sorted. For arrays with an even number of elements,
            returns the average of the two middle values. Returns an error for empty arrays.
            All elements must be numeric.

            Parameters:
            - array: Array of numbers to find median of

            Returns: Median value

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.median([1, 2, 3, 4, 5]) == 3;
                array.median([1, 2, 3, 4]) == 2.5;
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
            ```array.range(NUMBER from, NUMBER to)```

            Creates an array containing all integers from from to to (both inclusive).
            Returns an empty array if the range is invalid (from greater than to).
            Both parameters must be integers.

            Parameters:
            - from: Starting value (inclusive)
            - to: Ending value (inclusive)

            Returns: Array of consecutive integers

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.range(1, 5) == [1, 2, 3, 4, 5];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val range(@Number Val from, @Number Val to) {
        return rangeStepped(from, to, Val.of(1));
    }

    @Function(docs = """
            ```array.rangeStepped(NUMBER from, NUMBER to, NUMBER step)```

            Creates an array containing integers from from to to (both inclusive), incrementing
            by step. The step can be positive or negative. Returns an error if step is zero.
            All parameters must be integers. For positive step, from must be less than or equal
            to to. For negative step, from must be greater than or equal to to. Range is
            inclusive on both ends.

            Parameters:
            - from: Starting value (inclusive)
            - to: Ending value (inclusive)
            - step: Increment value (positive or negative, not zero)

            Returns: Array of integers with specified step

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.rangeStepped(1, 10, 2) == [1, 3, 5, 7, 9];
                array.rangeStepped(10, 1, -2) == [10, 8, 6, 4, 2];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val rangeStepped(@Number Val from, @Number Val to, @Number Val step) {
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
     * Builds range array with positive step value. Calculates iteration count
     * upfront to ensure
     * explicit loop termination.
     */
    private static void buildRangeWithPositiveStep(ArrayNode result, long fromValue, long toValue, long stepValue) {
        final var totalRange     = toValue - fromValue;
        final var numberOfSteps  = totalRange / stepValue;
        final var iterationCount = numberOfSteps + 1;

        for (long iteration = 0; iteration < iterationCount; iteration++) {
            final var currentValue = fromValue + (iteration * stepValue);
            addNumberNode(result, currentValue);
        }
    }

    /**
     * Builds range array with negative step value. Calculates iteration count
     * upfront to ensure
     * explicit loop termination.
     */
    private static void buildRangeWithNegativeStep(ArrayNode result, long fromValue, long toValue, long stepValue) {
        final var totalRange     = fromValue - toValue;
        final var stepMagnitude  = -stepValue;
        final var numberOfSteps  = totalRange / stepMagnitude;
        final var iterationCount = numberOfSteps + 1;

        for (long iteration = 0; iteration < iterationCount; iteration++) {
            final var currentValue = fromValue + (iteration * stepValue);
            addNumberNode(result, currentValue);
        }
    }

    /**
     * Adds number node to result array, using int representation if value fits in
     * int range.
     */
    private static void addNumberNode(ArrayNode result, long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            result.add(Val.JSON.numberNode((int) value));
        } else {
            result.add(Val.JSON.numberNode(value));
        }
    }

    @Function(docs = """
            ```array.crossProduct(ARRAY array1, ARRAY array2)```

            Returns the Cartesian product of two arrays. The result is an array of 2-element
            arrays, where each element contains one item from array1 paired with one item from
            array2. Returns an empty array if either input array is empty.

            Parameters:
            - array1: First array
            - array2: Second array

            Returns: Array of all possible pairs

            Example - generate permission-resource combinations:
            ```sapl
            policy "example"
            permit
            where
                var actions = ["read", "write"];
                var resources = ["doc1", "doc2"];
                var combinations = array.crossProduct(actions, resources);
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
            ```array.zip(ARRAY array1, ARRAY array2)```

            Combines two arrays element-wise into an array of 2-element arrays (pairs).
            The resulting array has length equal to the shorter of the two input arrays.

            Parameters:
            - array1: First array
            - array2: Second array

            Returns: Array of paired elements

            Example:
            ```sapl
            policy "example"
            permit
            where
                array.zip([1, 2, 3], ["a", "b", "c"]) == [[1, "a"], [2, "b"], [3, "c"]];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val zip(@Array Val array1, @Array Val array2) {
        final var jsonArray1 = array1.getArrayNode();
        final var jsonArray2 = array2.getArrayNode();

        final var result  = Val.JSON.arrayNode();
        final var minSize = Math.min(jsonArray1.size(), jsonArray2.size());

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
