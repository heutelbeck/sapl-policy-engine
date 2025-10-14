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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsErrors;

class ObjectImplCustomTests {

    @Test
    void objectPropagatesErrors() {
        assertExpressionReturnsErrors("{ \"a\": 1/0 }");
    }

    private static Stream<Arguments> provideStringsForExpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
	 			// simpleObjectToVal
	 			Arguments.of("{ \"a\": true, \"b\": false }","{ \"a\": true, \"b\": false }"),

	 			// emptyObject
	 			Arguments.of("{}", "{}"),

	 			// dropsUndefined
                Arguments.of("{ \"a\": true, \"b\": false, \"c\": undefined }","{ \"a\": true, \"b\": false }")
	 		);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideStringsForExpressionEvaluatesToExpectedValue")
    void expressionEvaluatesToExpectedValue(String expression, String expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }
}
