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

import static io.sapl.grammar.sapl.impl.util.ParserUtil.filterComponent;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.tests.MockFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyFilteringExtendedTest {

	private VariableContext variableCtx;
	private AnnotationFunctionContext functionCtx;
	private EvaluationContext ctx;

	@Before
	public void before() {
		variableCtx = new VariableContext();
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		functionCtx.loadLibrary(new MockFunctionLibrary());
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		ctx = new EvaluationContext(functionCtx, variableCtx);
	}

	@Test
	public void filterUndefined() {
		expressionErrors(ctx, "undefined |- { @.name : filter.remove }");
	}

	@Test
	public void filterError() {
		expressionErrors(ctx, "(10/0) |- { @.name : filter.remove }");
	}

	@Test
	public void noStatements() {
		var root = "{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }";
		var expression = root + " |-  { }";
		expressionEvaluatesTo(ctx, expression, root);
	}

	@Test
	public void removeKeyStepFromObject() {
		var expression = "{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" } |- { @.name : filter.remove }";
		var expected = "{ \"job\" : \"recreational surgeon\" }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeElementTwoKeyStepsDownFromObject() {
		var expression = "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : 1000000 } } "
				+ "|- { @.job.wage : filter.remove }";
		var expected = "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\" } }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeElementThreeKeyStepsDownFromObject() {
		var expression = "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"monthly\" : 1000000, \"currency\" : \"GBP\"} } } "
				+ "|- { @.job.wage.monthly : filter.remove }";
		var expected = "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"currency\" : \"GBP\"} } }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeKeyStepFromArray() {
		var expression = "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
				+ "|- { @.name : filter.remove }";
		var expected = "[ { \"job\" : \"recreational surgeon\" }, { \"job\" : \"professional perforator\"} ] ";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeNoStepsNoEach() {
		var expression = "{} |- { @ : filter.remove }";
		var expected = Val.UNDEFINED;
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeEachNoArray() {
		expressionErrors(ctx, "{} |- { each @ : filter.remove }");
	}

	@Test
	public void removeNoStepsEach() {
		var expression = "[ null, true ] |- { each @ : filter.remove }";
		var expected = "[ ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void emptyStringNoStepsNoEach() {
		var expression = "[ null, true ] |- { @ : mock.emptyString }";
		var expected = "\"\"";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void emptyStringNoStepsEach() {
		var expression = "[ null, true ] |- { each @ : mock.emptyString }";
		var expected = "[ \"\", \"\" ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void emptyStringEachNoArray() {
		expressionErrors(ctx, "[ {}, true ] |- { each @[0] : mock.emptyString }");
	}

	@Test
	public void removeResultArrayNoEach() {
		var expression = "[ null, true ] |-  { @[0] : filter.remove }";
		var expected = "[ true ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void blackenIndexInSelectedField() {
		var expression = "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
				+ "|- { @[0].job : filter.blacken }";
		var expected = "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"XXXXXXXXXXXXXXXXXXXX\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void blackenResultArrayNoEach() {
		var expression = "[ null, \"secret\", true ] |- { @[-2] : filter.blacken }";
		var expected = "[ null, \"XXXXXX\", true ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeArraySliceNegative() {
		var expression = "[ 0, 1, 2, 3, 4, 5 ] |- { @[-2:] : filter.remove }";
		var expected = "[0, 1, 2, 3 ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeArraySlicePositive() {
		var expression = "[ 0, 1, 2, 3, 4, 5 ] |- { @[2:4:1] : filter.remove }";
		var expected = "[ 0, 1, 4, 5 ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeArraySliceNegativeTo() {
		var expression = "[ 1, 2, 3, 4, 5 ] |- { @[0:-2:2] : filter.remove }";
		var expected = "[ 2, 4, 5 ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeArraySliceNegativeStep() {
		var expression = "[ 0, 1, 2, 3, 4, 5 ] |- { @[1:5:-2] : filter.remove }";
		var expected = "[ 0, 2, 4, 5 ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeAttributeUnionStep() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } |- { @[\"b\" , \"d\"] : filter.remove }";
		var expected = "{ \"a\" : 1, \"c\" : 3 }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeArrayElementInAttributeUnionStep() {
		var expression = "{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : filter.remove }";
		var expected = "{ \"a\" : [0,1,2,3], \"b\" : [0,2,3], \"c\" : [0,1,2,3], \"d\" : [0,2,3] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceWithEmptyStringArrayElementInAttributeUnionStep() {
		var expression = "{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : mock.emptyString }";
		var expected = "{ \"a\" : [0,1,2,3], \"b\" : [0,\"\",2,3], \"c\" : [0,1,2,3], \"d\" : [0,\"\",2,3] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeIndexUnionStep() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3] : filter.remove }";
		var expected = "[ [0,1,2,3], [2,1,2,3], [4,1,2,3] ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void doubleRemoveIndexUnionStep() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3][2,1] : filter.remove }";
		var expected = "[ [0,1,2,3], [1,3], [2,1,2,3], [3,3], [4,1,2,3] ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeExpressionStepArray() {
		var expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }";
		var expected = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeExpressionStepObject() {
		var expression = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }";
		var expected = "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeConditionStepFromObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @[?(@>2)] : filter.remove }";
		var expected = "{ \"a\" : 1, \"b\" : 2 }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceConditionStepFromArray() {
		var expression = "[1,2,3,4,5] |- { @[?(@>2)] : mock.emptyString }";
		var expected = "[1,2,\"\", \"\", \"\"]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceWildcardStepArray() {
		var expression = "[1,2,3,4,5] |- { @.* : mock.emptyString }";
		var expected = "[ \"\", \"\",\"\", \"\", \"\"]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceWildcardStepObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @.* : mock.emptyString }";
		var expected = "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceRecursiveWildcardStepArray() {
		var expression = "[1,2,3,4,5] |- { @..* : mock.emptyString }";
		var expected = "[ \"\", \"\",\"\", \"\", \"\"]";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceRecussiveWildcardStepObject() {
		var expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 } |- { @..* : mock.emptyString }";
		var expected = "{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void replaceRecussiveKeyStepObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..key : filter.blacken }";
		var expected = "{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" }, { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void removeRecussiveIndexStepObject() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..[0] : filter.remove }";
		var expected = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value3\" } ], \"array2\" : [ 2, 3, 4, 5 ] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void multipleFilterStatements() {
		var expression = "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
				+ "|- { @..[0] : filter.remove, @..key : filter.blacken, @.array2[-1] : filter.remove }";
		var expected = "{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 2, 3, 4 ] }";
		expressionEvaluatesTo(ctx, expression, expected);
	}

	@Test
	public void complexWithFunctionAndRelative() throws IOException {
		var root = Val.ofJson("{\"name\": \"Ben\", \"origin\": \"Berlin\"}");
		var filter = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expected = Val.ofJson("{\"name\": \"Ben from Berlin\"}");
		StepVerifier.create(filter.apply(root, ctx, root)).expectNext(expected).verifyComplete();
	}

	@Test
	public void complexWithFunctionAndRelativeArray() throws IOException {
		var root = Val.ofJson(
				"[ {\"name\": \"Ben\", \"origin\": \"Berlin\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}]");
		var filter = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expected = Val.ofJson("[{\"name\": \"Ben from Berlin\"},{ \"name\": \"Felix from Zürich\"}]");
		StepVerifier.create(filter.apply(root, ctx, root)).expectNext(expected).verifyComplete();
	}

}
