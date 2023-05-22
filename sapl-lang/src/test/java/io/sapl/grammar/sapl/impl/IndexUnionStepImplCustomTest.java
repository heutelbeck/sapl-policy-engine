/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

class IndexUnionStepImplCustomTest {

	@Test
	void applyIndexUnionStepToNonArrayFails() {
		expressionErrors("(undefined)[1,2]");
	}

	@Test
	void applyToArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][0,1,-2,10,-10]";
		var expected   = "[0,1,8]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void applyToArrayOutOfBounds() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][100,-100]";
		var expected   = "[]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNegativeStepArray() {
		var expression = "\"Otto\" |- { @[1,2,3] : mock.nil }";
		var expected   = "\"Otto\"";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterElementsInArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[0,1,-2,10,-10] : mock.nil }";
		var expected   = "[null,null,2,3,4,5,6,7,null,9]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterElementsInDescend() {
		var expression = "[[0,1,2,3],[0,1,2,3],[0,1,2,3],[0,1,2,3]] |- { @[1,3][3] : mock.nil }";
		var expected   = "[[0,1,2,3],[0,1,2,null],[0,1,2,3],[0,1,2,null]]";
		expressionEvaluatesTo(expression, expected);
	}

}
