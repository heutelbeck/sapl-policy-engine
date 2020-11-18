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

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class IndexStepImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.mockEvaluationContext();

	@Test
	public void applyIndexStepToNonArrayFails() {
		expressionErrors(CTX, "undefined[0]");
	}

	@Test
	public void applyPositiveExistingToArrayNode() {
		expressionEvaluatesTo(CTX, "[0,1,2,3,4,5,6,7,8,9][5]", "5");
	}

	@Test
	public void applyPositiveOutOfBoundsToArrayNode() {
		expressionErrors(CTX, "[0,1,2,3,4,5,6,7,8,9][100]");
	}

	@Test
	public void applyNegativeExistingToArrayNode() {
		expressionEvaluatesTo(CTX, "[0,1,2,3,4,5,6,7,8,9][-2]", "8");

	}

	@Test
	public void applyNegativeOutOfBoundsToArrayNode() {
		expressionErrors(CTX, "[0,1,2,3,4,5,6,7,8,9][-12]");
	}

	@Test
	public void filterOutOfBounds1() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[-12] : nil }";
		var expected = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterElementsInDescend() {
		var expression = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[3][2] : nil }";
		var expected = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,null,3]]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterOutOfBounds2() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[12] : nil }";
		var expected = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterNonArray() {
		expressionEvaluatesTo(CTX, "666 |- { @[2] : nil }", "666");
	}

}
