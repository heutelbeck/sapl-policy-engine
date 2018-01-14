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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.time.Clock;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
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
import io.sapl.interpreter.pip.TestPIP;

public class DefaultSAPLResolutionEvaluatorUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections
            .unmodifiableMap(new HashMap<>());

    private AttributeContext attributeCtx;
    private FunctionContext functionCtx;

    @Before
    public void init() throws FunctionException, AttributeException {
        attributeCtx = new AnnotationAttributeContext();
        attributeCtx.loadPolicyInformationPoint(new TestPIP());
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(new SimpleFunctionLibrary());
        functionCtx.loadLibrary(new FilterFunctionLibrary());
        functionCtx.loadLibrary(
                new SimpleFilterFunctionLibrary(Clock.systemUTC()));
    }

    @Test
    public void evaluateRelativeResolution()
            throws PolicyEvaluationException,
            JsonParseException,
            JsonMappingException,
            IOException {
        assertThat("relative resolution not working as expected",
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        "policy \"test\" permit transform [true,false] :: @",
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(new Response(Decision.PERMIT,
                		Optional.of(MAPPER.readValue("[true,false]", JsonNode.class)), null,
                        null)));
    }

    @Test
    public void evaluateExpressionResolution()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = ( {\"key\":true} ).key; true == variable;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("evaluating expression resolution not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void wildcardStepOnArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0, 1, 2] |- { each @.* : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.arrayNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("wildcard step on array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void wildcardStepOnHelperArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key\":true}; [ true ] == variable.*.*;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("wildcard step on helper array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void recursiveKeyStep() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key\":\"value1\",\"test\":[{\"key\":\"value2\"}]}; [\"value1\",\"value2\"] == variable..key;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("recursive key step not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void recursiveIndexStep() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,[0,1],null,[2]]; [[0,1],1] == variable..[1];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("recursive index step not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[(0+1)];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("expression step on array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnArrayWrongIndex()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[(8+1)];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step on helper array with wrong index should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepNumberOnNoArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"teststring\"; 1 == variable[(2+1)];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step evaluating to number applied on no array should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnHelperArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[?(true)][(0+1)];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("expression step on helper array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnHelperArrayWrongIndex1()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[?(true)][(0+5)];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step on helper array with wrong index should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnHelperArrayWrongIndex2()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[?(true)][(1-(5))];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step on helper array with wrong index should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepOnArrayWithKey()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[?(true)][(\"test\")];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step on array which evaluates to string should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepWithKey() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key\":\"value\"}; \"value\" == variable[(\"key\")];");
        assertEquals(
                "expression step evaluating to string not working as expected",
                Decision.PERMIT,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
    }

    @Test
    public void expressionStepWithKeyOnArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"value\"; \"value\" == variable[(\"key\")];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step evaluating to string applied on no object should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepWithKeyOnHelperArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"value\"; \"value\" == variable[?(true)][(\"key\")];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "expression step evaluating to string applied to helper array should cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void expressionStepWithWrongType() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"value\"; \"value\" == variable[(true)];");
        Response expectedResponse = Response.indeterminate();
        assertEquals("expression step must evaluate to string or number",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void conditionStepOnHelperArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key1\":\"value1\",\"key2\":\"value2\"}; [\"value1\"] == variable.*[?(@==\"value1\")];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("condition step on helper array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void conditionStepOnArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [1,2,3]; [3] == variable[?(@>2)];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("condition step on array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void conditionStepObject() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key1\":1,\"key2\":2}; [2] == variable[?(@>1)];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("condition step on object not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void recursiveWildcardStep() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,{\"key\":\"value\"}]; [0,1,{\"key\":\"value\"},\"value\"] == variable..*;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("recursive wildcard step not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void wildcardStepOnObject() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform {\"key\" : \"value\"} |- { each @.* : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.objectNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("wildcard step on object not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexUnionStep() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [1,2] == variable[1,2];");
        assertEquals("index union step not working as expected",
                Decision.PERMIT,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
    }

    @Test
    public void indexUnionStepHelperArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [1,2] == variable[?(true)][1,2];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals(
                "index union step applied to helper array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexUnionStepNoArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"teststring\"; true == variable[0,1];");
        Response expectedResponse = Response.indeterminate();
        assertEquals("index union step applied on no array must cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void attributeUnionStep() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}; [\"value1\",\"value2\"] == variable[\"key1\",\"key2\"];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("key union step not working as expected", expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void attributeUnionStepHelperArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}; [\"value1\",\"value2\"] == variable[?(true)][\"key1\",\"key2\"];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "attribte union step applied to helper array must cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void attributeUnionStepNoObject() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"teststring\"; [] == variable[\"key1\",\"key2\"];");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "attribte union step applied to no object must cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void keyStepOnArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [{\"key\":\"value1\"},{\"key\":\"value2\"}]; [\"value1\",\"value2\"] == variable.key;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("key step on array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void keyStepOnList() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [{\"key\":\"value1\"},{\"key\":\"value2\"}]; [\"value1\",\"value2\"] == variable[0,1].key;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("key step on list not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void keyStepWithoutKey() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = {\"key\":\"value1\"}; variable.key2 == \"value2\";");
        Response expectedResponse = Response.indeterminate();
        assertEquals("key step without key not working", expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void wildcardStepOnNoArrayOrObject()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"teststring\"; variable.* == \"value2\";");
        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "wildcard step on no array or object should throw an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[1];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("index step on array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnArraySlicing() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [0,1,2] == variable[:3];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("index step on array with slicing not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnArraySlicingWithStep()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [0,2] == variable[0:3:2];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals(
                "index step on array with slicing with step not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnArraySlicingWithNegative()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [0,1] == variable[0:-1];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals(
                "index step on array with slicing with negative 'to' not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnArrayNegative() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 2 == variable[-1];");
        assertEquals(
                "index step on array with negative index not working as expected",
                Decision.PERMIT,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
    }

    @Test
    public void indexStepOnHelperArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 1 == variable[?(true)][1];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("index step on helper array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void indexStepOnHelperArrayNegative()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; 2 == variable[?(true)][-1];");
        assertEquals(
                "index step on helper array with negative index not working as expected",
                Decision.PERMIT,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
    }

    @Test
    public void indexStepOnHelperArraySlicing()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = [0,1,2]; [0,1,2] == variable[?(true)][:3];");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals(
                "index step on helper array with slicing not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void attributeFinderInHead() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse("policy \"test\" permit \"test\".<test>");
        Response expectedResponse = Response.indeterminate();
        assertEquals("attribute finder not in body must cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void attributeFinderError() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"test\".<not.an.attribute.finder>;");
        Response expectedResponse = Response.indeterminate();
        assertEquals("attribute finder that does not exist msut cause an error",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void attributeFinderWorking() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"test\".<sapl.pip.test.echo>; variable == \"test\";");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("attribute finder does not work as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void attributeFinderImport() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "import sapl.pip.test.echo policy \"test\" permit where var variable = \"test\".<echo>; variable == \"test\";");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("imports for attribute finders do not work as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void attributeFinderImportWildcard() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "import sapl.pip.test.* policy \"test\" permit where var variable = \"test\".<echo>; variable == \"test\";");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("wildcard imports for attribute finders do not work as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void attributeFinderVariable() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var var1 = true; var var2 = \"var1\".<sapl.pip.test.someVariableOrNull>; var2 == true;");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("attribute finder does not correctly access variables",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void blacken() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"test\" |- filter.blacken; variable == \"XXXX\";");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("blacken does not work as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }
    
    @Test
    public void blacken2() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"test\" |- filter.blacken(1,2); variable == \"tXst\";");
        assertEquals("blacken does not work as expected",
                Decision.PERMIT,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
    }
    
    @Test
    public void blacken3() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit where var variable = \"test\" |- filter.blacken(1,0,\"*\"); variable == \"t***\";");
        Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), null,
                null);
        assertEquals("blacken does not work as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

}
