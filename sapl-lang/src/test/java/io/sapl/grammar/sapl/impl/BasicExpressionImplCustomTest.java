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

import org.junit.jupiter.api.Test;

class BasicExpressionImplCustomTest {

	@Test
	void basicExpressionWithStep() {
		expressionEvaluatesTo("[ null ].[0]", "null");
	}

	@Test
	void basicExpressionWithFilter() {
		expressionEvaluatesTo("null |- mock.emptyString", "\"\"");
	}

	@Test
	void subtemplateNoArray() {
		expressionEvaluatesTo("null :: { \"name\" : @ }", "{ \"name\" : null }");
	}

	@Test
	void subtemplateArray() {
		expressionEvaluatesTo("[true, false] :: null", "[ null,null ]");
	}

	@Test
	void subtemplateEmptyArray() {
		expressionEvaluatesTo("[] :: null", "[]");
	}

}
