/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplyStepsWildcardTests {

    @ParameterizedTest
    // @formatter:off
	@ValueSource(strings = {
		// wildcardStepPropagatesErrors
		"(10/0).*",
		// wildcardStepOnOtherThanArrayOrObjectFails
		"\"\".*",
		// wildcardStepOnUndefinedFails
		"undefined.*"
	}) 
	// @formatter:on
    void expressionEvaluatesToErrors(String expression) {
        assertExpressionReturnsErrors(expression);
    }

    private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
	 			// wildcardStepOnArrayIsIdentity
	 			Arguments.of("[1,2,3,4,5,6,7,8,9].*", 
	 					     "[1,2,3,4,5,6,7,8,9]"),

	 			// applyToObject
	 			Arguments.of("{\"key1\":null,\"key2\":true,\"key3\":false,\"key4\":{\"other_key\":123}}.*",
	 			             "[ null, true, false , { \"other_key\" : 123 } ]"),

	 			// replaceWildcardStepArray
	 			Arguments.of("[1,2,3,4,5] |- { @.* : mock.emptyString }",
	 			             "[ \"\", \"\",\"\", \"\", \"\"]"),

	 			// replaceWildcardStepObject
	 			Arguments.of("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @.* : mock.emptyString }",
	 			             "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }"),

	 			// replaceRecursiveWildcardStepArray
	 			Arguments.of("[1,2,3,4,5] |- { @..* : mock.emptyString }",
	 			             "[ \"\", \"\",\"\", \"\", \"\"]"),

	 			// filterNonObjectNonArray
	 			Arguments.of("\"Herbert\" |- { @..* : mock.emptyString }",
	 			             "\"Herbert\""),

	 			// replaceRecursiveWildcardStepObject
	 			Arguments.of("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @..* : mock.emptyString }",
	 			             "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }"),

	 			// filterInObjectDescend
	 			Arguments.of("{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
	 					   + "|- { @.*.partner : mock.emptyString }",
	 					     "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } }"),

	 			// filterInArrayDescend
	 			Arguments.of("{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
	 					   + "|- { @..children[*] : mock.nil }",
	 					     "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [null,null,null] } } "),

	 			// filterInArrayDescend2
	 			Arguments.of("[ {\"a\" : 1},{\"b\" : 2}] |- { @[*].b : mock.nil }",
	 			             "[ {\"a\" : 1},{\"b\" : null}]")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}
}
