/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License";
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

public class AttributeUnionStepImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.mockEvaluationContext();

	@Test
	public void applySlicingToNonObject() {
		var expression = "\"Otto\"['key1','key2']";
		expressionErrors(CTX, expression);
	}

	@Test
	public void applyToEmptyObject() {
		var expression = "{}['key1','key2']";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applyToObject() {
		var expression = "{ \"key1\" : null, \"key2\" : true,  \"key3\" : false }['key3','key2']";
		var expected = "[ true, false ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applyFilterToNonObject() {
		var expression = "\"Otto\" |- { @['key1','key2'] : nil }";
		var expected = "\"Otto\"";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterElementsInObject() {
		var expression = "{ \"key1\" : 1, \"key2\" : 2,  \"key3\" : 3 } |- { @['key3','key1'] : nil }";
		var expected = "{ \"key1\" : null, \"key2\" : 2,  \"key3\" : null }";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterElementsInDescend() {
		var expression = "{ \"key1\" : [1,2,3], \"key2\" : [1,2,3],  \"key3\" : [1,2,3] } |- { @['key3','key1'][2] : nil }";
		var expected = "{ \"key1\" : [1,2,null], \"key2\" : [1,2,3],  \"key3\" : [1,2,null] }";
		expressionEvaluatesTo(CTX, expression, expected);
	}
}
