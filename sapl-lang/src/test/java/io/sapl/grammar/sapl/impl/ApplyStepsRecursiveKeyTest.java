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

import java.io.IOException;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;

public class ApplyStepsRecursiveKeyTest {

	EvaluationContext ctx = MockUtil.mockEvaluationContext();

	@Test
	public void recursiveKeyStepPropagatesErrors() throws IOException {
		var expression = ParserUtil.expression("(10/0)..key");
		expressionErrors(ctx,expression);
	}

	@Test
	public void recursiveKeyStepOnUndefinedIsEmpty() throws IOException {
		var expression = ParserUtil.expression("undefined..key");
		var expected = Val.ofEmptyArray();
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void applyToNull() throws IOException {
		var expression = ParserUtil.expression("null..key");
		var expected = Val.ofEmptyArray();
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void applyToObject() throws IOException {
		var expression = ParserUtil.expression(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..key");
		var expected = Val.ofJson("[ \"value1\", \"value2\", \"value3\" ]");
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void applyToObjectNotPresent() throws IOException {
		var expression = ParserUtil.expression(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}..yek");
		var expected = Val.ofJson("[ ]");
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void filterArray() throws IOException {
		var expression = ParserUtil.expression(
				"[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
						+ " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
						+ " |- { @..key : nil} ");
		var expected = Val.ofJson(
				"[{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]},{\"key\":null,\"array1\":[{\"key\":null},{\"key\":null}],\"array2\":[1,2,3,4,5]}]]");
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void filterArrayDescend() throws IOException {
		var expression = ParserUtil.expression(
				"[ { \"key\" : \"value1\", \"array1\" : [ { \"key\" : { \"key2\": \"value2\" } }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}, "
						+ " { \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]} ]"
						+ " |- { @..key..key2 : nil} ");
		var expected = Val.ofJson(
				"[{\"key\":\"value1\",\"array1\":[{\"key\":{\"key2\":null}},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]},{\"key\":\"value1\",\"array1\":[{\"key\":\"value2\"},{\"key\":\"value3\"}],\"array2\":[1,2,3,4,5]}]");
		expressionEvaluatesTo(ctx,expression,expected);
	}

	@Test
	public void filterArrayEmpty() throws IOException {
		var expression = ParserUtil.expression("[] |- { @..key..key2 : nil} ");
		var expected = Val.ofJson("[]");
		expressionEvaluatesTo(ctx,expression,expected);
	}

}
