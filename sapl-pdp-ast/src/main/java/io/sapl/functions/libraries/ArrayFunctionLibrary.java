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
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.DoubleBinaryOperator;

/**
 * Array manipulation functions.
 */
@UtilityClass
@FunctionLibrary(name = ArrayFunctionLibrary.NAME, description = ArrayFunctionLibrary.DESCRIPTION, libraryDocumentation = ArrayFunctionLibrary.DOCUMENTATION)
public class ArrayFunctionLibrary {

    public static final String NAME          = "array";
    public static final String DESCRIPTION   = "Array manipulation functions.";
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
                var allOfSubjectsPermissions = array.union(direct, inherited);
                array.containsAll(allOfSubjectsPermissions, ["read", "write"]);
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

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final String ERROR_EMPTY_ARRAY_AVERAGE         = "Cannot calculate average of an empty array.";
    private static final String ERROR_EMPTY_ARRAY_HEAD            = "Cannot get head of an empty array.";
    private static final String ERROR_EMPTY_ARRAY_LAST            = "Cannot get last of an empty array.";
    private static final String ERROR_EMPTY_ARRAY_MEDIAN          = "Cannot calculate median of an empty array.";
    private static final String ERROR_MIXED_TYPE_NON_NUMERIC      = "All array elements must be numeric. Found non-numeric element: ";
    private static final String ERROR_MIXED_TYPE_NON_TEXT         = "All array elements must be text. Found non-text element: ";
    private static final String ERROR_PARAMETERS_MUST_BE_INTEGERS = "All parameters must be integers.";
    private static final String ERROR_PREFIX_ALL_ELEMENTS_MUST_BE = "All array elements must be ";
    private static final String ERROR_PREFIX_CANNOT_FIND          = "Cannot find ";
    private static final String ERROR_PREFIX_ELEMENTS_MUST_BE     = "Array elements must be numeric or text. First element is: ";
    private static final String ERROR_PREFIX_FOUND_NON            = ". Found non-";
    private static final String ERROR_STEP_MUST_NOT_BE_ZERO       = "Step must not be zero.";
    private static final String ERROR_SUFFIX_ELEMENT              = " element: ";
    private static final String ERROR_SUFFIX_EMPTY_ARRAY          = " of an empty array.";
    private static final String ERROR_SUFFIX_PERIOD               = ".";

    private static final String TYPE_NAME_NUMERIC = "numeric";
    private static final String TYPE_NAME_TEXT    = "text";

    /**
     * Concatenates multiple arrays into a single array.
     *
     * @param arrays
     * arrays to concatenate
     *
     * @return new array containing all elements in order
     */
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
    public static Value concatenate(ArrayValue... arrays) {
        val builder = ArrayValue.builder();
        for (var array : arrays) {
            builder.addAll(array);
        }
        return builder.build();
    }

    /**
     * Returns the set difference between two arrays.
     *
     * @param array1
     * array to subtract from
     * @param array2
     * array of elements to remove
     *
     * @return new array with elements in array1 but not in array2
     */
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
    public static Value difference(ArrayValue array1, ArrayValue array2) {
        val excludeSet = new HashSet<>(array2);
        val result     = new LinkedHashSet<Value>();

        for (val element : array1) {
            if (!excludeSet.contains(element)) {
                result.add(element);
            }
        }

        return createArrayFromElements(result);
    }

    /**
     * Returns the union of multiple arrays, removing duplicates.
     *
     * @param arrays
     * arrays to combine
     *
     * @return new array with all unique elements
     */
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
    public static Value union(ArrayValue... arrays) {
        val result = new LinkedHashSet<Value>();
        for (var array : arrays) {
            result.addAll(array);
        }
        return createArrayFromElements(result);
    }

    /**
     * Removes duplicate elements from an array, preserving order.
     *
     * @param array
     * array to deduplicate
     *
     * @return new array with unique elements
     */
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
    public static Value toSet(ArrayValue array) {
        val result = new LinkedHashSet<>(array);
        return createArrayFromElements(result);
    }

    /**
     * Returns the intersection of multiple arrays.
     *
     * @param arrays
     * arrays to intersect
     *
     * @return new array with elements present in all arrays
     */
    @Function(docs = """
            ```array.intersect(ARRAY...arrays)```

                    Creates a new array containing only elements present in all parameter arrays.
                    Removes duplicates from the result, preserving order from the first array.

                    Parameters:
                    - arrays: Arrays to intersect

                    Returns: New array with common elements

                    Example - find shared permissions:
            ```sapl
            policy "example"
            permit
            where
                var adminPerms = ["read", "write", "delete"];
                var editorPerms = ["read", "write"];
                var viewerPerms = ["read"];
                var common = array.intersect(adminPerms, editorPerms, viewerPerms);
                common == ["read"];
            ```
            """, schema = RETURNS_ARRAY)
    public static Value intersect(ArrayValue... arrays) {
        if (arrays.length == 0) {
            return Value.EMPTY_ARRAY;
        }
        if (arrays.length == 1) {
            return toSet(arrays[0]);
        }
        val result = new LinkedHashSet<>(arrays[0]);
        for (int i = 1; i < arrays.length; i++) {
            val currentSet = new HashSet<>(arrays[i]);
            result.retainAll(currentSet);
            if (result.isEmpty()) {
                break;
            }
        }

        return createArrayFromElements(result);
    }

    /**
     * Checks if an array contains at least one element from another array.
     *
     * @param array
     * array to search in
     * @param elements
     * elements to search for
     *
     * @return Value.TRUE if any element was found
     */
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
    public static Value containsAny(ArrayValue array, ArrayValue elements) {
        val arraySet = new HashSet<>(array);
        for (val element : elements) {
            if (arraySet.contains(element)) {
                return Value.TRUE;
            }
        }
        return Value.FALSE;
    }

    /**
     * Checks if an array contains all elements from another array.
     *
     * @param array
     * array to search in
     * @param elements
     * elements that must all be present
     *
     * @return Value.TRUE if all elements were found
     */
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
    public static Value containsAll(ArrayValue array, ArrayValue elements) {
        val arraySet = new HashSet<>(array);

        for (val element : elements) {
            if (!arraySet.contains(element)) {
                return Value.FALSE;
            }
        }
        return Value.TRUE;
    }

    /**
     * Checks if an array contains all elements from another array in sequential
     * order.
     *
     * @param array
     * array to search in
     * @param elements
     * elements that must appear in this order
     *
     * @return Value.TRUE if elements appear in order
     */
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
    public static Value containsAllInOrder(ArrayValue array, ArrayValue elements) {
        if (elements.isEmpty()) {
            return Value.TRUE;
        }
        var arrayIndex           = 0;
        var requiredElementIndex = 0;
        while (arrayIndex < array.size() && requiredElementIndex < elements.size()) {
            if (array.get(arrayIndex).equals(elements.get(requiredElementIndex))) {
                requiredElementIndex++;
            }
            arrayIndex++;
        }
        return Value.of(requiredElementIndex == elements.size());
    }

    /**
     * Sorts an array in ascending order.
     *
     * @param array
     * array to sort
     *
     * @return new sorted array
     */
    @Function(docs = """
            ```array.sort(ARRAY array)```

            Returns a new array with elements sorted in ascending order. The function determines
            the sort order based on the type of the first element. Numeric arrays are sorted
            numerically, string arrays are sorted lexicographically. All elements must be of the
            same type. Returns an error for mixed types, or unsupported types.

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
    public static Value sort(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        val firstElement = array.getFirst();

        if (firstElement instanceof NumberValue) {
            return sortNumericArray(array);
        } else if (firstElement instanceof TextValue) {
            return sortTextArray(array);
        } else {
            return Value.error(
                    ERROR_PREFIX_ELEMENTS_MUST_BE + firstElement.getClass().getSimpleName() + ERROR_SUFFIX_PERIOD);
        }
    }

    /**
     * Sorts numeric array elements.
     */
    private static Value sortNumericArray(ArrayValue arrayValue) {
        val numbers = new ArrayList<NumberValue>();

        for (val element : arrayValue) {
            if (!(element instanceof NumberValue number)) {
                return Value.error(ERROR_PREFIX_ALL_ELEMENTS_MUST_BE + TYPE_NAME_NUMERIC + ERROR_PREFIX_FOUND_NON
                        + TYPE_NAME_NUMERIC + ERROR_SUFFIX_ELEMENT + element);
            }
            numbers.add(number);
        }

        numbers.sort(Comparator.comparingDouble(n -> n.value().doubleValue()));

        val builder = ArrayValue.builder();
        for (val number : numbers) {
            builder.add(number);
        }
        return builder.build();
    }

    /**
     * Sorts text array elements.
     */
    private static Value sortTextArray(ArrayValue arrayValue) {
        val texts = new ArrayList<TextValue>();

        for (val element : arrayValue) {
            if (!(element instanceof TextValue text)) {
                return Value.error(ERROR_PREFIX_ALL_ELEMENTS_MUST_BE + TYPE_NAME_TEXT + ERROR_PREFIX_FOUND_NON
                        + TYPE_NAME_TEXT + ERROR_SUFFIX_ELEMENT + element);
            }
            texts.add(text);
        }

        texts.sort(Comparator.comparing(TextValue::value));

        val builder = ArrayValue.builder();
        for (val text : texts) {
            builder.add(text);
        }
        return builder.build();
    }

    /**
     * Flattens a nested array by one level.
     *
     * @param array
     * array to flatten
     *
     * @return new flattened array
     */
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
    public static Value flatten(ArrayValue array) {
        val builder = ArrayValue.builder();
        for (val element : array) {
            if (element instanceof ArrayValue innerArray) {
                builder.addAll(innerArray);
            } else {
                builder.add(element);
            }
        }
        return builder.build();
    }

    /**
     * Returns the number of elements in an array.
     *
     * @param array
     * array to measure
     *
     * @return element count
     */
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
    public static Value size(ArrayValue array) {
        return Value.of(array.size());
    }

    /**
     * Returns an array with its elements in reversed order.
     *
     * @param array
     * array to reverse
     *
     * @return new array with elements in reverse order
     */
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
    public static Value reverse(ArrayValue array) {
        val builder = ArrayValue.builder();
        val size    = array.size();
        for (int i = size - 1; i >= 0; i--) {
            builder.add(array.get(i));
        }
        return builder.build();
    }

    /**
     * Checks if an array contains only distinct elements.
     *
     * @param array
     * array to test
     *
     * @return Value.TRUE if array contains no duplicates
     */
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
    public static Value isSet(ArrayValue array) {
        val seen = new HashSet<Value>();
        for (val element : array) {
            if (!seen.add(element)) {
                return Value.FALSE;
            }
        }
        return Value.TRUE;
    }

    /**
     * Checks if an array is empty.
     *
     * @param array
     * array to test
     *
     * @return Value.TRUE if array has no elements
     */
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
    public static Value isEmpty(ArrayValue array) {
        return Value.of(array.isEmpty());
    }

    /**
     * Returns the first element of an array.
     *
     * @param array
     * array to extract from
     *
     * @return first element
     */
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
    public static Value head(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.error(ERROR_EMPTY_ARRAY_HEAD);
        }
        return array.getFirst();
    }

    /**
     * Returns the last element of an array.
     *
     * @param array
     * array to extract from
     *
     * @return last element
     */
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
    public static Value last(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.error(ERROR_EMPTY_ARRAY_LAST);
        }
        return array.getLast();
    }

    /**
     * Returns the maximum value from an array.
     *
     * @param array
     * array to find maximum in
     *
     * @return maximum value
     */
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
    public static Value max(ArrayValue array) {
        return findExtremum(array, "max", true);
    }

    /**
     * Returns the minimum value from an array.
     *
     * @param array
     * array to find minimum in
     *
     * @return minimum value
     */
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
    public static Value min(ArrayValue array) {
        return findExtremum(array, "min", false);
    }

    /**
     * Finds extremum (min or max) in array.
     */
    private static Value findExtremum(ArrayValue array, String operationName, boolean findMaximum) {
        if (array.isEmpty()) {
            return Value.error(ERROR_PREFIX_CANNOT_FIND + operationName + ERROR_SUFFIX_EMPTY_ARRAY);
        }
        val firstElement = array.getFirst();
        if (firstElement instanceof NumberValue) {
            return findNumericExtremum(array, findMaximum);
        } else if (firstElement instanceof TextValue) {
            return findTextualExtremum(array, findMaximum);
        } else {
            return Value.error(
                    ERROR_PREFIX_ELEMENTS_MUST_BE + firstElement.getClass().getSimpleName() + ERROR_SUFFIX_PERIOD);
        }
    }

    /**
     * Finds extremum value among numeric elements.
     */
    private static Value findNumericExtremum(ArrayValue arrayValue, boolean findMaximum) {
        var extremumValue = ((NumberValue) arrayValue.getFirst()).value().doubleValue();
        for (int i = 1; i < arrayValue.size(); i++) {
            val element = arrayValue.get(i);
            if (!(element instanceof NumberValue number)) {
                return Value.error(ERROR_MIXED_TYPE_NON_NUMERIC + element);
            }
            val value = number.value().doubleValue();
            if (findMaximum ? value > extremumValue : value < extremumValue) {
                extremumValue = value;
            }
        }
        return Value.of(extremumValue);
    }

    /**
     * Finds extremum value among textual elements.
     */
    private static Value findTextualExtremum(ArrayValue arrayValue, boolean findMaximum) {
        var extremumValue = ((TextValue) arrayValue.getFirst()).value();
        for (int i = 1; i < arrayValue.size(); i++) {
            val element = arrayValue.get(i);
            if (!(element instanceof TextValue text)) {
                return Value.error(ERROR_MIXED_TYPE_NON_TEXT + element);
            }
            val textValue = text.value();
            if (findMaximum ? textValue.compareTo(extremumValue) > 0 : textValue.compareTo(extremumValue) < 0) {
                extremumValue = textValue;
            }
        }
        return Value.of(extremumValue);
    }

    /**
     * Returns the sum of all numeric elements in an array.
     *
     * @param array
     * array of numbers to sum
     *
     * @return sum of all elements
     */
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
    public static Value sum(ArrayValue array) {
        return reduceNumericArray(array, 0.0, Double::sum);
    }

    /**
     * Returns the product of all numeric elements in an array.
     *
     * @param array
     * array of numbers to multiply
     *
     * @return product of all elements
     */
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
    public static Value multiply(ArrayValue array) {
        return reduceNumericArray(array, 1.0, (a, b) -> a * b);
    }

    /**
     * Reduces numeric array using accumulator function with identity value.
     */
    private static Value reduceNumericArray(ArrayValue array, double identityValue, DoubleBinaryOperator accumulator) {
        if (array.isEmpty()) {
            return Value.of(identityValue);
        }
        var result = identityValue;
        for (val element : array) {
            if (!(element instanceof NumberValue number)) {
                return Value.error(ERROR_MIXED_TYPE_NON_NUMERIC + element);
            }
            result = accumulator.applyAsDouble(result, number.value().doubleValue());
        }

        return Value.of(result);
    }

    /**
     * Returns the arithmetic mean of all numeric elements in an array.
     *
     * @param array
     * array of numbers to average
     *
     * @return average value
     */
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
    public static Value avg(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.error(ERROR_EMPTY_ARRAY_AVERAGE);
        }
        var sum = 0.0;
        for (val element : array) {
            if (!(element instanceof NumberValue number)) {
                return Value.error(ERROR_MIXED_TYPE_NON_NUMERIC + element);
            }
            sum += number.value().doubleValue();
        }

        return Value.of(sum / array.size());
    }

    /**
     * Returns the median value of all numeric elements in an array.
     *
     * @param array
     * array of numbers to find median of
     *
     * @return median value
     */
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
    public static Value median(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.error(ERROR_EMPTY_ARRAY_MEDIAN);
        }
        val numbers = new ArrayList<Double>();
        for (val element : array) {
            if (!(element instanceof NumberValue number)) {
                return Value.error(ERROR_MIXED_TYPE_NON_NUMERIC + element);
            }
            numbers.add(number.value().doubleValue());
        }
        numbers.sort(Double::compareTo);
        val size        = numbers.size();
        val medianValue = (size % 2 == 1) ? numbers.get(size / 2)
                : (numbers.get(size / 2 - 1) + numbers.get(size / 2)) / 2.0;
        return Value.of(medianValue);
    }

    /**
     * Creates an array of consecutive integers from from to to (inclusive).
     *
     * @param from
     * starting value (inclusive)
     * @param to
     * ending value (inclusive)
     *
     * @return array of consecutive integers
     */
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
    public static Value range(NumberValue from, NumberValue to) {
        return rangeStepped(from, to, Value.ONE);
    }

    /**
     * Creates an array of integers from from to to (inclusive) with a step
     * increment.
     *
     * @param from
     * starting value (inclusive)
     * @param to
     * ending value (inclusive)
     * @param step
     * increment value (positive or negative, not zero)
     *
     * @return array of integers with specified step
     */
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
    public static Value rangeStepped(NumberValue from, NumberValue to, NumberValue step) {
        if (isNonLongValue(from) || isNonLongValue(to) || isNonLongValue(step)) {
            return Value.error(ERROR_PARAMETERS_MUST_BE_INTEGERS);
        }

        val fromValue = from.value().longValue();
        val toValue   = to.value().longValue();
        val stepValue = step.value().longValue();

        if (stepValue == 0) {
            return Value.error(ERROR_STEP_MUST_NOT_BE_ZERO);
        }

        val builder = ArrayValue.builder();

        if (stepValue > 0) {
            for (long currentValue = fromValue; currentValue <= toValue; currentValue += stepValue) {
                builder.add(Value.of(currentValue));
            }
        } else {
            for (long currentValue = fromValue; currentValue >= toValue; currentValue += stepValue) {
                builder.add(Value.of(currentValue));
            }
        }

        return builder.build();
    }

    /**
     * Checks if NumberValue represents a long integer.
     */
    private static boolean isNonLongValue(NumberValue number) {
        val value = number.value();
        return value.scale() > 0 || value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                || value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0;
    }

    /**
     * Returns the Cartesian product of two arrays.
     *
     * @param array1
     * first array
     * @param array2
     * second array
     *
     * @return array of all possible pairs
     */
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
                combinations == [ ["read" , "doc1"], ["read" , "doc2"], ["write" , "doc1"], ["write" , "doc2"]];
            ```
            """, schema = RETURNS_ARRAY)
    public static Value crossProduct(ArrayValue array1, ArrayValue array2) {
        if (array1.isEmpty() || array2.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        val builder = ArrayValue.builder();

        for (val element1 : array1) {
            for (val element2 : array2) {
                val pair = Value.ofArray(element1, element2);
                builder.add(pair);
            }
        }

        return builder.build();
    }

    /**
     * Combines two arrays element-wise into an array of pairs.
     *
     * @param array1
     * first array
     * @param array2
     * second array
     *
     * @return array of paired elements
     */
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
    public static Value zip(ArrayValue array1, ArrayValue array2) {
        val builder = ArrayValue.builder();
        val minSize = Math.min(array1.size(), array2.size());

        for (int i = 0; i < minSize; i++) {
            val pair = Value.ofArray(array1.get(i), array2.get(i));
            builder.add(pair);
        }

        return builder.build();
    }

    /**
     * Creates ArrayValue from collection of elements.
     */
    private static Value createArrayFromElements(Collection<Value> elements) {
        return ArrayValue.builder().addAll(elements).build();
    }
}
