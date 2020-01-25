/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.TestPIP;

public class DefaultSAPLInterpreterTest {

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

	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private AuthorizationSubscription authzSubscription;

	private AttributeContext attributeCtx;

	private FunctionContext functionCtx;

	@Before
	public void init() throws IOException, FunctionException, AttributeException {
		authzSubscription = MAPPER.readValue(AUTHZ_SUBSCRIPTION_JSON, AuthorizationSubscription.class);
		attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TestPIP());
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
	}

	@Test
	public void parseTest() throws PolicyEvaluationException {
		final String policyDocument = "policy \"test\" permit";
		INTERPRETER.parse(policyDocument);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void parseTestWithError() throws PolicyEvaluationException {
		final String policyDocument = "xyz";
		INTERPRETER.parse(policyDocument);
	}

	@Test
	public void analyzePolicySet() {
		final String policyDefinition = "set \"test\" deny-overrides policy \"xx\" permit";
		final DocumentAnalysisResult expected = new DocumentAnalysisResult(true, "test", DocumentType.POLICY_SET, "");
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertEquals("policy set analysis result mismatch", expected, actual);
	}

	@Test
	public void analyzePolicy() {
		final String policyDefinition = "policy \"test\" permit";
		final DocumentAnalysisResult expected = new DocumentAnalysisResult(true, "test", DocumentType.POLICY, "");
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertEquals("policy analysis result mismatch", expected, actual);
	}

	@Test
	public void analyzeException() {
		final String policyDefinition = "xyz";
		final DocumentAnalysisResult actual = INTERPRETER.analyze(policyDefinition);
		assertFalse("policy analysis failure not reported correctly", actual.isValid());
	}

	@Test
	public void permitAll() {
		final String policyDefinition = "policy \"test\" permit";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("permit all did not evaluate to permit", expected, actual);
	}

	@Test
	public void denyAll() {
		final String policyDefinition = "policy \"test\" deny";
		final AuthorizationDecision expected = AuthorizationDecision.DENY;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("deny all did not evaluate to deny", expected, actual);
	}

	@Test
	public void permitFalse() {
		final String policyDefinition = "policy \"test\" permit false";
		final AuthorizationDecision expected = AuthorizationDecision.NOT_APPLICABLE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("false in target did not lead to not_applicable result", expected, actual);
	}

	@Test
	public void permitParseError() {
		final String policyDefinition = "--- policy \"test\" permit ---";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("parse error should lead to indeterminate", expected, actual);
	}

	@Test
	public void targetNotBoolean() {
		final String policyDefinition = "policy \"test\" permit 20";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("target expression type mismatch not detected", expected, actual);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void syntaxError() throws PolicyEvaluationException {
		final String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
		INTERPRETER.parse(policyDefinition);
	}

	@Test
	public void processParsedEmptyArray() {
		final String policyDefinition = "policy \"test\" permit resource.emptyArray == []";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("a parsed empty array cannot be processed", expected, actual);
	}

	@Test
	public void processParsedEmptyObject() {
		final String policyDefinition = "policy \"test\" permit resource.emptyObject == {}";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("a parsed empty object cannot be processed", expected, actual);
	}

	@Test
	public void evaluateWorkingBodyTrue() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void evaluateWorkingBodyFalse() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
		final AuthorizationDecision expected = AuthorizationDecision.NOT_APPLICABLE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void evaluateWorkingBodyError() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void echoAttributeFinder() {
		final String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("external attribute finder not evaluated as expected", expected, actual);
	}

	@Test
	public void attributeFinderInTarget() {
		final String policyDefinition = "policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		// StepVerifier.create(INTERPRETER.evaluate(authzSubscriptionObject,
		// policyDefinition,
		// attributeCtx, functionCtx, SYSTEM_VARIABLES))
		// .expectNext(expected)
		// .verifyComplete();
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("external attribute finder was allowed in target", expected, actual);
	}

	@Test
	public void bodyStatementNotBoolean() {
		final String policyDefinition = "policy \"test\" permit where null;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("non boolean statement should lead to an error", expected, actual);
	}

	@Test
	public void variableRedefinition() {
		final String policyDefinition = "policy \"test\" permit where var test = null; var test = 2; test == 2;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("redefinition of value was not allowed", expected, actual);
	}

	@Test
	public void unboundVariable() {
		final String policyDefinition = "policy \"test\" permit where variable;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("access to unbound variable should lead to an error", expected, actual);
	}

	@Test
	public void functionCall() {
		final String policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("function call not evaluated as expected", expected, actual);
	}

	@Test
	public void functionCallImport() {
		final String policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("function call with import not evaluated as expected", expected, actual);
	}

	@Test
	public void functionCallError() {
		final String policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("function call error should lead to indeterminate", expected, actual);
	}

	@Test
	public void keyStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where undefined.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step on undefined did not evaluate to undefined", expected, actual);
	}

	@Test
	public void recursiveKeyStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where undefined..key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive key step on undefined did not evaluate to undefined", expected, actual);
	}

	@Test
	public void keyStepOnString() {
		final String policyDefinition = "policy \"test\" permit where \"foo\".key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step on string did not evaluate to undefined", expected, actual);
	}

	@Test
	public void recursiveKeyStepOnString() {
		final String policyDefinition = "policy \"test\" permit where \"foo\"..key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive key step on string did not evaluate to undefined", expected, actual);
	}

	@Test
	public void keyStepWithUnknownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": 1}.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step with unknown key did not evaluate to undefined", expected, actual);
	}

	@Test
	public void recursiveKeyStepWithUnknownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": 1}..key == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive key step with unknown key did not evaluate to the empty array", expected, actual);
	}

	@Test
	public void keyStepWithKnownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1}.key == 1;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step with known key did not evaluate to the value", expected, actual);
	}

	@Test
	public void recursiveKeyStepWithKnownKey() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1}..key == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive key step with known key did not evaluate to an array containing the value", expected,
				actual);
	}

	@Test
	public void keyStepWithKnownKeyInChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.key == undefined;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step with known key in child object did not evaluate to undefined", expected, actual);
	}

	@Test
	public void recursiveKeyStepWithKnownKeyInChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..key == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals(
				"recursive key step with known key in child object did not evaluate to an array containing the value",
				expected, actual);
	}

	@Test
	public void keyStepWithKnownKeyInParentAndChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}.key == {\"key\": 1};";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("key step with known key in parent and child object did not evaluate to child object", expected,
				actual);
	}

	@Test
	public void recursiveKeyStepWithKnownKeyInParentAndChildObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": 1}}..key == [{\"key\": 1}, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals(
				"recursive key step with known key in parent and child object did not evaluate to an array containing the values",
				expected, actual);
	}

	@Test
	public void recursiveKeyStepComplex() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": {\"key\": [{\"key\": 1}, {\"key\": 2}]}}..key == [{\"key\": [{\"key\": 1}, {\"key\": 2}]}, [{\"key\": 1}, {\"key\": 2}], 1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive key step on complex object did not evaluate as expected", expected, actual);
	}

	@Test
	public void indexStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("index step on undefined did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveIndexStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined..[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on undefined did not throw an exception", expected, actual);
	}

	@Test
	public void indexStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\"[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("index step on string did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveIndexStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\"..[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on string did not throw an exception", expected, actual);
	}

	@Test
	public void indexStepOnArrayWithUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = [undefined][0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("index step on array with undefined did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = [undefined]..[0]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on array with undefined did not throw an exception", expected, actual);
	}

	@Test
	public void indexStepOnArrayWithIndexOutOfBounds() {
		final String policyDefinition = "policy \"test\" permit where var error = [0,1][2]; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("index step on array with index out of bounds did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithIndexOutOfBounds() {
		final String policyDefinition = "policy \"test\" permit where [0,1]..[2] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on array with index out of bounds did not throw an exception", expected,
				actual);
	}

	@Test
	public void indexStepOnArrayWithValidIndex() {
		final String policyDefinition = "policy \"test\" permit where [0,1][1] == 1;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("index step on array with valid index did not evaluate to array element", expected, actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithValidIndex() {
		final String policyDefinition = "policy \"test\" permit where [0,1]..[1] == [1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals(
				"recursive index step on array with valid index did not evaluate to array containing the correct element",
				expected, actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithChildArray1() {
		final String policyDefinition = "policy \"test\" permit where [[0,1], 2]..[1] == [2, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on array with child array (1) did not evaluate as expected", expected,
				actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithChildArray2() {
		final String policyDefinition = "policy \"test\" permit where [0, [0,1]]..[1] == [[0,1], 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on array with child array (2) did not evaluate as expected", expected,
				actual);
	}

	@Test
	public void recursiveIndexStepOnArrayWithChildArray3() {
		final String policyDefinition = "policy \"test\" permit where [0, [0, 1, 2]]..[2] == [2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on array with child array (3) did not evaluate as expected", expected,
				actual);
	}

	@Test
	public void recursiveIndexStepComplex() {
		final String policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2]]..[2] == [2, 5];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive index step on complex array did not evaluate as expected", expected, actual);
	}

	@Test
	public void wildcardStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined.*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on undefined did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnUndefined() {
		final String policyDefinition = "policy \"test\" permit where var error = undefined..*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on undefined did not throw an exception", expected, actual);
	}

	@Test
	public void wildcardStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\".*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on string did not throw an exception", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnString() {
		final String policyDefinition = "policy \"test\" permit where var error = \"foo\"..*; true == true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on string did not throw an exception", expected, actual);
	}

	@Test
	public void wildcardStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}.* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on empty object did not evaluate to the empty array", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}..* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on empty object did not evaluate to the empty array", expected, actual);
	}

	@Test
	public void wildcardStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [].* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on empty array did not evaluate to the empty array", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where []..* == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on empty array did not evaluate to the empty array", expected, actual);
	}

	@Test
	public void wildcardStepOnSimpleObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}.* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on simple object did not evaluate to array of values", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnSimpleObject() {
		final String policyDefinition = "policy \"test\" permit where {\"key\": 1, \"attr\": 2}..* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on simple object did not evaluate to array of values", expected, actual);
	}

	@Test
	public void wildcardStepOnSimpleArray() {
		final String policyDefinition = "policy \"test\" permit where [1, 2].* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on simple array did not evaluate to same array", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnSimpleArray() {
		final String policyDefinition = "policy \"test\" permit where [1, 2]..* == [1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on simple array did not evaluate to array of elements", expected, actual);
	}

	@Test
	public void wildcardStepOnHierarchicalObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}.* == [{\"key\": 1}];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on hierarchical object did not evaluate to array of top level values", expected,
				actual);
	}

	@Test
	public void recursiveWildcardStepOnHierarchicalObject() {
		final String policyDefinition = "policy \"test\" permit where {\"attr\": {\"key\": 1}}..* == [{\"key\": 1}, 1];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on hierarchical object did not evaluate to array of all values", expected,
				actual);
	}

	@Test
	public void wildcardStepOnHierarchicalArray() {
		final String policyDefinition = "policy \"test\" permit where [0, [1, 2]].* == [0, [1, 2]];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard step on hierarchical array did not evaluate to same array", expected, actual);
	}

	@Test
	public void recursiveWildcardStepOnHierarchicalArray() {
		final String policyDefinition = "policy \"test\" permit where [0, [1, 2]]..* == [0, [1, 2], 1, 2];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("recursive wildcard step on hierarchical array did not evaluate as expected", expected, actual);
	}

	@Test
	public void recursiveWildcardStepComplex() {
		final String policyDefinition = "policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], 6]..* == [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], {\"text\": 1, \"arr\": [3, 4, 5]}, 1, [3, 4, 5], 3, 4, 5, 1, 2, 6];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("complex recursive wildcard step did not evaluate as expected", expected, actual);
	}

	@Test
	public void conditionStepOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [][?(@ == undefined)] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("condition step on empty array did not evaluate to empty array", expected, actual);
	}

	@Test
	public void conditionStepOnEmptyObject() {
		final String policyDefinition = "policy \"test\" permit where {}[?(@ == undefined)] == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("condition step on empty object did not evaluate to empty array", expected, actual);
	}

	@Test
	public void functionCallOnObjectNodeWithRelativeArguments() {
		final String policyDefinition = "import simple.append policy \"test\" permit where {\"name\": \"Ben\", \"origin\": \"Berlin\"} |- {@.name : append(\" from \", @.origin), @.origin : remove} == {\"name\": \"Ben from Berlin\"};";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("function call on object node passing relative arguments not evaluated as expected", expected,
				actual);
	}

	@Test
	public void functionCallOnEachArrayItemWithRelativeArguments() {
		final String policyDefinition = "import simple.* policy \"test\" permit where [{\"name\": \"Hans\", \"origin\": \"Hagen\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}] |- {each @..name : append(\" aus \", @.origin), each @..origin : remove} == [{\"name\": \"Hans aus Hagen\"}, {\"name\": \"Felix aus Zürich\"}];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("function call on each array item passing relative arguments not evaluated as expected", expected,
				actual);
	}

	@Test
	public void filterExtended() throws IOException {
		final String policyDefinition = "policy \"test\" permit transform [\"foo\", \"bars\"] |- {each @.<sapl.pip.test.echo> : simple.length}";
		final AuthorizationDecision expected = new AuthorizationDecision(Decision.PERMIT,
				Optional.of(MAPPER.readTree("[3, 4]")), Optional.empty(), Optional.empty());
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("permit all did not evaluate to permit", expected, actual);
	}

	@Test
	public void subtemplateOnEmptyArray() {
		final String policyDefinition = "policy \"test\" permit where [] :: { \"name\": \"foo\" } == [];";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("subtemplate on empty array did not evaluate to empty array", expected, actual);
	}

	@Test
	public void transformation() {
		final String policyDefinition = "policy \"test\" permit transform null";
		final Optional<NullNode> expected = Optional.of(JSON.nullNode());
		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		final Optional<JsonNode> actual = authzDecision.getResource();
		assertEquals("transformation not evaluated as expected", expected, actual);
	}

	@Test
	public void transformationError() {
		final String policyDefinition = "policy \"test\" permit transform null * true";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("error in transformation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void obligation() {
		final String policyDefinition = "policy \"test\" permit obligation null";

		final ArrayNode expectedObligation = JSON.arrayNode();
		expectedObligation.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedObligation);

		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		final Optional<ArrayNode> actual = authzDecision.getObligations();

		assertEquals("obligation not evaluated as expected", expected, actual);
	}

	@Test
	public void obligationError() {
		final String policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("error in obligation evaluation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void advice() {
		final String policyDefinition = "policy \"test\" permit advice null";

		final ArrayNode expectedAdvice = JSON.arrayNode();
		expectedAdvice.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedAdvice);

		final AuthorizationDecision authzDecision = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		final Optional<ArrayNode> actual = authzDecision.getAdvices();

		assertEquals("advice not evaluated as expected", expected, actual);
	}

	@Test
	public void adviceError() {
		final String policyDefinition = "policy \"test\" permit advice \"a\" > 5";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("error in advice evaluation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void importWildcard() {
		final String policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("wildcard import not working", expected, actual);
	}

	@Test
	public void importAttributeFinder() {
		final String policyDefinition = "import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("attribute finder import not working", expected, actual);
	}

	@Test
	public void importLibrary() {
		final String policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("library import with alias not working", expected, actual);
	}

	@Test
	public void importMultiple() {
		final String policyDefinition = "import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");";
		final AuthorizationDecision expected = AuthorizationDecision.PERMIT;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("multiple imports not working", expected, actual);
	}

	@Test
	public void importNonExistingFunction() {
		final String policyDefinition = "import simple.non_existing policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("importing non existing function should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateFunction() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateFunctionMatchingPolicy() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateWildcard() {
		final String policyDefinition = "import simple.append import simple.* policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateAlias() {
		final String policyDefinition = "import simple as test import simple as test policy \"test\" permit where true;";
		final AuthorizationDecision expected = AuthorizationDecision.INDETERMINATE;
		final AuthorizationDecision actual = INTERPRETER
				.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst();
		assertEquals("importing duplicate aliased name should cause an error", expected, actual);
	}

}
