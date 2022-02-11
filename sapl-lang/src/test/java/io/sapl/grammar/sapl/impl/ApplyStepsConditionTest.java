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

class ApplyStepsConditionTest {

	@Test
	void propagatesErrors() {
		expressionErrors("(10/0)[?(@>0)]");
	}

	@Test
	void onUndefinedError() {
		expressionErrors("undefined[?(@>0)]");
	}

	@Test
	void nonObjectNonArray() {
		expressionErrors("\"Toastbrot\"[?(@>0)]");
	}

	@Test
	void applyToObjectConditionNotBoolean() {
		expressionEvaluatesTo("{ \"key\" : null }[?(null)]", "[]");
	}

	@Test
	void applyToArray() {
		expressionEvaluatesTo("[20, 5][?(@>10)]", "[20]");
	}

	@Test
	void applyToArrayWithError() {
		expressionErrors("[20, (10/0), 5][?(@>10)]");
	}

	@Test
	void applyToArrayWithErrorCondition() {
		expressionErrors("[20,  5][?(@>(10/0)]");
	}

	@Test
	void applyToEmptyObject() {
		expressionEvaluatesTo("{}[?(@>10)]", "[]");
	}

	@Test
	void applyToEmptyArray() {
		expressionEvaluatesTo("[][?(@>10)]", "[]");
	}

	@Test
	void applyToObjectNode() {
		expressionEvaluatesTo("{ \"key1\" : 20, \"key2\" : 5 }[?(@>10)]", "[20]");
	}

	@Test
	void removeConditionStepFromObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @[?(@>2)] : filter.remove }";
		var expected   = "{ \"a\" : 1, \"b\" : 2 }";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceConditionStepFromArray() {
		var expression = "[1,2,3,4,5] |- { @[?(@>2)] : mock.emptyString }";
		var expected   = "[1,2,\"\", \"\", \"\"]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterErrorPropagation() {
		expressionErrors("[(10/0)] |- { @[?(@>2)] : mock.emptyString }");
	}

	@Test
	void filterNonArrayOrObject() {
		expressionEvaluatesTo("null |- { @[?(@>2)] : mock.emptyString }", "null");
	}

	@Test
	void filterEmptyArray() {
		expressionEvaluatesTo("[] |- { @[?(@>2)] : mock.emptyString }", "[]");
	}

	@Test
	void filterErrorInArray() {
		expressionErrors("[1, (10/0), 3] |- { @[?(@>2)] : mock.emptyString }");
	}

	@Test
	void filterInArrayDescend() {
		var expression = "[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\", \"job\" : \"warmonger\" } ] "
				+ "|- { @[?(@.name == \"Willi\")].job : filter.remove }";
		var expected   = "[ { \"name\" : \"Otto\", \"job\" : \"carpenter\" }, { \"name\" : \"Willi\" } ]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterInObjectDescend() {
		var expression = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
				+ "|- { @[?(\"Paul\" in @.children)]..children[1] : filter.remove }";
		var expected   = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Paul\" ] } } ";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterObjectErrorInCondition() {
		var expression = "{ \"name\" : \"Otto\" } |- { @[?(10/0))] : filter.remove }";
		expressionErrors(expression);
	}

	@Test
	void filterObjectNonBoolInCondition() {
		var expression = "{ \"name\" : \"Otto\" } |- { @[?(123)] : filter.remove }";
		expressionErrors(expression);
	}

	@Test
	void filterEmptyObject() {
		expressionEvaluatesTo("{} |- { @[?(@>2)] : mock.emptyString }", "{}");
	}

	@Test
	void filterErrorInCondition() {
		expressionErrors("[10,1] |- { @[?(@>(10/0))] : mock.emptyString }");
	}

	@Test
	void filterNonBoolInCondition() {
		expressionErrors("[10,1] |- { @[?(123)] : mock.emptyString }");
	}

}
