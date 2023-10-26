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

import static io.sapl.grammar.sapl.impl.util.ParserUtil.filterComponent;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsError;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.test.StepVerifier;

class ApplyFilteringExtendedTests {

    @Test
    void filterUndefined() {
        assertExpressionReturnsError("undefined |- { @.name : filter.remove }",
                "Filters cannot be applied to undefined values.");
    }

    @Test
    void filterError() {
        assertExpressionReturnsError("(10/0) |- { @.name : filter.remove }", "Division by zero");
    }

    private static Stream<Arguments> provideStringsForexpressionEvaluatesToExpectedValue() {
        // @formatter:off
		return Stream.of(
				// No filter Statement
				Arguments.of("{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" } |- { }",
                             "{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }"),
				
				// Remove key step from object
	 			Arguments.of("{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" } |- { @.name : filter.remove }",
				     	     "{ \"job\" : \"recreational surgeon\" }"),
	 			
	 			// Remove element two key steps down from object
	 			Arguments.of("{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : 1000000 } }" 
	 					   + "|- { @.job.wage : filter.remove }",
	 					     "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\" } }"),
	 			
	 			// Remove element three key steps down from object
	 			Arguments.of("{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"monthly\" : 1000000, \"currency\" : \"GBP\"} } } "
	 					   + "|- { @.job.wage.monthly : filter.remove }",
	 					     "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"currency\" : \"GBP\"} } }"),
	 			
	 			// Remove key step from array
	 			Arguments.of("[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
	 					   + "|- { @.name : filter.remove }",
	 					     "[ { \"job\" : \"recreational surgeon\" }, { \"job\" : \"professional perforator\"} ] "),
	 			
	 			// Remove no steps each
	 			Arguments.of("[ null, true ] |- { each @ : filter.remove }",
	 					     "[ ]"),
	 			
	 			// Empty string no steps no each
	 			Arguments.of("[ null, true ] |- { @ : mock.emptyString }",
	 					     "\"\""),
	 			
	 			// Empty string no steps each
	 			Arguments.of("[ null, true ] |- { each @ : mock.emptyString }",
	 					     "[ \"\", \"\" ]"),

	 			// Remove result array no each
	 			Arguments.of("[ null, true ] |-  { @[0] : filter.remove }",
					     "[ true ]"),

	 			// Blacken index in selected field
	 			Arguments.of("[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
	 					   + "|- { @[0].job : filter.blacken }",
	 					     "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"XXXXXXXXXXXXXXXXXXXX\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ]"),
			
	 			// Blacken result array no each
	 			Arguments.of("[ null, \"secret\", true ] |- { @[-2] : filter.blacken }",
	 					     "[ null, \"XXXXXX\", true ]"),
			
	 			// Remove array slice negative
	 			Arguments.of("[ 0, 1, 2, 3, 4, 5 ] |- { @[-2:] : filter.remove }",
	 					     "[0, 1, 2, 3 ]"),
			
	 			// Remove array slice positive
	 			Arguments.of("[ 0, 1, 2, 3, 4, 5 ] |- { @[2:4:1] : filter.remove }",
	 					     "[ 0, 1, 4, 5 ]"),
			
	 			// Remove array slice negative to
	 			Arguments.of("[ 1, 2, 3, 4, 5 ] |- { @[0:-2:2] : filter.remove }",
	 					     "[ 2, 4, 5 ]"),
			
	 			// Remove array slice negative step
	 			Arguments.of("[ 0, 1, 2, 3, 4, 5 ] |- { @[1:5:-2] : filter.remove }",
	 					     "[ 0, 2, 4, 5 ]"),

	 			// Remove attribute union step
	 			Arguments.of("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } |- { @[\"b\" , \"d\"] : filter.remove }",
	 					     "{ \"a\" : 1, \"c\" : 3 }"),
			
	 			// Remove array element in attribute union step
	 			Arguments.of("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : filter.remove }",
	 					     "{ \"a\" : [0,1,2,3], \"b\" : [0,2,3], \"c\" : [0,1,2,3], \"d\" : [0,2,3] }"),
			
	 			// Replace with empty string array element in attribute union step
	 			Arguments.of("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : mock.emptyString }",
	 					     "{ \"a\" : [0,1,2,3], \"b\" : [0,\"\",2,3], \"c\" : [0,1,2,3], \"d\" : [0,\"\",2,3] }"),
			
	 			// Remove index union step
	 			Arguments.of("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3] : filter.remove }",
	 					     "[ [0,1,2,3], [2,1,2,3], [4,1,2,3] ]"),
			
	 			// Double remove index union step
	 			Arguments.of("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3][2,1] : filter.remove }",
	 					     "[ [0,1,2,3], [1,3], [2,1,2,3], [3,3], [4,1,2,3] ]"),
			
	 			// Relative undefined
	 			Arguments.of("{}[?(@ == undefined)]",
					         "[]"),

	 			// Replace recursive key step object
	 			Arguments.of("{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
	 					   + "|- { @..key : filter.blacken }",
	 					     "{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" }, { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }")
				);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideStringsForexpressionEvaluatesToExpectedValue")
	void expressionEvaluatesToExpectedValue(String expression, String expected) {
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void removeNoStepsNoEach() {
		var expression = "{} |- { @ : filter.remove }";
		var expected   = Val.UNDEFINED;
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void emptyStringEachNoArray() {
		assertExpressionReturnsErrors("[ {}, true ] |- { each @[0] : mock.emptyString }");
	}
	
	@Test
	void removeEachNoArray() {
		assertExpressionReturnsError("{} |- { each @ : filter.remove }",
				"Type mismatch error. Cannot use 'each' keyword with non-array values. Value type was: OBJECT");
	}

	@Test
	void multipleFilterStatements() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..[0] : filter.remove, @..key : filter.blacken, @.array2[-1] : filter.remove }";
		var expected   = "{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 2, 3, 4 ] }";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void complexWithFunctionAndRelative() throws IOException {
		var root     = Val.ofJson("{\"name\": \"Ben\", \"origin\": \"Berlin\"}");
		var filter   = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expected = Val.ofJson("{\"name\": \"Ben from Berlin\"}");
		StepVerifier.create(filter.apply(root).contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, root))
				.contextWrite(MockUtil::setUpAuthorizationContext)).expectNext(expected).verifyComplete();
	}

	@Test
	void complexWithFunctionAndRelativeArray() throws IOException {
		var root     = Val.ofJson(
				"[ {\"name\": \"Ben\", \"origin\": \"Berlin\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}]");
		var filter   = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expected = Val.ofJson("[{\"name\": \"Ben from Berlin\"},{ \"name\": \"Felix from Zürich\"}]");
		StepVerifier.create(filter.apply(root).contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, root))
				.contextWrite(MockUtil::setUpAuthorizationContext)).expectNext(expected).verifyComplete();
	}

}
