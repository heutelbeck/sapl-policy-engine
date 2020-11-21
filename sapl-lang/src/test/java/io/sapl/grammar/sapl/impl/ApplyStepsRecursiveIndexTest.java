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

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ApplyStepsRecursiveIndexTest {

	private static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void recursiveIndexStepPropagatesErrors() {
		expressionErrors(CTX, "(10/0)..[5]");
	}

	@Test
	public void recursiveIndexStepOnUndefinedEmpty() {
		expressionEvaluatesTo(CTX, "undefined..[2]", "[]");
	}

	@Test
	public void applyToNull() {
		expressionEvaluatesTo(CTX, "null..[2]", "[]");
	}

	@Test
	public void applyIndex1() {
		expressionEvaluatesTo(CTX, "[ [1,2,3], [4,5,6,7] ]..[1]", "[[4,5,6,7],2,5]");
	}

	@Test
	public void applyIndex2() {
		expressionEvaluatesTo(CTX, "[ [1,2,3], [4,5,6,7] ]..[2]", "[3,6]");
	}

	@Test
	public void applyIndex3() {
		expressionEvaluatesTo(CTX, "[ [1,2,3], [4,5,6,7] ]..[-1]", "[[4,5,6,7],3,7]");
	}

	@Test
	public void applyIndex4() {
		expressionEvaluatesTo(CTX, "[ [1,2,3], [4,5,6,7] ]..[-4]", "[4]");
	}

	@Test
	public void filterApplyIndex() {
		expressionEvaluatesTo(CTX, "[ [1,2,3], [4,5,6,7] ] |- { @..[-4] : mock.nil }", "[ [1,2,3], [null,5,6,7] ]");
	}

	@Test
	public void applyToObject() throws JsonProcessingException {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..[0]";
		var expected = "[ { \"key\" : \"value2\" }, 1 ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void removeRecussiveIndexStepObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..[0] : filter.remove }";
		var expected = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value3\" } ], \"array2\" : [ 2, 3, 4, 5 ] }";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void removeRecussiveIndexStepObjectDescend() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ [ 1,2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [1,2,3], 2, 3, 4, 5 ] } "
				+ "|- { @..[0][0] : filter.remove }";
		var expected = "{ \"key\" : \"value1\", \"array1\" : [ [ 2,3 ], { \"key\" : \"value3\" } ], \"array2\" : [ [2,3], 2, 3, 4, 5 ] }";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}
