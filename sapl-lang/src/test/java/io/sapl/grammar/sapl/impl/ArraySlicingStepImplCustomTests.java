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
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsError;

import org.junit.jupiter.api.Test;

class ArraySlicingStepImplCustomTests {

	@Test
	void slicingPropagatesErrors() {
		assertExpressionReturnsError("(1/0)[0:1]","Division by zero");
	}

	@Test
	void applySlicingToNoArray() {
		assertExpressionReturnsError("\"abc\"[0:1]", "Type mismatch. Expected an Array, but got: '\"abc\"'.");
	}

	@Test
	void defaultsToIdentity() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][:]";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void useCaseTestTwoNull() {
		var expression = "[1,2,3,4,5][2:]";
		var expected   = "[3,4,5]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void negativeToTest() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][7:-1]";
		var expected   = "[7,8]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-3:9]";
		var expected   = "[7,8]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayWithFromGreaterThanToReturnsEmptyArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][4:1]";
		var expected   = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithoutTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][7:]";
		var expected   = "[7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithoutFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][:3]";
		var expected   = "[0,1,2]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-3:]";
		var expected   = "[7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithNegativeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :-1]";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-2:6:-1]";
		var expected   = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-2:-5:-1]";
		var expected   = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][1:5:-1]";
		var expected   = "[1,2,3,4]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingStepZeroErrors() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][1:5:0]";
		assertExpressionReturnsError(expression,"Step must not be zero.");
	}

	@Test
	void applySlicingToResultArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][3:6]";
		var expected   = "[3,4,5]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayWithThreeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :3]";
		var expected   = "[0,3,6,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void applySlicingToArrayWithNegativeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :-3]";
		var expected   = "[1,4,7]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterDefaultsToIdentity() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[:] : mock.nil }";
		var expected   = "[null,null,null,null,null,null,null,null,null,null]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterDefaultsToIdentityDescendStep() {
		var expression = "[[10,11,12,13,14],0,1,2,3,4,5,6,7,8,9] |- { @[:][-2:] : mock.nil }";
		var expected   = "[[10,11,12,null,null],0,1,2,3,4,5,6,7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterErrorOnZeroStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[: :0] : mock.nil }";
		assertExpressionReturnsError(expression,"Step must not be zero.");
	}

	@Test
	void filterEmptyArray() {
		var expression = "[] |- { @[:] : mock.nil }";
		var expected   = "[]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNegativeStepArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[: :-2] : mock.nil }";
		var expected   = "[null,1,null,3,null,5,null,7,null,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNegativeTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[:-2] : mock.nil }";
		var expected   = "[null,null,null,null,null,null,null,null,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

}
