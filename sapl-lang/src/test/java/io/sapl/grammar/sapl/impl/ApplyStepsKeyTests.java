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
package io.sapl.grammar.sapl.impl;

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.Val;

class ApplyStepsKeyTests {

    @Test
    void keyStepPropagatesErrors() {
        assertExpressionReturnsErrors("(10/0).key");
    }

    @Test
    void keyStepToNonObjectUndefined() {
        assertExpressionEvaluatesTo("true.key", Val.UNDEFINED);
    }

    @Test
    void keyStepToEmptyObject() {
        assertExpressionEvaluatesTo("{}.key", Val.UNDEFINED);
    }

    private static Stream<Arguments> provideStringsForExpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
				// Key step to object
	 			Arguments.of("{\"key\" : true}.key", "true"),

	 			// Key step to array
	 			Arguments.of("[{\"key\" : true},{\"key\": 123}].key", "[true,123]"),

	 			// Key step to array no match
	 			Arguments.of("[{\"key\" : true},{\"key\": 123}].x", "[]"),

	 			// Filter non-object or array
	 			Arguments.of("\"Gudrun\" |- { @.key : mock.nil }", "\"Gudrun\""),

                // Filter object
                Arguments.of("{\"key\" : true, \"other\" : false} |- { @.key : mock.nil}",
                             "{\"key\" : null, \"other\" : false}"),

	 			// Filter object descend
	 			Arguments.of("{\"key\" : { \"key2\" : true}, \"other\" : false} |- { @.key.key2 : mock.nil}",
	 					     "{\"key\" : {\"key2\" : null }, \"other\" : false}"),

	 			// Filter array
	 			Arguments.of("[ {\"key\" : true, \"other\" : false} , false ] |- { @.key : mock.nil}",
	 					     "[ {\"key\" : null, \"other\" : false} , false ]"),

	 			// Filter empty array
	 			Arguments.of("[] |- { @.key : mock.nil}",
	 					     "[]")
				);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideStringsForExpressionEvaluatesToExpectedValue")
    void expressionEvaluatesToExpectedValue(String expression, String expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }
}
