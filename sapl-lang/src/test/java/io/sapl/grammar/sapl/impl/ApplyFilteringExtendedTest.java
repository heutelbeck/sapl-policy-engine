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
	public void filterUndefined() throws IOException {
		var root = Val.UNDEFINED;
		var filter = filterComponent("{ @.name : filter.remove }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void filterError() throws IOException {
		var root = Val.error("ERROR");
		var filter = filterComponent("{ @.name : filter.remove }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(root).verifyComplete();
	}

	@Test
	public void noStatements() throws IOException {
		var root = Val.ofJson("{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }");
		var filter = filterComponent("{ }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(root).verifyComplete();
	}

	@Test
	public void removeKeyStepFromObject() throws IOException {
		var root = Val.ofJson("{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }");
		var filter = filterComponent("{ @.name : filter.remove }");
		var expectedValue = Val.ofJson("{ \"job\" : \"recreational surgeon\" }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void removeElementTwoKeyStepsDownFromObject() throws IOException {
		var root = Val.ofJson(
				"{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : 1000000 } }");
		var filter = filterComponent("{ @.job.wage : filter.remove }");
		var expectedValue = Val
				.ofJson("{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\" } }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void removeElementThreeKeyStepsDownFromObject() throws IOException {
		var root = Val.ofJson(
				"{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"monthly\" : 1000000, \"currency\" : \"GBP\"} } }");
		var filter = filterComponent("{ @.job.wage.monthly : filter.remove }");
		var expectedValue = Val.ofJson(
				"{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\",  \"wage\" : { \"currency\" : \"GBP\"} } }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void removeKeyStepFromArray() throws IOException {
		var root = Val.ofJson(
				"[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ]");
		var filter = filterComponent("{ @.name : filter.remove }");
		var expectedValue = Val
				.ofJson("[ { \"job\" : \"recreational surgeon\" }, { \"job\" : \"professional perforator\"} ] ");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void removeNoStepsNoEach() throws IOException {
		var root = Val.ofEmptyObject();
		var filter = filterComponent("{ @ : filter.remove }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(Val.UNDEFINED).verifyComplete();
	}

	@Test
	public void removeEachNoArray() throws IOException {
		var root = Val.ofEmptyObject();
		var filter = filterComponent("{ each @ : filter.remove }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void removeNoStepsEach() throws IOException {
		var root = Val.ofJson("[ null, true ]");
		var filter = filterComponent("{ each @ : filter.remove }");
		var expectedResult = Val.ofEmptyArray();
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void emptyStringNoStepsNoEach() throws IOException {
		var root = Val.ofJson("[ null, true ]");
		var filter = filterComponent("{ @ : mock.emptyString }");
		var expectedResult = Val.of("");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void emptyStringNoStepsEach() throws IOException {
		var root = Val.ofJson("[ null, true ]");
		var filter = filterComponent("{ each @ : mock.emptyString }");
		var expectedResult = Val.ofJson("[ \"\", \"\" ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void emptyStringEachNoArray() throws IOException {
		var root = Val.ofJson("[ {}, true ]");
		var filter = filterComponent("{ each @[0] : mock.emptyString }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void removeResultArrayNoEach() throws IOException {
		var root = Val.ofJson("[ null, true ]");
		var filter = filterComponent("{ @[0] : filter.remove }");
		var expectedResult = Val.ofJson("[ true ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void blackenIndexInSelectedField() throws IOException {
		var root = Val.ofJson(
				"[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ]");
		var filter = filterComponent("{ @[0].job : filter.blacken }");
		var expectedValue = Val.ofJson(
				"[ { \"name\" : \"Jack the Ripper\", \"job\" : \"XXXXXXXXXXXXXXXXXXXX\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void blackenResultArrayNoEach() throws IOException {
		var root = Val.ofJson("[ null, \"secret\", true ]");
		var filter = filterComponent("{ @[-2] : filter.blacken }");
		var expectedResult = Val.ofJson("[ null, \"XXXXXX\", true ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeArraySliceNegative() throws IOException {
		var root = Val.ofJson("[ 0, 1, 2, 3, 4, 5 ]");
		var filter = filterComponent("{ @[-2:] : filter.remove }");
		var expectedResult = Val.ofJson("[0, 1, 2, 3 ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeArraySlicePositive() throws IOException {
		var root = Val.ofJson("[ 0, 1, 2, 3, 4, 5 ]");
		var filter = filterComponent("{ @[2:4:1] : filter.remove }");
		var expectedResult = Val.ofJson("[ 0, 1, 4, 5 ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeArraySliceNegativeTo() throws IOException {
		var root = Val.ofJson("[ 1, 2, 3, 4, 5 ]");
		var filter = filterComponent("{ @[0:-2:2] : filter.remove }");
		var expectedResult = Val.ofJson("[ 2, 4, 5 ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeArraySliceNegativeStep() throws IOException {
		var root = Val.ofJson("[ 0, 1, 2, 3, 4, 5 ]");
		var filter = filterComponent("{ @[1:5:-2] : filter.remove }");
		var expectedResult = Val.ofJson("[ 0, 2, 4, 5 ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeAttributeUnionStep() throws IOException {
		var root = Val.ofJson("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 }");
		var filter = filterComponent("{ @[\"b\" , \"d\"] : filter.remove }");
		var expectedResult = Val.ofJson("{ \"a\" : 1, \"c\" : 3 }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeArrayElementInAttributeUnionStep() throws IOException {
		var root = Val.ofJson("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] }");
		var filter = filterComponent("{ @[\"b\" , \"d\"][1] : filter.remove }");
		var expectedResult = Val.ofJson("{ \"a\" : [0,1,2,3], \"b\" : [0,2,3], \"c\" : [0,1,2,3], \"d\" : [0,2,3] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceWithEmptyStringArrayElementInAttributeUnionStep() throws IOException {
		var root = Val.ofJson("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] }");
		var filter = filterComponent("{ @[\"b\" , \"d\"][1] : mock.emptyString }");
		var expectedResult = Val
				.ofJson("{ \"a\" : [0,1,2,3], \"b\" : [0,\"\",2,3], \"c\" : [0,1,2,3], \"d\" : [0,\"\",2,3] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeIndexUnionStep() throws IOException {
		var root = Val.ofJson("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ]");
		var filter = filterComponent("{ @[1,3] : filter.remove }");
		var expectedResult = Val.ofJson("[ [0,1,2,3], [2,1,2,3], [4,1,2,3] ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void doubleRemoveIndexUnionStep() throws IOException {
		var root = Val.ofJson("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ]");
		var filter = filterComponent("{ @[1,3][2,1] : filter.remove }");
		var expectedResult = Val.ofJson("[ [0,1,2,3], [1,3], [2,1,2,3], [3,3], [4,1,2,3] ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeExpressionStepArray() throws IOException {
		var root = Val.ofJson("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ]");
		var filter = filterComponent("{ @[(1+2)] : filter.remove }");
		var expectedResult = Val.ofJson("[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeExpressionStepObject() throws IOException {
		var root = Val.ofJson("{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] }");
		var filter = filterComponent("{ @[(\"c\"+\"b\")] : filter.remove }");
		var expectedResult = Val.ofJson("{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeConditionStepFromObject() throws IOException {
		var root = Val.ofJson("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 }");
		var filter = filterComponent("{ @[?(@>2)] : filter.remove }");
		var expectedResult = Val.ofJson("{ \"a\" : 1, \"b\" : 2 }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceConditionStepFromArray() throws IOException {
		var root = Val.ofJson("[1,2,3,4,5]");
		var filter = filterComponent("{ @[?(@>2)] : mock.emptyString }");
		var expectedResult = Val.ofJson("[1,2,\"\", \"\", \"\"]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceWildcardStepArray() throws IOException {
		var root = Val.ofJson("[1,2,3,4,5]");
		var filter = filterComponent("{ @.* : mock.emptyString }");
		var expectedResult = Val.ofJson("[ \"\", \"\",\"\", \"\", \"\"]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceWildcardStepObject() throws IOException {
		var root = Val.ofJson("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 }");
		var filter = filterComponent("{ @.* : mock.emptyString }");
		var expectedResult = Val.ofJson("{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceRecursiveWildcardStepArray() throws IOException {
		var root = Val.ofJson("[1,2,3,4,5]");
		var filter = filterComponent("{ @..* : mock.emptyString }");
		var expectedResult = Val.ofJson("[ \"\", \"\",\"\", \"\", \"\"]");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceRecussiveWildcardStepObject() throws IOException {
		var root = Val.ofJson("{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 , \"e\" : 5 }");
		var filter = filterComponent("{ @..* : mock.emptyString }");
		var expectedResult = Val.ofJson("{ \"a\" : \"\", \"b\" : \"\", \"c\" : \"\", \"d\" : \"\" , \"e\" : \"\" }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void replaceRecussiveKeyStepObject() throws IOException {
		var root = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }");
		var filter = filterComponent("{ @..key : filter.blacken }");
		var expectedResult = Val.ofJson(
				"{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" }, { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void removeRecussiveIndexStepObject() throws IOException {
		var root = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }");
		var filter = filterComponent("{ @..[0] : filter.remove }");
		var expectedResult = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value3\" } ], \"array2\" : [ 2, 3, 4, 5 ] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void multipleFilterStatements() throws IOException {
		var root = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] }");
		var filter = filterComponent(
				"{ @..[0] : filter.remove, @..key : filter.blacken, @.array2[-1] : filter.remove }");
		var expectedResult = Val.ofJson(
				"{ \"key\" : \"XXXXXX\", \"array1\" : [ { \"key\" : \"XXXXXX\" } ], \"array2\" : [ 2, 3, 4 ] }");
		StepVerifier.create(filter.apply(root, ctx, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void complexWithFunctionAndRelative() throws IOException {
		var root = Val.ofJson("{\"name\": \"Ben\", \"origin\": \"Berlin\"}");
		var filter = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expectedResult = Val.ofJson("{\"name\": \"Ben from Berlin\"}");
		StepVerifier.create(filter.apply(root, ctx, root)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void complexWithFunctionAndRelativeArray() throws IOException {
		var root = Val.ofJson(
				"[ {\"name\": \"Ben\", \"origin\": \"Berlin\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}]");
		var filter = filterComponent("{@.name : simple.append(\" from \", @.origin), @.origin : filter.remove}");
		var expectedResult = Val.ofJson("[{\"name\": \"Ben from Berlin\"},{ \"name\": \"Felix from Zürich\"}]");
		StepVerifier.create(filter.apply(root, ctx, root)).expectNext(expectedResult).verifyComplete();
	}

}
