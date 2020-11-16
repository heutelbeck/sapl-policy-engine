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
package io.sapl.grammar.tests;

import static io.sapl.grammar.sapl.impl.util.ParserUtil.filterComponent;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

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
	public void removeNoEach() throws IOException {
		var root = Val.ofEmptyObject();
		var filter = filterComponent("filter.remove");
		var expectedResult = Val.UNDEFINED;
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeEachNoArray() throws IOException {
		var root = Val.ofEmptyObject();
		var filter = filterComponent("each filter.remove");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void removeEachArray() throws IOException {
		var root = Val.ofJson("[ null ]");
		var filter = filterComponent("each filter.remove");
		var expectedResult = Val.ofEmptyArray();
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void emptyStringNoEach() throws IOException {
		var root = Val.ofEmptyArray();
		var filter = filterComponent("mock.emptyString");
		var expectedResult = Val.of("");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void emptyStringEach() throws IOException {
		var root = Val.ofJson("[ null, 5 ]");
		var filter = filterComponent("each mock.emptyString(null)");
		var expectedResult = Val.ofJson("[ \"\", \"\" ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

}
