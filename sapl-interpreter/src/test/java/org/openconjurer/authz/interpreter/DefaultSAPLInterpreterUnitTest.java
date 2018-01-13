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
package org.openconjurer.authz.interpreter;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.openconjurer.authz.interpreter.pip.TestPIP;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pip.AttributeException;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

public class DefaultSAPLInterpreterUnitTest {

    private static final String REQUEST_JSON = "{" + "\"subject\" : { "
            + "\"id\" : \"123456789012345678901212345678901234567890121234567890123456789012\","
            + "\"organizationId\" : \"12345678901234567890121234567890123456789012\","
            + "\"tenantId\" : \"1234567890123456789012\","
            + "\"isActive\" : true," + "\"granted_authorities\" : {"
            + "\"roles\"  : [ \"USER\", \"ACCOUNTANT\" ], "
            + "\"groups\" : [ \"OPERATORS\", \"DEVELOPERS\" ] " + " }" + " },"
            + "\"action\" : { " + "\"verb\" : \"withdraw_funds\", "
            + "\"parameters\" : [ 200.00 ]" + "}," + "\"resource\" : {"
            + "\"url\" : \"http://api.bank.com/accounts/12345\","
            + "\"id\" : \"123456789012345678901212345678901234567890121234567890123456789999\","
            + "\"organizationId\" : \"12345678901234567890121234567890123456789012\","
            + "\"tenantId\" : \"1234567890123456789012\","
            + "\"isActive\" : true," + "\"textarray\" : [ \"one\", \"two\" ],"
            + "\"objectarray\" : [ {\"id\" : \"1\", \"name\" : \"one\"}, {\"id\" : \"2\", \"name\" : \"two\"} ] },"
            + "\"environment\" : {" + "\"ipAddress\" : \"10.10.10.254\","
            + "\"year\" : 2016" + "}" + " }";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections
            .unmodifiableMap(new HashMap<>());
    private static final Response DENY = new Response(Decision.DENY, Optional.empty(), null,
            null);
    private static final Response NOT_APPLICABLE = Response.notApplicable();
    private static final Response INDETERMINATE = Response.indeterminate();
    private static final String IPADDRESS_PATTERN = "^10\\\\.10\\\\.10\\\\.([1-9]\\\\d?|1\\\\d{2}|2[0-4]\\\\d|25[0-4])$";

    private Request requestObject;
    private Response permitUnfiltered;
    private AttributeContext attributeCtx;
    private FunctionContext functionCtx;

    @Before
    public void init()
            throws JsonParseException,
            JsonMappingException,
            IOException,
            FunctionException,
            AttributeException {
        requestObject = MAPPER.readValue(REQUEST_JSON, Request.class);
        permitUnfiltered = new Response(Decision.PERMIT,
		        Optional.empty(),
		        null, null);
        attributeCtx = new AnnotationAttributeContext();
        
        attributeCtx.loadPolicyInformationPoint(new TestPIP());
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(new FilterFunctionLibrary());
        functionCtx.loadLibrary(new SimpleFunctionLibrary());
        functionCtx.loadLibrary(
                new SimpleFilterFunctionLibrary(Clock.systemUTC()));
    }

    @Test
    public void parseTest()
            throws PolicyEvaluationException,
            FileNotFoundException {
        InputStream policyStream = new FileInputStream(
                new File("./src/test/resources/test_noerror.sapl"));
        try {
            INTERPRETER.parse(policyStream);
        } catch (Exception e) {
            assertNull("parsing from input stream does not work", e);
        }
    }

    @Test
    public void parseTestWithError() throws FileNotFoundException {
        try {
            InputStream policyStream = new FileInputStream(
                    new File("./src/test/resources/test_parsingerror.sapl"));
            INTERPRETER.parse(policyStream);
            fail("FileNotFound Error not detected");
        } catch (PolicyEvaluationException e) {
            assertNotNull("FileNotFound Error not detected", e);
        }
    }

    @Test
    public void parseTestWithIOError() {
        try {
            InputStream policyStream = new FileInputStream(
                    new File("./src/test/resources/test_noerror.sapl"));
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
                new DocumentAnalysisResult(true, "test",
                        DocumentType.POLICY_SET, ""),
                result);
    }

    @Test
    public void analyzePolicy() {
        String policyDefinition = "policy  \"test\" permit";
        DocumentAnalysisResult result = INTERPRETER.analyze(policyDefinition);
        assertEquals("policy analysis result mismatch", result,
                new DocumentAnalysisResult(true, "test", DocumentType.POLICY,
                        ""));
    }

    @Test
    public void analyzeException() {
        String policyDefinition = "xyz";
        DocumentAnalysisResult result = INTERPRETER.analyze(policyDefinition);
        assertFalse("policy analysis failure not reported correctly",
                result.isValid());
    }

    @Test
    public void complexPolicy1() throws PolicyEvaluationException {
        // @formatter:off
		String policyDefinition = "policy \"test\" permit " + "subject.organizationId == resource.organizationId && "
				+ "action.verb == \"withdraw_funds\"" + " where " + "subject.isActive; "
				+ "action.parameters[0] < 1000.00; " + "environment.ipAddress =~ \"" + IPADDRESS_PATTERN + "\";"
				+ "environment.year >= 2016;";
		// @formatter:on
        assertThat("permit all did not evaluate to permit",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void permitAll() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit";
        assertThat("permit all did not evaluate to permit",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void denyAll() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" deny";
        assertThat("deny all did not evaluate to deny",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(DENY));
    }

    @Test
    public void permitFalse() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit false";
        assertThat("false in target did not lead to not_applicable result",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(NOT_APPLICABLE));
    }

    @Test
    public void permitNotFalse() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !false";
        assertThat("!false in target did not lead to permit result",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void permitNotTrue() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !true";
        assertThat("!true in target did not lead to NOT_APPLICABLE result",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(NOT_APPLICABLE));
    }

    @Test
    public void permitIllegalNot() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !\"string\"";
        assertThat("type mismatch in boolean negation not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void targetNotBoolean() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 123";
        assertThat("non-boolean result in target blovk not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void targetArithmeticNegation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit -(123) == -123";
        assertThat("arithmetic negation did not work",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void targetIllegalArithmeticNegation()
            throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit -null == -123";
        assertThat("arithmetic type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void permitTrue() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true";
        assertThat("true in target did not lead to permit result",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void numbersNoTypeMismatchCase() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 4 == (2*2)";
        assertThat("arithmetic operation with two numbers was not accepted",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void numbersTypeMismatchCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 4 == (\"x\"*2)";
        assertThat("type mismatch in arithmetic operation not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void numbersTypeMismatchCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 4 == (2*\"x\")";
        assertThat("type mismatch in arithmetic operation not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void booleanNoTypeMismatchCase() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true || false";
        assertThat("boolean operation with two booleans was accepted",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void booleanTypeMismatchCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true || 4";
        assertThat("type mismatch in boolean operation not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void booleanTypeMismatchCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 4 || false";
        assertThat("type mismatch in boolean operation not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void minusOperation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 10 == (200 - 190)";
        assertThat("minus operation not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void divOperation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 10 == (200 / 20)";
        assertThat("dividion not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void plusOperation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 250 == (120 + 130)";
        assertThat("addition not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void plusOperationConcatStrings() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"test1test2\" == (\"test1\" + \"test2\")";
        assertThat("concatenation not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void multiOperation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 250 == (20 * 12.5)";
        assertThat("multiplication not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void andOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true && true";
        assertThat("&& not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void andOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !(true && false)";
        assertThat("&& not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void andOperationCase3() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !(false && true)";
        assertThat("&& not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void orOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true || true";
        assertThat("|| not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void orOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true || false";
        assertThat("|| not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void orOperationCase3() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit false || true";
        assertThat("|| not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void orOperationCase4() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !(false || false)";
        assertThat("|| not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void lessOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 5 < 500e2";
        assertThat("< not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void lessOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !(5 < 5)";
        assertThat("< not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void lessEqualOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 5 <= 5";
        assertThat("<= not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void lessEqualOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit ! (6 <= 5)";
        assertThat("<= not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void moreOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 5 > 4 ";
        assertThat("> not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void moreOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit !(4 > 5)";
        assertThat("> not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void moreEqualOperationCase1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit 5 >= 5";
        assertThat(">= not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void moreEqualOperationCase2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit ! (5 >= 6)";
        assertThat(">= not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void regexMatch() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"123-45-6789\" =~ \"^(\\\\d{3}-?\\\\d{2}-?\\\\d{4})$\"";
        assertThat("regex not evaluated correctly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void regexMatchTypeMismatch1() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit true =~ \"^(\\\\d{3}-?\\\\d{2}-?\\\\d{4})$\"";
        assertThat("regex type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void regexMatchTypeMismatch2() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"a string\" =~ 6432 ";
        assertThat("regex type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void regexSyntaxError() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"a string\" =~ \"^(10\\\\.10\\\\.10.*$\" ";
        assertThat("regex type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void regexMatchTypeMismatch3() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit { \"key\" : \"value\" } =~ 6432 ";
        assertThat("regex type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test(expected = PolicyEvaluationException.class)
    public void syntaxError() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
        INTERPRETER.parse(policyDefinition);
    }

    @Test
    public void syntaxError2() throws PolicyEvaluationException { // XXXX
        String policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
        assertThat("regex type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void matches() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true";
        SAPL policy = INTERPRETER.parse(policyDefinition);
        assertTrue("match method fails", INTERPRETER.matches(requestObject,
                policy, functionCtx, null));
    }

    @Test
    public void evaluateEmptyBody() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true";
        assertThat("evaluateRule behaves unexpectedly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void evaluateRulesWorkingBodyTrue()
            throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
        assertThat("evaluateRule behaves unexpectedly",
                INTERPRETER.evaluateRules(requestObject,
                        INTERPRETER.parse(policyDefinition), attributeCtx,
                        functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void evaluateWorkingBodyTrue() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true where true;";
        assertThat("evaluateRule behaves unexpectedly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void evaluateWorkingBodyFalse() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true where false;";
        assertThat("evaluateRule behaves unexpectedly",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(NOT_APPLICABLE));
    }

    @Test
    public void evaluateWorkingBodyError() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit subject.isActive == true where 4 && true;";
        assertThat("evaluateRule behaves unexpectedly",
                INTERPRETER.evaluateRules(requestObject,
                        INTERPRETER.parse(policyDefinition), attributeCtx,
                        functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void nullLiteral() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit null == null";
        assertThat("null literal not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void evaluateArray() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit [1,2,3] == [1,2,3]";
        assertThat("array not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void evaluateValueDefinitionWithArrayAndIndexAccess()
            throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable[0] == 1;";
        assertThat("variable definition and index access failed",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }
    
    @Test
    public void echoAttributeFinder() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;";
        assertThat("external attribute finder not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void attributeFinderError() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where var variable = [1,2,3]; variable.\"x:y:z\" == variable;";
        assertThat("external attribute finder not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void keyAccessTypeMismatch() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"string\".key";
        assertThat("type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void indexAccessTypeMismatch() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"string\"[5]";
        assertThat("type mismatch not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void malformedAttributeName() throws PolicyEvaluationException {
        String badURI = ">sdfsdfj<";
        String policyDefinition = "policy \"test\" permit where val variable := [1,2,3]; variable.\""
                + badURI + "\" == variable;";
        assertThat("illegal attribute name not detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void statementNotBoolean() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where null;";
        assertThat("non boolean statement not working as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }
    
    @Test
    public void valueRedefinition() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where var test=null; var test=2;";
        assertThat("redefinition of value was not allowed",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void externalAttributeInTarget() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit \"xxx\".\"x:y:z\"";
        assertThat("external attribute finder was allowed in target",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void unboundVariablet() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit variable";
        assertThat("external attribute finder was allowed in target",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void functionCall() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";";
        assertThat("external attribute finder not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void functionCallImport() throws PolicyEvaluationException {
        String policyDefinition = "import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";";
        assertThat("external attribute finder not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(permitUnfiltered));
    }

    @Test
    public void functionCallError() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where append(null) == \"ab\";";
        assertThat("external attribute finder not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void functionCallIllegalName() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit where append[0](\"a\",\"b\") == \"ab\";";
        assertThat("illegal function name not properly detected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(INDETERMINATE));
    }

    @Test
    public void transformation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit transform null";
        assertEquals("transformation not evaluated as expected",
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getResource(),
                Optional.of(JsonNodeFactory.instance.nullNode()));
    }
    
    @Test
    public void illegalObligation() throws PolicyEvaluationException {
        String policyDefinition = "policy \"test\" permit obligation \"a\" > 5";
        assertEquals("error in obligation evaluation should evaluate to indeterminate",
        		Decision.INDETERMINATE,
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getDecision());
    }
    
    @Test
    public void importWildcard() throws PolicyEvaluationException {
        String policyDefinition = "import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");";
        assertEquals("wildcard import not working",
        		Decision.PERMIT,
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getDecision());
    }
    
    @Test
    public void importLibrary() throws PolicyEvaluationException {
        String policyDefinition = "import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");";
        assertEquals("library import with alias not working",
        		Decision.PERMIT,
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getDecision());
    }
    
    @Test
    public void importMultiple() throws PolicyEvaluationException {
        String policyDefinition = "import simple.* import filter.* policy \"test\" permit where var a = append(\"a\",\"b\") |- replace(null);";
        assertEquals("multiple imports not working",
        		Decision.PERMIT,
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getDecision());
    }
    
    @Test
    public void importConflict() throws PolicyEvaluationException {
        String policyDefinition = "import simple.append import simple.append policy \"test\" permit where var a = append(\"a\",\"b\") |- replace(null);";
        assertEquals("multiple imports with same simple name should cause an error",
        		Decision.INDETERMINATE,
                INTERPRETER.evaluate(requestObject, policyDefinition,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                        .getDecision());
    }
    
}
