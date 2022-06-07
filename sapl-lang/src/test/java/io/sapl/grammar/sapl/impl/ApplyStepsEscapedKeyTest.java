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

import io.sapl.api.interpreter.Val;

class ApplyStepsEscapedkeyTest {

	@Test
	void keyStepPropagatesErrors() {
		expressionErrors("(10/0).\"k e y\"");
	}

	@Test
	void keyStepToNonObjectUndefined() {
		expressionEvaluatesTo("true.\"k e y\"", Val.UNDEFINED);
	}

	@Test
	void keyStepToEmptyObject() {
		expressionEvaluatesTo("{}.\"k e y\"", Val.UNDEFINED);
	}

	@Test
	void keyStepToObject() {
		expressionEvaluatesTo("{\"k e y\" : true}.\"k e y\"", "true");
	}

	@Test
	void keyStepToArray() {
		var expression = "[{\"k e y\" : true},{\"k e y\": 123}].\"k e y\"";
		var expected   = "[true,123]";
		expressionEvaluatesTo(expression, expected);
	}

	// FIXME: {"k e y",123} should be rejected at parse time
	@Test
	void keyStepToArrayNoMatch() {
		var expression = "[{\"k e y\" : true},{\"k e y\": 123}].\"x\"";
		var expected   = "[]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNonObjectOrArray() {
		var expression = "\"Gudrun\" |- { @.\"k e y\" : mock.nil }";
		var expected   = "\"Gudrun\"";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterObject() {
		var expression = "{\"k e y\" : true, \"other\" : false} |- { @.\"k e y\" : mock.nil}";
		var expected   = "{\"k e y\" : null, \"other\" : false}";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterObjectDescend() {
		var expression = "{\"k e y\" : { \"k e y2\" : true}, \"other\" : false} |- { @.\"k e y\".\"k e y2\" : mock.nil}";
		var expected   = "{\"k e y\" : {\"k e y2\" : null }, \"other\" : false}";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterArray() {
		var expression = "[ {\"k e y\" : true, \"other\" : false} , false ] |- { @.\"k e y\" : mock.nil}";
		var expected   = "[ {\"k e y\" : null, \"other\" : false} , false ]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterEmptyrray() {
		var expression = "[] |- { @.\"k e y\" : mock.nil}";
		var expected   = "[]";
		expressionEvaluatesTo(expression, expected);
	}

}
