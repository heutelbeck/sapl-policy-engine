/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

class IndexStepImplCustomTest {

	@Test
	void applyIndexStepToNonArrayFails() {
		expressionErrors("undefined[0]");
	}

	@Test
	void applyPositiveExistingToArrayNode() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][5]", "5");
	}

	@Test
	void applyPositiveExistingToArrayNodeUpperEdge() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][9]", "9");
	}

	@Test
	void applyPositiveExistingToArrayNodeLowerEdge() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][0]", "0");
	}

	@Test
	void applyPositiveExistingToArrayNodeLowerEdgeNegative() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-1]", "9");
	}

	@Test
	void applyPositiveExistingToArrayNodeOpperEdgeNegative() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-10]", "0");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayNode1() {
		expressionErrors("[0,1,2,3,4,5,6,7,8,9][100]");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayNodeUpperEdge() {
		expressionErrors("[0,1,2,3,4,5,6,7,8,9][10]");
	}

	@Test
	void applyPositiveOutOfBoundsToArrayLowerUpperEdgeNegative() {
		expressionErrors("[0,1,2,3,4,5,6,7,8,9][-11]");
	}

	@Test
	void applyNegativeExistingToArrayNode() {
		expressionEvaluatesTo("[0,1,2,3,4,5,6,7,8,9][-2]", "8");

	}

	@Test
	void applyNegativeOutOfBoundsToArrayNode() {
		expressionErrors("[0,1,2,3,4,5,6,7,8,9][-12]");
	}

	@Test
	void filterOutOfBounds1() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[-12] : mock.nil }";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterElementsInDescend() {
		var expression = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[3][2] : mock.nil }";
		var expected   = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,null,3]]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterOutOfBounds2() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[12] : mock.nil }";
		var expected   = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNonArray() {
		expressionEvaluatesTo("666 |- { @[2] : mock.nil }", "666");
	}

}
