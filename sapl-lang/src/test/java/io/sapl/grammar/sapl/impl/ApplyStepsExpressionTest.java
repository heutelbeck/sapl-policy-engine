/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class ApplyStepsExpressionTest {

	@Test
	void expressionStepPropagatesErrors1() {
		expressionErrors("[][(10/0)]");
	}

	@Test
	void expressionStepPropagatesErrors2() {
		expressionErrors("[(10/0)][(2+2)]");
	}

	@Test
	void expressionStepOutOfBounds1() {
		expressionErrors("[1,2,3][(1+100)]");
	}

	@Test
	void expressionStepOutOfBounds2() {
		expressionErrors("[1,2,3][(1 - 100)]");
	}

	@Test
	void expressionStepOutOfBouonds2() {
		expressionErrors("[1,2,3][(1 - 100)]");
	}

	@Test
	void applyExpressionStepToNonObjectNonArrayFails() {
		expressionErrors("undefined[(1 + 1)]");
	}

	@Test
	void expressionEvaluatesToBooleanAndFails() {
		expressionErrors("[1,2,3][(true)]");
	}

	@Test
	void applyToArrayWithTextualExpressionResult() {
		expressionErrors("[0,1,2,3,4,5,6,7,8,9][(\"key\")]");
	}

	@Test
	void applyToArrayWithNumberExpressionResult() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][(2+3)]", "5");
	}

	@Test
	void applyToObjectWithTextualResult() {
		expressionEvaluatesTo("{ \"key\" : true }[(\"ke\"+\"y\")]", "true");
	}

	@Test
	void applyToObjectWithTextualResultNonExistingKey() {
		expressionEvaluatesTo("{ \"key\" : true }[(\"no_ke\"+\"y\")]", Val.UNDEFINED);
	}

	@Test
	void applyToObjectWithNumericalResult() {
		expressionErrors("{ \"key\" : true }[(5+2)]");
	}

	@Test
	void applyToObjectWithError() {
		expressionErrors("{ \"key\" : true }[(10/0)]");
	}

	@Test
	void filterNonArrayNonObject() {
		expressionEvaluatesTo("123 |- { @[(1+1)] : mock.nil }", "123");
	}

	@Test
	void removeExpressionStepArray() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }";
		var expected   = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterTypeMismatch1() {
		expressionErrors("[ [4,1,2,3] ] |- { @[(false)] : filter.remove }");
	}

	@Test
	void filterTypeMismatch2() {
		expressionErrors("[ [4,1,2,3] ] |- { @[(\"a\")] : filter.remove }");
	}

	@Test
	void filterTypeMismatch3() {
		expressionErrors("{ \"a\": [4,1,2,3] } |- { @[(123)] : filter.remove }");
	}

	@Test
	void removeExpressionStepObject() {
		var expression = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }";
		var expected   = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }";
		expressionEvaluatesTo(expression, expected);
	}

}
