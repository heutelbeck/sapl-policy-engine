/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsError;

import org.junit.jupiter.api.Test;

class ApplyStepsConditionTest {

	@Test
	void propagatesErrors() {
		assertExpressionReturnsError("(10/0)[?(@>0)]","Division by zero");
	}

	@Test
	void onUndefinedError() {
		assertExpressionReturnsError("undefined[?(@>0)]","Type mismatch. Expected an Object or Array, but got: 'undefined'.");
	}

	@Test
	void nonObjectNonArray() {
		assertExpressionReturnsError("\"Toastbrot\"[?(@>0)]","Type mismatch. Expected an Object or Array, but got: '\"Toastbrot\"'.");
	}

	@Test
	void applyToObjectConditionNotBoolean() {
		assertExpressionEvaluatesTo("{ \"key\" : null }[?(null)]", "[]");
	}

	@Test
	void applyToArray() {
		assertExpressionEvaluatesTo("[20, 5][?(@>10)]", "[20]");
	}

	@Test
	void applyToArrayWithError() {
		assertExpressionReturnsError("[20, 10/0, 5][?(@>10)]","Division by zero");
	}

	@Test
	void applyToArrayWithErrorCondition() {
		assertExpressionReturnsError("[20,  5][?(@>(10/0)]","Division by zero");
	}

	@Test
	void applyToEmptyObject() {
		assertExpressionEvaluatesTo("{}[?(@>10)]", "[]");
	}

	@Test
	void applyToEmptyArray() {
		assertExpressionEvaluatesTo("[][?(@>10)]", "[]");
	}

	@Test
	void applyToObjectNode() {
		assertExpressionEvaluatesTo("{ \"key1\" : 20, \"key2\" : 5 }[?(@>10)]", "[20]");
	}

	@Test
	void removeConditionStepFromObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @[?(@>2)] : filter.remove }";
		var expected   = "{ \"a\" : 1, \"b\" : 2 }";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceConditionStepFromArray() {
		var expression = "[1,2,3,4,5] |- { @[?(@>2)] : mock.emptyString }";
		var expected   = "[1,2,\"\", \"\", \"\"]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterErrorPropagation() {
		assertExpressionReturnsError("[(10/0)] |- { @[?(@>2)] : mock.emptyString }","Division by zero");
	}

	@Test
	void filterNonArrayOrObject() {
		assertExpressionEvaluatesTo("null |- { @[?(@>2)] : mock.emptyString }", "null");
	}

	@Test
	void filterEmptyArray() {
		assertExpressionEvaluatesTo("[] |- { @[?(@>2)] : mock.emptyString }", "[]");
	}

	@Test
	void filterErrorInArray() {
		assertExpressionReturnsError("[1, (10/0), 3] |- { @[?(@>2)] : mock.emptyString }","Division by zero");
	}

	@Test
	void filterInArrayDescend() {
		var expression = "[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\", \"job\" : \"warmonger\" } ] "
				+ "|- { @[?(@.name == \"Willi\")].job : filter.remove }";
		var expected   = "[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\" } ]";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterInObjectDescend() {
		var expression = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
				+ "|- { @[?(\"Paul\" in @.children)]..children[1] : filter.remove }";
		var expected   = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Paul\" ] } } ";
		assertExpressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterObjectErrorInCondition() {
		var expression = "{ \"name\" : \"Otto\" } |- { @[?(10/0))] : filter.remove }";
		assertExpressionReturnsError(expression,"Division by zero");
	}

	@Test
	void filterObjectNonBoolInCondition() {
		var expression = "{ \"name\" : \"Otto\" } |- { @[?(123)] : filter.remove }";
		assertExpressionReturnsError(expression,"Type mismatch. Expected the condition expression to return a Boolean, but was '123'.");
	}

	@Test
	void filterEmptyObject() {
		assertExpressionEvaluatesTo("{} |- { @[?(@>2)] : mock.emptyString }", "{}");
	}

	@Test
	void filterErrorInCondition() {
		assertExpressionReturnsError("[10,1] |- { @[?(@>(10/0))] : mock.emptyString }","Division by zero");
	}

	@Test
	void filterNonBoolInCondition() {
		assertExpressionReturnsError("[10,1] |- { @[?(123)] : mock.emptyString }", "Type mismatch. Expected the condition expression to return a Boolean, but was '123'.");
	}

}
