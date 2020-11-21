/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ApplyStepsExpressionTest {

	private static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void expressionStepPropagatesErrors1() {
		expressionErrors(CTX, "[][(10/0)]");
	}

	@Test
	public void expressionStepPropagatesErrors2() {
		expressionErrors(CTX, "[(10/0)][(2+2)]");
	}

	@Test
	public void expressionStepOutOfBounds1() {
		expressionErrors(CTX, "[1,2,3][(1+100)]");
	}

	@Test
	public void expressionStepOutOfBounds2() {
		expressionErrors(CTX, "[1,2,3][(1 - 100)]");
	}

	@Test
	public void expressionStepOutOfBouonds2() {
		expressionErrors(CTX, "[1,2,3][(1 - 100)]");
	}

	@Test
	public void applyExpressionStepToNonObjectNonArrayFails() {
		expressionErrors(CTX, "undefined[(1 + 1)]");
	}

	@Test
	public void expressionEvaluatesToBooleanAndFails() {
		expressionErrors(CTX, "[1,2,3][(true)]");
	}

	@Test
	public void applyToArrayWithTextualExpressionResult() {
		expressionErrors(CTX, "[0,1,2,3,4,5,6,7,8,9][(\"key\")]");
	}

	@Test
	public void applyToArrayWithNumberExpressionResult() {
		expressionEvaluatesTo(CTX, "[0,1,2,3,4,5,6,7,8,9][(2+3)]", "5");
	}

	@Test
	public void applyToObjectWithTextualResult() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true }[(\"ke\"+\"y\")]", "true");
	}

	@Test
	public void applyToObjectWithTextualResultNonExistingKey() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true }[(\"no_ke\"+\"y\")]", Val.UNDEFINED);
	}

	@Test
	public void applyToObjectWithNumericalResult() {
		expressionErrors(CTX, "{ \"key\" : true }[(5+2)]");
	}

	@Test
	public void applyToObjectWithError() {
		expressionErrors(CTX, "{ \"key\" : true }[(10/0)]");
	}

	@Test
	public void filterNonArrayNonObject() {
		expressionEvaluatesTo(CTX, "123 |- { @[(1+1)] : mock.nil }", "123");
	}

	@Test
	public void removeExpressionStepArray() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }";
		var expected = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterTypeMismatch1() {
		expressionErrors(CTX, "[ [4,1,2,3] ] |- { @[(false)] : filter.remove }");
	}

	@Test
	public void filterTypeMismatch2() {
		expressionErrors(CTX, "[ [4,1,2,3] ] |- { @[(\"a\")] : filter.remove }");
	}

	@Test
	public void filterTypeMismatch3() {
		expressionErrors(CTX, "{ \"a\": [4,1,2,3] } |- { @[(123)] : filter.remove }");
	}

	@Test
	public void removeExpressionStepObject() {
		var expression = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }";
		var expected = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}
