/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplyStepsRecursiveKeyTests {

    @Test
    void recursiveKeyStepPropagatesErrors() {
        assertExpressionReturnsErrors("(10/0)..key");
    }

    private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
				// recursiveKeyStepOnUndefinedIsEmpty
	 			Arguments.of("undefined..key", "[]"),

	 			// applyToNull
	 			Arguments.of("null..key", "[]"),

	 			// applyToObject
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..key",
	 			             "[ \"value1\", \"value2\", \"value3\" ]"),

	 			// applyToObjectNotPresent
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..yek",
	 			             "[ ]"),

	 			// filterArray
	 			Arguments.of("[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
	 					   + " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
	 					   + " |- { @..key : mock.nil} ",
	 			             "[{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]},{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]}]]"),

	 			// filterArrayDescend
	 			Arguments.of("[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
	 					   + " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
	 					   + " |- { @..key..key2 : mock.nil}",
	 			             "[{\"key\":\"value1\",\"array1\":[{\"key\":{\"key2\":null}},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]},{\"key\":\"value1\",\"array1\":[{\"key\":\"value2\"},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]}]"),

	 			// filterArrayEmpty
	 			Arguments.of("[] |- { @..key..key2 : mock.nil} ", "[]")
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
