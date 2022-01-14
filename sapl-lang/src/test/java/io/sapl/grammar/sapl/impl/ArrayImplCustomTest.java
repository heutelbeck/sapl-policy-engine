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

import org.junit.jupiter.api.Test;

class ArrayImplCustomTest {

	@Test
	void simpleArrayToVal() {
		var expression = "[true,false]";
		var expected   = "[true,false]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void arrayPropagatesErrors() {
		expressionErrors("[true,(1/0)]");
	}

	@Test
	void emptyArray() {
		expressionEvaluatesTo("[]", "[]");
	}

	@Test
	void dropsUndefined() {
		var expression = "[true,undefined,false,undefined]";
		var expected   = "[true,false]";
		expressionEvaluatesTo(expression, expected);
	}

}
