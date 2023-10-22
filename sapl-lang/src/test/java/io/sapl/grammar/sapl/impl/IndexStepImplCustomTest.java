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

class IndexStepImplCustomTest {

	@Test
	void applyIndexStepToNonArrayFails() {
		assertExpressionReturnsErrors("undefined[0]");
	}

	@Test
	void applyPositiveExistingToArrayNode() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][5]", "5");
	}

	@Test
	void applyPositiveExistingToArrayNodeUpperEdge() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][9]", "9");
	}

	@Test
	void applyPositiveExistingToArrayNodeLowerEdge() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][0]", "0");
	}

	@Test
	void applyPositiveExistingToArrayNodeLowerEdgeNegative() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-1]", "9");
	}

	@Test
	void applyPositiveExistingToArrayNodeUpperEdgeNegative() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-10]", "0");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayNode1() {
		assertExpressionReturnsErrors("[0,1,2,3,4,5,6,7,8,9][100]");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayNodeUpperEdge() {
		assertExpressionReturnsErrors("[0,1,2,3,4,5,6,7,8,9][10]");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayLowerUpperEdgeNegative() {
		assertExpressionReturnsErrors("[0,1,2,3,4,5,6,7,8,9][-11]");
	}

	@Test
	void applyNegativeExistingToArrayNode() {
		assertExpressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-2]", "8");

	}

	@Test
	void applyNegativeOutOfBoundsToArrayNode() {
		assertExpressionReturnsErrors("[0,1,2,3,4,5,6,7,8,9][-12]");
	}

	@Test
	void filterOutOfBounds1() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[-12] : mock.nil }";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterElementsInDescend() {
		var expression = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[3][2] : mock.nil }";
		var expected   = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,null,3]]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterOutOfBounds2() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[12] : mock.nil }";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNonArray() {
		assertExpressionEvaluatesTo("666 |- { @[2] : mock.nil }", "666");
	}

}
