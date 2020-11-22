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

import org.junit.Test;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ApplyStepsRecursiveKeyTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void recursiveKeyStepPropagatesErrors() {
		expressionErrors(CTX, "(10/0)..key");
	}

	@Test
	public void recursiveKeyStepOnUndefinedIsEmpty() {
		var expression = "undefined..key";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applyToNull() {
		var expression = "null..key";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applyToObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..key";
		var expected = "[ \"value1\", \"value2\", \"value3\" ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applyToObjectNotPresent() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..yek";
		var expected = "[ ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterArray() {
		var expression = "[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
				+ " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
				+ " |- { @..key : mock.nil} ";
		var expected = "[{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]},{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]}]]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterArrayDescend() {
		var expression = "[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
				+ " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
				+ " |- { @..key..key2 : mock.nil}";
		var expected = "[{\"key\":\"value1\",\"array1\":[{\"key\":{\"key2\":null}},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]},{\"key\":\"value1\",\"array1\":[{\"key\":\"value2\"},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]}]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterArrayEmpty() {
		var expression = "[] |- { @..key..key2 : mock.nil} ";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}
