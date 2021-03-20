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
package io.sapl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.impl.util.EObjectUtil;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.TestPIP;
import reactor.core.publisher.Hooks;

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

	private EvaluationContext evaluationCtx;
	private AuthorizationSubscription authzSubscription;

	@BeforeEach
	void setUp() throws JsonProcessingException, InitializationException {
		Hooks.onOperatorDebug();
		authzSubscription = MAPPER.readValue(AUTHZ_SUBSCRIPTION_JSON, AuthorizationSubscription.class);
		var attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TestPIP());
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	void parseTest() {
		final String policyDocument = "policy \"test\" permit";
		INTERPRETER.parse(policyDocument);
	}

	@Test
	void brokenInputStreamTest() {
		var brokenInputStream = mock(InputStream.class);
		assertThrows(PolicyEvaluationException.class, () -> {
			INTERPRETER.parse(brokenInputStream);
		});
	}

	@Test
	void parseTestWithError() {
		final String policyDocument = "xyz";
		assertThrows(PolicyEvaluationException.class, () -> {
			INTERPRETER.parse(policyDocument);
		});
	}

	@Test
	void analyzePolicySet() {
		final String policyDefinition = "set \"test\" deny-overrides policy \"xx\" permit";
		final DocumentAnalysisResult expected = new DocumentAnalysisResult(true, "test", DocumentType.POLICY_SET, "");
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertEquals(expected, actual);
	}

	@Test
	void analyzePolicy() {
		final String policyDefinition = "policy \"test\" permit";
		final DocumentAnalysisResult expected = new DocumentAnalysisResult(true, "test", DocumentType.POLICY, "");
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertEquals(expected, actual);
	}

	@Test
	void analyzeException() {
		final String policyDefinition = "xyz";
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertFalse(actual.isValid());
	}

	@Test
	void permitAll() {
		final String policyDefinition = "policy \"test\" permit";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void denyAll() {
		final String policyDefinition = "policy \"test\" deny";
		final AuthorizationDecision expected = AuthorizationDecision.DENY;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void permitFalse() {
		final String policyDefinition = "policy \"test\" permit false";
		final AuthorizationDecision expected = AuthorizationDecision.NOT_APPLICABLE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void permitParseError() {
		final String policyDefinition = "--- policy \"test\" permit ---";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void targetNotBoolean() {
		final String policyDefinition = "policy \"test\" permit 20";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void syntaxError() {
		final String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
		assertThrows(PolicyEvaluationException.class, () -> {
			INTERPRETER.parse(policyDefinition);
		});
	}

	@Test
	void processParsedEmptyArray() {
		final String policyDefinition = "policy \"test\" permit resource.emptyArray == []";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void processParsedEmptyObject() {
		final String policyDefinition = "policy \"test\" permit resource.emptyObject == {}";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void evaluateWorkingBodyTrue() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void evaluateWorkingBodyFalse() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
		final AuthorizationDecision expected = AuthorizationDecision.NOT_APPLICABLE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void evaluateWorkingBodyError() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void echoAttributeFinder() {
		final String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void attributeFinderInTarget() {
		final String policyDefinition = "policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void attributeFinderWithArgumentsTest() {
		final String policyDefinition = "policy \"test\" permit where var variable = \"hello\"; variable.<sapl.pip.test.echoRepeat(2)> == (variable + variable);";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void bodyStatementNotBoolean() {
		final String policyDefinition = "policy \"test\" permit where null;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void variableRedefinition() {
		final String policyDefinition = "policy \"test\" permit where var test = null; var test = 2; test == 2;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void unboundVariable() {
		final String policyDefinition = "policy \"test\" permit where variable;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void functionCall() {
		final String policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void functionCallImport() {
		final String policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void functionCallError() {
		final String policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where undefined.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where undefined..key == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepOnString() {
		final String policyDefinition = "policy \"test\" permit where \"foo\".key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepOnString() {
		final String policyDefinition = "policy \"test\" permit where \"foo\"..key == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepWithUnknownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": 1}.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepWithUnknownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": 1}..key == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepWithKnownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1}.key == 1;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepWithKnownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1}..key == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepWithKnownKeyInChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepWithKnownKeyInChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..key == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void keyStepWithKnownKeyInParentAndChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}.key == {\"key\": 1};";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepWithKnownKeyInParentAndChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}..key == [{\"key\": 1}, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveKeyStepComplex() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": [{\"key\": 1}, {\"key\": 2}]}}..key == [{\"key\": [{\"key\": 1}, {\"key\": 2}]}, [{\"key\": 1}, {\"key\": 2}], 1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void indexStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where undefined..[0] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void indexStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\"[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void indexStepOnArrayWithUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = [undefined][0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = [undefined]..[0]; error == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void indexStepOnArrayWithIndexOutOfBounds() {
		final String policyDefinition = "policy \"test\" permit where var error = [0,1][2]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithIndexOutOfBounds() {
		final String policyDefinition = "policy \"test\" permit where [0,1]..[2] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void indexStepOnArrayWithValidIndex() {
		final String policyDefinition = "policy \"test\" permit where [0,1][1] == 1;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithValidIndex() {
		final String policyDefinition = "policy \"test\" permit where [0,1]..[1] == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray1() {
		final String policyDefinition = "policy \"test\" permit where [[0,1], 2]..[1] == [2, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray2() {
		final String policyDefinition = "policy \"test\" permit where [0, [0,1]]..[1] == [[0,1], 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepOnArrayWithChildArray3() {
		final String policyDefinition = "policy \"test\" permit where [0, [0, 1, 2]]..[2] == [2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveIndexStepComplex() {
		final String policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2]]..[2] == [2, 5];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined.*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined..*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\".*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnString() {
		final String policyDefinition = "policy \"test\" permit where \"foo\"..* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}.* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}..* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [].* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where []..* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnSimpleObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}.* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnSimpleObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}..* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnSimpleArray() {
		final String policyDefinition = "policy \"test\" permit where [1, 2].* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnSimpleArray() {
		final String policyDefinition = "policy \"test\" permit where [1, 2]..* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnHierarchicalObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.* == [{\"key\": 1}];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnHierarchicalObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..* == [{\"key\": 1}, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void wildcardStepOnHierarchicalArray() {
		final String policyDefinition = "policy \"test\" permit where [0, [1, 2]].* == [0, [1, 2]];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepOnHierarchicalArray() {
		final String policyDefinition = "policy \"test\" permit where [0, [1, 2]]..* == [0, [1, 2], 1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void recursiveWildcardStepComplex() {
		final String policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], 6]..* == [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], {\"text\": 1, \"arr\": [3, 4, 5]}, 1, [3, 4, 5], 3, 4, 5, 1, 2, 6];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void conditionStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [][?(@ == undefined)] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void conditionStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}[?(@ == undefined)] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void functionCallOnObjectNodeWithRelativeArguments() {
		final String policyDefinition = "import simple.append import filter.remove policy \"test\" permit where {\"name\": \"Ben\", \"origin\": \"Berlin\"} |- {@.name : append(\" from \", @.origin), @.origin : remove} == {\"name\": \"Ben from Berlin\"};";
		SAPL s = INTERPRETER.parse(policyDefinition);
		EObjectUtil.dump(s);
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void functionCallOnEachArrayItemWithRelativeArguments() {
		final String policyDefinition = "import simple.* import filter.* policy \"test\" permit where [{\"name\": \"Hans\", \"origin\": \"Hagen\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}] |- { @..name : append(\" aus \", @.origin),  @..origin : remove} == [{\"name\": \"Hans aus Hagen\"}, {\"name\": \"Felix aus Zürich\"}];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void filterExtended() throws IOException {
		final String policyDefinition = "policy \"test\" permit transform [\"foo\", \"bars\"] |- {each @.<sapl.pip.test.echo> : simple.length}";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void subtemplateOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [] :: { \"name\": \"foo\" } == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void transformation() {
		final String policyDefinition = "policy \"test\" permit transform null";
		final Optional<NullNode> expected = Optional.of(JSON.nullNode());
		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, evaluationCtx).blockFirst();
		final Optional<JsonNode> actual = authzDecision.getResource();
		assertEquals(expected, actual);
	}

	@Test
	void transformationError() {
		final String policyDefinition = "policy \"test\" permit transform null * true";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void obligation() {
		final String policyDefinition = "policy \"test\" permit obligation null";

		final ArrayNode expectedObligation = JSON.arrayNode();
		expectedObligation.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedObligation);

		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, evaluationCtx).blockFirst();
		final Optional<ArrayNode> actual = authzDecision.getObligations();

		assertEquals(expected, actual);
	}

	@Test
	void obligationError() {
		final String policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void advice() {
		final String policyDefinition = "policy \"test\" permit advice null";

		final ArrayNode expectedAdvice = JSON.arrayNode();
		expectedAdvice.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedAdvice);

		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, evaluationCtx).blockFirst();
		final Optional<ArrayNode> actual = authzDecision.getAdvices();

		assertEquals(expected, actual);
	}

	@Test
	void adviceError() {
		final String policyDefinition = "policy \"test\" permit advice \"a\" > 5";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importWildcard() {
		final String policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importAttributeFinder() {
		final String policyDefinition = "import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importLibrary() {
		final String policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importMultiple() {
		final String policyDefinition = "import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importNonExistingFunction() {
		final String policyDefinition = "import simple.non_existing policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importDuplicateFunction() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importDuplicateFunctionMatchingPolicy() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importDuplicateWildcard() {
		final String policyDefinition = "import simple.append import simple.* policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

	@Test
	void importDuplicateAlias() {
		final String policyDefinition = "import simple as test import simple as test policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER.evaluate(authzSubscription, policyDefinition, evaluationCtx)
				.blockFirst();
		assertEquals(expected, actual);
	}

}
