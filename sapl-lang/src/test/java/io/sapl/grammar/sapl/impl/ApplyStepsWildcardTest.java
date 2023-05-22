/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

class ApplyStepsWildcardTest {

	@Test
	void wildcardStepPropagatesErrors() {
		expressionErrors("(10/0).*");
	}

	@Test
	void wildcardStepOnOtherThanArrayOrObjectFails() {
		expressionErrors("\"\".*");
	}

	@Test
	void wildcardStepOnUndefinedFails() {
		expressionErrors("undefined.*");
	}

	@Test
	void wildcardStepOnArrayIsIdentity() {
		var expression = "[1,2,3,4,5,6,7,8,9].*";
		var expected   = "[1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void applyToObject() {
		var expression = "{\"key1\":null,\"key2\":true,\"key3\":false,\"key4\":{\"other_key\":123}}.*";
		var expected   = "[ null, true, false , { \"other_key\" : 123 } ]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceWildcardStepArray() {
		var expression = "[1,2,3,4,5] |- { @.* : mock.emptyString }";
		var expected   = "[ \"\", \"\",\"\", \"\", \"\"]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceWildcardStepObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @.* : mock.emptyString }";
		var expected   = "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceRecursiveWildcardStepArray() {
		var expression = "[1,2,3,4,5] |- { @..* : mock.emptyString }";
		var expected   = "[ \"\", \"\",\"\", \"\", \"\"]";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterNonObjectNonArray() {
		var expression = "\"Herbert\" |- { @..* : mock.emptyString }";
		var expected   = "\"Herbert\"";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void replaceRecursiveWildcardStepObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @..* : mock.emptyString }";
		var expected   = "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterInObjectDescend() {
		var expression = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
				+ "|- { @.*.partner : mock.emptyString }";
		var expected   = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } }";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterInArrayDescend() {
		var expression = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [ \"Mary\", \"Louis\", \"Paul\" ] } } "
				+ "|- { @..children[*] : mock.nil }";
		var expected   = "{ \"name\" : \"Otto\", \"family\" : { \"partner\" : \"James\", \"children\": [null,null,null] } } ";
		expressionEvaluatesTo(expression, expected);
	}

	@Test
	void filterInArrayDescend2() {
		var expression = "[ {\"a\" : 1},{\"b\" : 2}] |- { @[*].b : mock.nil }";
		var expected   = "[ {\"a\" : 1},{\"b\" : null}]";
		expressionEvaluatesTo(expression, expected);
	}

}
