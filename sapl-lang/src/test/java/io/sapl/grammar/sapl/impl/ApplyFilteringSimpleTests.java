/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.Val;

class ApplyFilteringSimpleTests {

    @Test
    void filterPropagatesError() {
        assertExpressionReturnsErrors("(10/0) |- filter.remove");
    }

    @Test
    void filterUndefined() {
        assertExpressionReturnsErrors("undefined |- filter.remove");
    }

    @Test
    void removeNoEach() {
        var expression = "{} |- filter.remove";
        var expected   = Val.UNDEFINED;
        assertExpressionEvaluatesTo(expression, expected);
    }

    @Test
    void removeEachNoArray() {
        assertExpressionReturnsErrors("{} |- each filter.remove");
    }

    private static Stream<Arguments> provideStringsForExpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
	 			// Remove each array
	 			Arguments.of("[null] |- each filter.remove",
	 					     "[]"),

	 			// Empty string no each
	 			Arguments.of("[] |- mock.emptyString",
	 					     "\"\""),

	 			// Empty string each
	 			Arguments.of("[ null, 5 ] |- each mock.emptyString(null)",
	 					     "[ \"\", \"\" ]")
				);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideStringsForExpressionEvaluatesToExpectedValue")
    void expressionEvaluatesToExpectedValue(String expression, String expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }
}
