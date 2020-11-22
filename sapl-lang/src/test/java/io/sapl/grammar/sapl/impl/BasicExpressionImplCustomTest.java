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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.Test;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class BasicExpressionImplCustomTest {

	private static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void basicExpressionWithStep() {
		expressionEvaluatesTo(CTX, "[ null ].[0]", "null");
	}

	@Test
	public void basicExpressionWithFilter() {
		expressionEvaluatesTo(CTX, "null |- mock.emptyString", "\"\"");
	}

	@Test
	public void subtemplateNoArray() {
		expressionEvaluatesTo(CTX, "null :: { \"name\" : @ }", "{ \"name\" : null }");
	}

	@Test
	public void subtemplateArray() {
		expressionEvaluatesTo(CTX, "[true, false] :: null", "[ null,null ]");
	}

	@Test
	public void subtemplateEmptyArray() {
		expressionEvaluatesTo(CTX, "[] :: null", "[]");
	}

}
