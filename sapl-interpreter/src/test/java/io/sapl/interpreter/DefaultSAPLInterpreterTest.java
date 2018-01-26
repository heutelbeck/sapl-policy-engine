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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pip.AttributeException;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.TestPIP;

public class DefaultSAPLInterpreterTest {

	private static final String REQUEST_JSON = "{" + "\"subject\" : { "
			+ "\"id\" : \"123456789012345678901212345678901234567890121234567890123456789012\","
			+ "\"organizationId\" : \"12345678901234567890121234567890123456789012\","
			+ "\"tenantId\" : \"1234567890123456789012\"," + "\"isActive\" : true," + "\"granted_authorities\" : {"
			+ "\"roles\"  : [ \"USER\", \"ACCOUNTANT\" ], " + "\"groups\" : [ \"OPERATORS\", \"DEVELOPERS\" ] " + " }"
			+ " }," + "\"action\" : { " + "\"verb\" : \"withdraw_funds\", " + "\"parameters\" : [ 200.00 ]" + "},"
			+ "\"resource\" : {" + "\"url\" : \"http://api.bank.com/accounts/12345\","
			+ "\"id\" : \"123456789012345678901212345678901234567890121234567890123456789999\","
			+ "\"organizationId\" : \"12345678901234567890121234567890123456789012\","
			+ "\"tenantId\" : \"1234567890123456789012\"," + "\"isActive\" : true,"
			+ "\"textarray\" : [ \"one\", \"two\" ],"
			+ "\"objectarray\" : [ {\"id\" : \"1\", \"name\" : \"one\"}, {\"id\" : \"2\", \"name\" : \"two\"} ] },"
			+ "\"environment\" : {" + "\"ipAddress\" : \"10.10.10.254\"," + "\"year\" : 2016" + "}" + " }";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private Request requestObject;
	private Response permitUnfiltered;
	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;

	@Before
	public void init()
			throws JsonParseException, JsonMappingException, IOException, FunctionException, AttributeException {
		requestObject = MAPPER.readValue(REQUEST_JSON, Request.class);
		permitUnfiltered = new Response(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.empty());
		attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TestPIP());
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
	}

	@Test
	public void parseTest() throws PolicyEvaluationException, FileNotFoundException {
		InputStream policyStream = new FileInputStream(new File("./src/test/resources/test_noerror.sapl"));
		try {
			INTERPRETER.parse(policyStream);
		} catch (Exception e) {
			assertNull("parsing from input stream does not work", e);
		}
	}

	@Test
	public void parseTestWithError() throws FileNotFoundException {
		try {
			InputStream policyStream = new FileInputStream(new File("./src/test/resources/test_parsingerror.sapl"));
			INTERPRETER.parse(policyStream);
			fail("FileNotFound Error not detected");
		} catch (PolicyEvaluationException e) {
			assertNotNull("FileNotFound Error not detected", e);
		}
	}

	@Test
	public void parseTestWithIOError() {
		try {
			InputStream policyStream = new FileInputStream(new File("./src/test/resources/test_noerror.sapl"));
			policyStream.close();
			INTERPRETER.parse(policyStream);
			fail("IO Error not detected");
		} catch (PolicyEvaluationException | IOException e) {
			assertNotNull("IO Error not detected", e);
		}
	}

	@Test
	public void analyzePolicySet() {
		String policyDefinition = "set  \"test\" deny-overrides policy \"xx\" permit";
		DocumentAnalysisResult result = INTERPRETER.analyze(policyDefinition);
		assertEquals("policy set analysis result mismatch",
				new DocumentAnalysisResult(true, "test", DocumentType.POLICY_SET, ""), result);
	}

	@Test
	public void analyzePolicy() {
		String policyDefinition = "policy  \"test\" permit";
		DocumentAnalysisResult result = INTERPRETER.analyze(policyDefinition);
		assertEquals("policy analysis result mismatch", result,
				new DocumentAnalysisResult(true, "test", DocumentType.POLICY, ""));
	}

	@Test
	public void analyzeException() {
		String policyDefinition = "xyz";
		DocumentAnalysisResult result = INTERPRETER.analyze(policyDefinition);
		assertFalse("policy analysis failure not reported correctly", result.isValid());
	}

	@Test
	public void permitAll() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit";
		assertEquals("permit all did not evaluate to permit", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void denyAll() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" deny";
		assertEquals("deny all did not evaluate to deny", Response.deny(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void permitFalse() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit false";
		assertEquals("false in target did not lead to not_applicable result", Response.notApplicable(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void permitParseError() throws PolicyEvaluationException {
		String policyDefinition = "--- policy \"test\" permit ---";
		assertEquals("parse error should lead to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void targetNotBoolean() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit 20";
		assertEquals("target expression type mismatch not detected", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test(expected = PolicyEvaluationException.class)
	public void syntaxError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
		INTERPRETER.parse(policyDefinition);
	}

	@Test
	public void evaluateWorkingBodyTrue() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
		assertEquals("evaluateRule behaves unexpectedly", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void evaluateWorkingBodyFalse() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
		assertEquals("evaluateRule behaves unexpectedly", Response.notApplicable(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void evaluateWorkingBodyError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
		assertEquals("evaluateRule behaves unexpectedly", Response.indeterminate(), INTERPRETER.evaluateRules(
				requestObject,
						INTERPRETER.parse(policyDefinition), attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void echoAttributeFinder() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
		assertEquals("external attribute finder not evaluated as expected", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void attributeFinderInTarget() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"";
		assertEquals("external attribute finder was allowed in target", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void bodyStatementNotBoolean() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where null;";
		assertEquals("non boolean statement should lead to an error", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void variableRedefinition() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where var test = null; var test = 2; test == 2;";
		assertEquals("redefinition of value was not allowed", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void unboundVariablet() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where variable;";
		assertEquals("access to unbound variable should lead to an error", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void functionCall() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
		assertEquals("function call not evaluated as expected", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void functionCallImport() throws PolicyEvaluationException {
		String policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
		assertEquals("function call with import not evaluated as expected", permitUnfiltered,
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void functionCallError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
		assertEquals("function call error should lead to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void transformation() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit transform null";
		assertEquals("transformation not evaluated as expected", Optional.of(JSON.nullNode()),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.getResource());
	}

	@Test
	public void transformationError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit transform null + true";
		assertEquals("error in transformation should evaluate to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void obligation() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit obligation null";
		ArrayNode expectedObligation = JSON.arrayNode();
		expectedObligation.add(JSON.nullNode());
		assertEquals("obligation not evaluated as expected", Optional.of(expectedObligation),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.getObligation());
	}

	@Test
	public void obligationError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
		assertEquals("error in obligation evaluation should evaluate to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void advice() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit advice null";
		ArrayNode expectedAdvice = JSON.arrayNode();
		expectedAdvice.add(JSON.nullNode());
		assertEquals("advice not evaluated as expected", Optional.of(expectedAdvice), INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getAdvice());
	}

	@Test
	public void adviceError() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit advice \"a\" > 5";
		assertEquals("error in advice evaluation should evaluate to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void importWildcard() throws PolicyEvaluationException {
		String policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
		assertEquals("wildcard import not working", Decision.PERMIT, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importFinder() throws PolicyEvaluationException {
		String policyDefinition = "import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;";
		assertEquals("wildcard import not working", Decision.PERMIT, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importLibrary() throws PolicyEvaluationException {
		String policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
		assertEquals("library import with alias not working", Decision.PERMIT, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importMultiple() throws PolicyEvaluationException {
		String policyDefinition = "import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");";
		assertEquals("multiple imports not working", Decision.PERMIT, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importNonExistingFunction() throws PolicyEvaluationException {
		String policyDefinition = "import simple.non_existing policy \"test\" permit where true;";
		assertEquals("importing non existing function should cause an error", Decision.INDETERMINATE, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importDuplicateFunction() throws PolicyEvaluationException {
		String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		assertEquals("importing duplicate short name should cause an error", Decision.INDETERMINATE, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importDuplicateFunctionMatchingPolicy() throws PolicyEvaluationException {
		String policyDefinition = "import simple.append import simple.append policy \"test\" permit where true;";
		SAPL policy = INTERPRETER.parse(policyDefinition);
		assertEquals("importing duplicate short name should cause an error", Decision.INDETERMINATE, INTERPRETER
				.evaluateRules(requestObject, policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importDuplicateWildcard() throws PolicyEvaluationException {
		String policyDefinition = "import simple.append import simple.* policy \"test\" permit where true;";
		assertEquals("importing duplicate short name should cause an error", Decision.INDETERMINATE, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importDuplicateAlias() throws PolicyEvaluationException {
		String policyDefinition = "import simple as test import simple as test policy \"test\" permit where true;";
		assertEquals("importing duplicate aliased name should cause an error", Decision.INDETERMINATE, INTERPRETER
				.evaluate(requestObject, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void importWithSubjectAsVariable() throws PolicyEvaluationException {
		String policyDefinition = "policy \"test\" permit";
		SAPL policy = INTERPRETER.parse(policyDefinition);
		Map<String, JsonNode> variables = new HashMap<>();
		variables.put("subject", JSON.nullNode());
		assertEquals("evaluateRules called with subject as variable name short evaluate to indeterminate",
				Response.indeterminate(),
				INTERPRETER.evaluateRules(requestObject, policy.getPolicyElement(), attributeCtx, functionCtx,
						SYSTEM_VARIABLES, variables, new HashMap<>()));
	}

}
