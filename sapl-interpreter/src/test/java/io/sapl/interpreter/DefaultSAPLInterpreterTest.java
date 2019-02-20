/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.TestPIP;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class DefaultSAPLInterpreterTest {

	private static final String REQUEST_JSON = "{ " + "\"subject\" : { " + "\"id\" : \"1234\","
			+ "\"organizationId\" : \"5678\"," + "\"isActive\" : true," + "\"granted_authorities\" : { "
			+ "\"roles\"  : [ \"USER\", \"ACCOUNTANT\" ], " + "\"groups\" : [ \"OPERATORS\", \"DEVELOPERS\" ] " + " }"
			+ " }," + "\"action\" : { " + "\"verb\" : \"withdraw_funds\", " + "\"parameters\" : [ 200.00 ]" + "},"
			+ "\"resource\" : { " + "\"url\" : \"http://api.bank.com/accounts/12345\"," + "\"id\" : \"9012\","
			+ "\"textarray\" : [ \"one\", \"two\" ],"
			+ "\"objectarray\" : [ {\"id\" : \"1\", \"name\" : \"one\"}, {\"id\" : \"2\", \"name\" : \"two\"} ] " + "},"
			+ "\"environment\" : { " + "\"ipAddress\" : \"10.10.10.254\"," + "\"year\" : 2016" + "}" + " }";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private Request requestObject;
	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;

	@Before
	public void init() throws IOException, FunctionException, AttributeException {
		requestObject = MAPPER.readValue(REQUEST_JSON, Request.class);
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
	@Ignore
	public void permitAll() {
		final String policyDefinition = "policy \"test\" permit transform [\"foo\", \"bar\"] |- {each @.<sapl.pip.test.echo> : simple.length}";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("permit all did not evaluate to permit", expected, actual);
	}

	@Test
	public void denyAll() {
		final String policyDefinition = "policy \"test\" deny";
		final Response expected = Response.deny();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("deny all did not evaluate to deny", expected, actual);
	}

	@Test
	public void permitFalse() {
		final String policyDefinition = "policy \"test\" permit false";
		final Response expected = Response.notApplicable();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("false in target did not lead to not_applicable result", expected, actual);
	}

	@Test
	public void permitParseError() {
		final String policyDefinition = "--- policy \"test\" permit ---";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("parse error should lead to indeterminate", expected, actual);
	}

	@Test
	public void targetNotBoolean() {
		final String policyDefinition = "policy \"test\" permit 20";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("target expression type mismatch not detected", expected, actual);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void syntaxError() throws PolicyEvaluationException {
		final String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
		INTERPRETER.parse(policyDefinition);
	}

	@Test
	public void evaluateWorkingBodyTrue() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void evaluateWorkingBodyFalse() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
		final Response expected = Response.notApplicable();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void evaluateWorkingBodyError() {
		final String policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("evaluateRule behaves unexpectedly", expected, actual);
	}

	@Test
	public void echoAttributeFinder() {
		final String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("external attribute finder not evaluated as expected", expected, actual);
	}

	@Test
	public void attributeFinderInTarget() {
		final String policyDefinition = "policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"";
		final Response expected = Response.indeterminate();
//		StepVerifier.create(INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES))
//				.expectNext(expected)
//				.verifyComplete();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("external attribute finder was allowed in target", expected, actual);
	}

	@Test
	public void bodyStatementNotBoolean() {
		final String policyDefinition = "policy \"test\" permit where null;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("non boolean statement should lead to an error", expected, actual);
	}

	@Test
	public void variableRedefinition() {
		final String policyDefinition = "policy \"test\" permit where var test = null; var test = 2; test == 2;";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("redefinition of value was not allowed", expected, actual);
	}

	@Test
	public void unboundVariable() {
		final String policyDefinition = "policy \"test\" permit where variable;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("access to unbound variable should lead to an error", expected, actual);
	}

	@Test
	public void functionCall() {
		final String policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("function call not evaluated as expected", expected, actual);
	}

	@Test
	public void functionCallImport() {
		final String policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("function call with import not evaluated as expected", expected, actual);
	}

	@Test
	public void functionCallError() {
		final String policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("function call error should lead to indeterminate", expected, actual);
	}

	@Test
	public void functionCallOnObjectNodeWithRelativeArguments() {
		final String policyDefinition = "import simple.append policy \"test\" permit where {\"name\": \"Ben\", \"origin\": \"Berlin\"} |- {@.name : append(\" from \", @.origin), @.origin : remove} == {\"name\": \"Ben from Berlin\"};";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("function call on object node passing relative arguments not evaluated as expected", expected,
				actual);
	}

	@Test
	public void functionCallOnEachArrayItemWithRelativeArguments() {
		final String policyDefinition = "import simple.append policy \"test\" permit where [{\"name\": \"Ben\", \"origin\": \"Berlin\"}, {\"name\": \"Zoe\", \"origin\": \"Zurich\"}] |- {each @.name : append(\" from \", @.origin), each @.origin : remove} == [{\"name\": \"Ben from Berlin\"}, {\"name\": \"Zoe from Zurich\"}];";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("function call on each array item passing relative arguments not evaluated as expected", expected,
				actual);
	}

	@Test
	public void transformation() {
		final String policyDefinition = "policy \"test\" permit transform null";
		final Optional<NullNode> expected = Optional.of(JSON.nullNode());
		final Response response = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		final Optional<JsonNode> actual = response.getResource();
		assertEquals("transformation not evaluated as expected", expected, actual);
	}

	@Test
	public void transformationError() {
		final String policyDefinition = "policy \"test\" permit transform null + true";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("error in transformation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void obligation() {
		final String policyDefinition = "policy \"test\" permit obligation null";

		final ArrayNode expectedObligation = JSON.arrayNode();
		expectedObligation.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedObligation);

		final Response response = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		final Optional<ArrayNode> actual = response.getObligations();

		assertEquals("obligation not evaluated as expected", expected, actual);
	}

	@Test
	public void obligationError() {
		final String policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("error in obligation evaluation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void advice() {
		final String policyDefinition = "policy \"test\" permit advice null";

		final ArrayNode expectedAdvice = JSON.arrayNode();
		expectedAdvice.add(JSON.nullNode());
		final Optional<ArrayNode> expected = Optional.of(expectedAdvice);

		final Response response = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		final Optional<ArrayNode> actual = response.getAdvices();

		assertEquals("advice not evaluated as expected", expected, actual);
	}

	@Test
	public void adviceError() {
		final String policyDefinition = "policy \"test\" permit advice \"a\" > 5";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("error in advice evaluation should evaluate to indeterminate", expected, actual);
	}

	@Test
	public void importWildcard() {
		final String policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("wildcard import not working", expected, actual);
	}

	@Test
	public void importAttributeFinder() {
		final String policyDefinition = "import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("attribute finder import not working", expected, actual);
	}

	@Test
	public void importLibrary() {
		final String policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("library import with alias not working", expected, actual);
	}

	@Test
	public void importMultiple() {
		final String policyDefinition = "import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");";
		final Response expected = Response.permit();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("multiple imports not working", expected, actual);
	}

	@Test
	public void importNonExistingFunction() {
		final String policyDefinition = "import simple.non_existing policy \"test\" permit where true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("importing non existing function should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateFunction() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateFunctionMatchingPolicy() {
		final String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateWildcard() {
		final String policyDefinition = "import simple.append import simple.* policy \"test\" permit where true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("importing duplicate short name should cause an error", expected, actual);
	}

	@Test
	public void importDuplicateAlias() {
		final String policyDefinition = "import simple as test import simple as test policy \"test\" permit where true;";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst();
		assertEquals("importing duplicate aliased name should cause an error", expected, actual);
	}

}
