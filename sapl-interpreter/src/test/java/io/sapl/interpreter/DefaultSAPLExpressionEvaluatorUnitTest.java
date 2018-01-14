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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

public class DefaultSAPLExpressionEvaluatorUnitTest {

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
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(new SimpleFunctionLibrary());
        functionCtx.loadLibrary(new FilterFunctionLibrary());
        functionCtx.loadLibrary(
                new SimpleFilterFunctionLibrary(Clock.systemUTC()));
    }

    @Test
    public void evaluateElementOfTrue() 
    		throws PolicyEvaluationException 
    {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit 1 in [4,1,true]");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "operator 'in' not working as expected",
                Decision.PERMIT, decision);
    }
    
    @Test
    public void evaluateElementOfFalse() 
    		throws PolicyEvaluationException 
    {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit 6 in [4,1,true]");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "operator 'in' not working as expected",
                Decision.NOT_APPLICABLE, decision);
    }
    
    @Test
    public void evaluateElementOfObjectOrder() 
    		throws PolicyEvaluationException 
    {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit {\"a\":1, \"b\":2} in [{\"b\":2,\"a\":1}]");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "operator 'in' not working as expected",
                Decision.PERMIT, decision);
    }
    
    @Test
    public void evaluateElementOfNoArray() 
    		throws PolicyEvaluationException 
    {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit 1 in 1");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "operator 'in' not working as expected",
                Decision.INDETERMINATE, decision);
    }
    
    @Test
    public void evaluateTransformation()
            throws PolicyEvaluationException,
            JsonParseException,
            JsonMappingException,
            IOException {
        assertThat("simple transformation not working as expected",
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        "policy \"test\" permit transform \"teststring\"",
                        attributeCtx, functionCtx, SYSTEM_VARIABLES),
                equalTo(new Response(Decision.PERMIT,
                        Optional.of(MAPPER.readValue("\"teststring\"", JsonNode.class)),
                        null, null)));
    }

    @Test
    public void evaluateFilter() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform \"teststring\" |- filter.replace(null)");
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(JsonNodeFactory.instance.nullNode()), null, null);

        assertEquals("simple filter not working as expected", expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateMultiFilter() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform \"teststring\" |- { @ : filter.replace(null) }");
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(JsonNodeFactory.instance.nullNode()), null, null);

        assertEquals("multiple filter not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterHelperArrayWithoutEach()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [1,2,3] |- { @[?(true)] : filter.replace(null) }");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "error not detected: application of filter on helper array only allowed with 'each'",
                Decision.INDETERMINATE, decision);
    }

    @Test
    public void evaluateFilterNoArrayWithEach()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform \"teststring\" |- { each @ : filter.replace(null) }");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "error not detected: application of filter with 'each' requires an array",
                Decision.INDETERMINATE, decision);
    }

    @Test
    public void evaluateFilterRemoveRoot() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform \"teststring\" |- remove");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals(
                "error not detected: removing the main value is not allowed",
                Decision.INDETERMINATE, decision);
    }

    @Test
    public void evaluateFilterRemoveObject() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform { \"key\" : \"value\" } |- { @.key : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.objectNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("removing key from object not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterRemoveArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform { \"key\" : [0,1,2] } |- { @.key : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.objectNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("removing array not working as expected", expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterRemoveArrayElement()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0] |- { @[0] : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.arrayNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("removing index from array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterRemoveEachArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0, 1, 2] |- { each @ : remove }");
        JsonNode expectedResource = JsonNodeFactory.instance.arrayNode();
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("removing each element from array not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterRemoveEachNoArray()
            throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform { \"key\" : \"value\" } |- { each @.key : remove }");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals("error not detected: removing 'each' value requires array",
                Decision.INDETERMINATE, decision);
    }

    @Test
    public void evaluateFilterToList() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0, 1, 2] |- { each @[?(true)] : filter.replace(null) }");
        ArrayNode expectedResource = JsonNodeFactory.instance.arrayNode();
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());

        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals(
                "filtering helper array with 'each' not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterToArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0,1,2] |- { each @ : filter.replace(null) }");
        ArrayNode expectedResource = JsonNodeFactory.instance.arrayNode();
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());

        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("filtering array with 'each' not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterRemoveToList() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0,1,2] |- { each @[?(true)] : remove }");
        ArrayNode expectedResource = JsonNodeFactory.instance.arrayNode();

        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals(
                "filtering with remove function on list with 'each' not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterObject() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform { \"key\" : \"value\" } |- { @.key : filter.replace(null) }");
        ObjectNode expectedResource = JsonNodeFactory.instance.objectNode();
        expectedResource.set("key", JsonNodeFactory.instance.nullNode());
        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);
        assertEquals("filtering key from object not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateSubtemplate() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform [0,\"1\",true] :: null");
        ArrayNode expectedResource = JsonNodeFactory.instance.arrayNode();
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());
        expectedResource.add(JsonNodeFactory.instance.nullNode());

        Response expectedResponse = new Response(Decision.PERMIT,
        		Optional.of(expectedResource), null, null);

        assertEquals("simple subtemplate not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateSubtemplateNoArray() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER
                .parse("policy \"test\" permit transform \"test\" :: null");

        Response expectedResponse = Response.indeterminate();
        assertEquals(
                "subtemplate applied to no array value not working as expected",
                expectedResponse,
                INTERPRETER.evaluate(new Request(null, null, null, null),
                        policy, attributeCtx, functionCtx, SYSTEM_VARIABLES));
    }

    @Test
    public void evaluateFilterWrongFunction() throws PolicyEvaluationException {
        SAPL policy = INTERPRETER.parse(
                "policy \"test\" permit transform \"teststring\" |- nofunction");
        Decision decision = INTERPRETER
                .evaluate(new Request(null, null, null, null), policy,
                        attributeCtx, functionCtx, SYSTEM_VARIABLES)
                .getDecision();
        assertEquals("error not detected: wrong filter function",
                Decision.INDETERMINATE, decision);
    }

}
