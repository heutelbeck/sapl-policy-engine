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
package io.sapl.interpreter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.TestPIP;
import reactor.test.StepVerifier;

class DefaultSAPLInterpreterTest {

	private static final String AUTHZ_SUBSCRIPTION_JSON = "{ " + "\"subject\" : { " + "\"id\" : \"1234\","
			+ "\"organizationId\" : \"5678\"," + "\"isActive\" : true," + "\"granted_authorities\" : { "
			+ "\"roles\"  : [ \"USER\", \"ACCOUNTANT\" ], " + "\"groups\" : [ \"OPERATORS\", \"DEVELOPERS\" ] " + " }"
			+ " }," + "\"action\" : { " + "\"verb\" : \"withdraw_funds\", " + "\"parameters\" : [ 200.00 ]" + "},"
			+ "\"resource\" : { " + "\"url\" : \"http://api.bank.com/accounts/12345\"," + "\"id\" : \"9012\","
			+ "\"emptyArray\" : []," + "\"textArray\" : [ \"one\", \"two\" ]," + "\"emptyObject\" : {},"
			+ "\"objectArray\" : [ {\"id\" : \"1\", \"name\" : \"one\"}, {\"id\" : \"2\", \"name\" : \"two\"} ] " + "},"
			+ "\"environment\" : { " + "\"ipAddress\" : \"10.10.10.254\"," + "\"year\" : 2016" + "}" + " }";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static AuthorizationSubscription authzSubscription;

	private static AnnotationAttributeContext attributeCtx;

	private static AnnotationFunctionContext functionCtx;

	private static Map<String, JsonNode> variables;

	@BeforeAll
	static void beforeAll() throws JsonProcessingException, InitializationException {
		authzSubscription = MAPPER.readValue(AUTHZ_SUBSCRIPTION_JSON, AuthorizationSubscription.class);
		attributeCtx      = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TestPIP());
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		functionCtx.loadLibrary(new StandardFunctionLibrary());
		variables = new HashMap<String, JsonNode>();

	}

	@Test
	void parseTest() {
		var policyDocument = "policy \"test\" permit";
		INTERPRETER.parse(policyDocument);
	}

	@Test
	void parseTestValidationFailsOnLazyBooleanOperatorsInTarget() {
		var policyDocument = "policy \"test\"  permit true && false";
		assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDocument));
	}

	@Test
	void brokenInputStreamTest() {
		var brokenInputStream = mock(InputStream.class);
		assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(brokenInputStream));
	}

	@Test
	void parseTestWithError() {
		var policyDocument = "xyz";
		assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDocument));
	}

	@Test
	void analyzePolicySet() {
		var policyDefinition = "set \"test\" deny-overrides policy \"xx\" permit";
		var expected         = new DocumentAnalysisResult(true, "test", DocumentType.POLICY_SET, "");
		assertThat(INTERPRETER.analyze(policyDefinition), is(expected));
	}

	@Test
	void analyzePolicy() {
		var policyDefinition = "policy \"test\" permit";
		var expected         = new DocumentAnalysisResult(true, "test", DocumentType.POLICY, "");
		assertThat(INTERPRETER.analyze(policyDefinition), is(expected));
	}

	@Test
	void analyzeException() {
		assertThat(INTERPRETER.analyze("xyz").isValid(), is(false));
	}

	@Test
	void permitAll() {
		var policyDefinition = "policy \"test\" permit";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void denyAll() {
		var policyDefinition = "policy \"test\" deny";
		var expected         = AuthorizationDecision.DENY;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void permitFalse() {
		var policyDefinition = "policy \"test\" permit false";
		var expected         = AuthorizationDecision.NOT_APPLICABLE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void permitParseError() {
		var policyDefinition = "--- policy \"test\" permit ---";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void targetNotBoolean() {
		var policyDefinition = "policy \"test\" permit 20";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void syntaxError() {
		var policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
		assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDefinition));
	}

	@Test
	void processParsedEmptyArray() {
		var policyDefinition = "policy \"test\" permit resource.emptyArray == []";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void processParsedEmptyObject() {
		var policyDefinition = "policy \"test\" permit resource.emptyObject == {}";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void evaluateWorkingBodyTrue() {
		var policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void evaluateWorkingBodyFalse() {
		var policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
		var expected         = AuthorizationDecision.NOT_APPLICABLE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void evaluateWorkingBodyError() {
		var policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void echoAttributeFinder() {
		var policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void attributeFinderInTarget() {
		var policyDefinition = "policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void attributeFinderWithArgumentsTest() {
		var policyDefinition = "policy \"test\" permit where var variable = \"hello\"; variable.<sapl.pip.test.echoRepeat(2)> == (variable + variable);";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void bodyStatementNotBoolean() {
		var policyDefinition = "policy \"test\" permit where null;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void variableRedefinition() {
		var policyDefinition = "policy \"test\" permit where var test = null; var test = 2; test == 2;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void unboundVariable() {
		var policyDefinition = "policy \"test\" permit where variable;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void functionCall() {
		var policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void functionCallImport() {
		var policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void functionCallError() {
		var policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where undefined.key == undefined;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where undefined..key == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepOnString() {
		var policyDefinition = "policy \"test\" permit where \"foo\".key == undefined;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepOnString() {
		var policyDefinition = "policy \"test\" permit where \"foo\"..key == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepWithUnknownKey() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": 1}.key == undefined;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepWithUnknownKey() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": 1}..key == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepWithKnownKey() {
		var policyDefinition = "policy \"test\" permit where {\"key\": 1}.key == 1;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepWithKnownKey() {
		var policyDefinition = "policy \"test\" permit where {\"key\": 1}..key == [1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepWithKnownKeyInChildObject() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.key == undefined;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepWithKnownKeyInChildObject() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..key == [1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void keyStepWithKnownKeyInParentAndChildObject() {
		var policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}.key == {\"key\": 1};";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepWithKnownKeyInParentAndChildObject() {
		var policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}..key == [{\"key\": 1}, 1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveKeyStepComplex() {
		var policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": [{\"key\": 1}, {\"key\": 2}]}}..key == [{\"key\": [{\"key\": 1}, {\"key\": 2}]}, [{\"key\": 1}, {\"key\": 2}], 1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void indexStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where var error = undefined[0]; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where undefined..[0] == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void indexStepOnString() {
		var policyDefinition = "policy \"test\" permit where var error = \"foo\"[0]; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void indexStepOnArrayWithUndefined() {
		var policyDefinition = "policy \"test\" permit where var error = [undefined][0]; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithUndefined() {
		var policyDefinition = "policy \"test\" permit where var error = [undefined]..[0]; error == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void indexStepOnArrayWithIndexOutOfBounds() {
		var policyDefinition = "policy \"test\" permit where var error = [0,1][2]; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithIndexOutOfBounds() {
		var policyDefinition = "policy \"test\" permit where [0,1]..[2] == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void indexStepOnArrayWithValidIndex() {
		var policyDefinition = "policy \"test\" permit where [0,1][1] == 1;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithValidIndex() {
		var policyDefinition = "policy \"test\" permit where [0,1]..[1] == [1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray1() {
		var policyDefinition = "policy \"test\" permit where [[0,1], 2]..[1] == [2, 1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray2() {
		var policyDefinition = "policy \"test\" permit where [0, [0,1]]..[1] == [[0,1], 1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray3() {
		var policyDefinition = "policy \"test\" permit where [0, [0, 1, 2]]..[2] == [2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveIndexStepComplex() {
		var policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2]]..[2] == [2, 5];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where var error = undefined.*; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnUndefined() {
		var policyDefinition = "policy \"test\" permit where var error = undefined..*; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnString() {
		var policyDefinition = "policy \"test\" permit where var error = \"foo\".*; true == true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnString() {
		var policyDefinition = "policy \"test\" permit where \"foo\"..* == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnEmptyObject() {
		var policyDefinition = "policy \"test\" permit where {}.* == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnEmptyObject() {
		var policyDefinition = "policy \"test\" permit where {}..* == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnEmptyArray() {
		var policyDefinition = "policy \"test\" permit where [].* == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnEmptyArray() {
		var policyDefinition = "policy \"test\" permit where []..* == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnSimpleObject() {
		var policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}.* == [1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnSimpleObject() {
		var policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}..* == [1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnSimpleArray() {
		var policyDefinition = "policy \"test\" permit where [1, 2].* == [1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnSimpleArray() {
		var policyDefinition = "policy \"test\" permit where [1, 2]..* == [1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnHierarchicalObject() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.* == [{\"key\": 1}];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnHierarchicalObject() {
		var policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..* == [{\"key\": 1}, 1];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void wildcardStepOnHierarchicalArray() {
		var policyDefinition = "policy \"test\" permit where [0, [1, 2]].* == [0, [1, 2]];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepOnHierarchicalArray() {
		var policyDefinition = "policy \"test\" permit where [0, [1, 2]]..* == [0, [1, 2], 1, 2];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void recursiveWildcardStepComplex() {
		var policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], 6]..* == [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], {\"text\": 1, \"arr\": [3, 4, 5]}, 1, [3, 4, 5], 3, 4, 5, 1, 2, 6];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void conditionStepOnEmptyArray() {
		var policyDefinition = "policy \"test\" permit where [][?(@ == undefined)] == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void conditionStepOnEmptyObject() {
		var policyDefinition = "policy \"test\" permit where {}[?(@ == undefined)] == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void functionCallOnObjectNodeWithRelativeArguments() {
		var policyDefinition = "import simple.append import filter.remove policy \"test\" permit where {\"name\": \"Ben\", \"origin\": \"Berlin\"} |- {@.name : append(\" from \", @.origin), @.origin : remove} == {\"name\": \"Ben from Berlin\"};";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void functionCallOnEachArrayItemWithRelativeArguments() {
		var policyDefinition = "import simple.* import filter.* policy \"test\" permit where [{\"name\": \"Hans\", \"origin\": \"Hagen\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}] |- { @..name : append(\" aus \", @.origin),  @..origin : remove} == [{\"name\": \"Hans aus Hagen\"}, {\"name\": \"Felix aus Zürich\"}];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void filterExtended() throws IOException {
		var policyDefinition = "policy \"test\" permit transform [\"foo\", \"bars\"] |- {each @.<sapl.pip.test.echo> : simple.length}";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void subTemplateOnEmptyArray() {
		var policyDefinition = "policy \"test\" permit where [] :: { \"name\": \"foo\" } == [];";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test

	void transformationError() {
		var policyDefinition = "policy \"test\" permit transform null * true";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void transformation() {
		var policyDefinition = "policy \"test\" permit transform null";
		var expected         = Optional.of(JSON.nullNode());
		StepVerifier
				.create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
				.assertNext(actual -> assertThat(actual.getResource(), is(expected))).verifyComplete();
	}

	@Test
	void obligation() {
		var policyDefinition   = "policy \"test\" permit obligation null";
		var expectedObligation = JSON.arrayNode();
		expectedObligation.add(JSON.nullNode());
		var expected = Optional.of(expectedObligation);
		StepVerifier
				.create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
				.assertNext(actual -> assertThat(actual.getObligations(), is(expected))).verifyComplete();
	}

	@Test
	void obligationError() {
		var policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void advice() {
		var policyDefinition = "policy \"test\" permit advice null";
		var expectedAdvice   = JSON.arrayNode();
		expectedAdvice.add(JSON.nullNode());
		var expected = Optional.of(expectedAdvice);
		StepVerifier
				.create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
				.assertNext(actual -> assertThat(actual.getAdvice(), is(expected))).verifyComplete();
	}

	@Test
	void adviceError() {
		var policyDefinition = "policy \"test\" permit advice \"a\" > 5";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importWildcard() {
		var policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importAttributeFinder() {
		var policyDefinition = "import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importLibrary() {
		var policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importMultiple() {
		var policyDefinition = "import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importNonExistingFunction() {
		var policyDefinition = "import simple.non_existing policy \"test\" permit where true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importDuplicateFunction() {
		var policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importDuplicateFunctionMatchingPolicy() {
		var policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importDuplicateWildcard() {
		var policyDefinition = "import simple.append import simple.* policy \"test\" permit where true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	@Test
	void importDuplicateAlias() {
		var policyDefinition = "import simple as test import simple as test policy \"test\" permit where true;";
		var expected         = AuthorizationDecision.INDETERMINATE;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}
	
	@Test
	void onErrorMap() {
		var policyDefinition = "import standard.* policy \"errors\" permit where onErrorMap(100/0, true);";
		var expected         = AuthorizationDecision.PERMIT;
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	private void assertThatPolicyEvaluationReturnsExpected(String document, AuthorizationDecision expected) {
		StepVerifier.create(INTERPRETER.evaluate(authzSubscription, document, attributeCtx, functionCtx, variables))
				.expectNext(expected).verifyComplete();
	}

}
