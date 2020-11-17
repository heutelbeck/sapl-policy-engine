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

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.tests.MockFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyFilteringSimpleTest {

	private VariableContext variableCtx;
	private FunctionContext functionCtx;
	private EvaluationContext ctx;

	@Before
	public void before() {
		variableCtx = new VariableContext();
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		functionCtx.loadLibrary(new MockFunctionLibrary());
		ctx = new EvaluationContext(functionCtx, variableCtx);
	}

	@Test
	public void filterPropagatesError() throws IOException {
		expressionErrors(ctx, "(10/0) |- filter.remove");
	}

	@Test
	public void filterUndefined() throws IOException {
		expressionErrors(ctx, "undefined |- filter.remove");
	}

	@Test
	public void removeNoEach() throws IOException {
		var expression = "{} |- filter.remove";
		var expected = Val.UNDEFINED;
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeEachNoArray() throws IOException {
		expressionErrors(ctx, "{} |- each filter.remove");
	}

	@Test
	public void removeEachArray() throws IOException {
		var expression = "[null] |- each filter.remove";
		var expected = "[]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void emptyStringNoEach() throws IOException {
		var expression = "[] |- mock.emptyString";
		var expected = "\"\"";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void emptyStringEach() throws IOException {
		var expression = "[ null, 5 ] |- each mock.emptyString(null)";
		var expected = "[ \"\", \"\" ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

}
