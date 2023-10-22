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

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class ApplyStepsExpressionTests {

	@Test
	void expressionStepPropagatesErrors1() {
		assertExpressionReturnsErrors("[][(10/0)]");
	}

	@Test
	void expressionStepPropagatesErrors2() {
		assertExpressionReturnsErrors("[(10/0)][(2+2)]");
	}

	@Test
	void expressionStepOutOfBounds1() {
		assertExpressionReturnsErrors("[1,2,3][(1+100)]");
	}

	@Test
	void expressionStepOutOfBounds2() {
		assertExpressionReturnsErrors("[1,2,3][(1 - 100)]");
	}

	@Test
	void expressionStepOutOfBounds3() {
		assertExpressionReturnsErrors("[1,2,3][(1 - 100)]");
	}

	@Test
	void applyExpressionStepToNonObjectNonArrayFails() {
		assertExpressionReturnsErrors("undefined[(1 + 1)]");
	}

	@Test
	void expressionEvaluatesToBooleanAndFails() {
		assertExpressionReturnsErrors("[1,2,3][(true)]");
	}

	@Test
	void applyToArrayWithTextualExpressionResult() {
		assertExpressionReturnsErrors("[0,1,2,3,4,5,6,7,8,9][(\"key\")]");
	}

	@Test
	void applyToArrayWithNumberExpressionResult() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][(2+3)]", "5");
	}

	@Test
	void applyToObjectWithTextualResult() {
		assertExpressionEvaluatesTo("{ \"key\" : true }[(\"ke\"+\"y\")]", "true");
	}

	@Test
	void applyToObjectWithTextualResultNonExistingKey() {
		assertExpressionEvaluatesTo("{ \"key\" : true }[(\"no_ke\"+\"y\")]", Val.UNDEFINED);
	}

	@Test
	void applyToObjectWithNumericalResult() {
		assertExpressionReturnsErrors("{ \"key\" : true }[(5+2)]");
	}

	@Test
	void applyToObjectWithError() {
		assertExpressionReturnsErrors("{ \"key\" : true }[(10/0)]");
	}

	@Test
	void filterNonArrayNonObject() {
		assertExpressionEvaluatesTo("123 |- { @[(1+1)] : mock.nil }", "123");
	}

	@Test
	void removeExpressionStepArray() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }";
		var expected   = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterTypeMismatch1() {
		assertExpressionReturnsErrors("[ [4,1,2,3] ] |- { @[(false)] : filter.remove }");
	}

	@Test
	void filterTypeMismatch2() {
		assertExpressionReturnsErrors("[ [4,1,2,3] ] |- { @[(\"a\")] : filter.remove }");
	}

	@Test
	void filterTypeMismatch3() {
		assertExpressionReturnsErrors("{ \"a\": [4,1,2,3] } |- { @[(123)] : filter.remove }");
	}

	@Test
	void removeExpressionStepObject() {
		var expression = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }";
		var expected   = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }";
		assertExpressionEvaluatesTo(expression, expected);
	}

}
