/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.util;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.SaplCompiler;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class for testing combining algorithm implementations. Provides
 * convenience methods to reduce boilerplate in
 * test classes.
 */
@UtilityClass
public class CombiningAlgorithmTestUtil {

    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    /**
     * Creates a default authorization subscription for testing. Uses simple string
     * values for subject, action,
     * resource.
     *
     * @return authorization subscription with default test values
     */
    public static AuthorizationSubscription defaultSubscription() {
        return new AuthorizationSubscription(Value.of("subject"), Value.of("action"), Value.of("resource"),
                Value.UNDEFINED);
    }

    /**
     * Evaluates a policy set and returns the decision value.
     *
     * @param policySet
     * the policy set source code
     *
     * @return the decision value (ObjectValue containing decision, obligations,
     * etc.)
     *
     * @throws RuntimeException
     * if compilation fails
     */
    public static Value evaluatePolicySet(String policySet) {
        return evaluatePolicySet(policySet, defaultSubscription());
    }

    /**
     * Evaluates a policy set with a custom subscription and returns the decision
     * value.
     *
     * @param policySet
     * the policy set source code
     * @param subscription
     * the authorization subscription
     *
     * @return the decision value (ObjectValue containing decision, obligations,
     * etc.)
     *
     * @throws RuntimeException
     * if compilation fails
     */
    public static Value evaluatePolicySet(String policySet, AuthorizationSubscription subscription) {
        try {
            val functionBroker = new DefaultFunctionBroker();
            functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);

            val attributeRepo   = new InMemoryAttributeRepository(Clock.systemUTC());
            val attributeBroker = new CachingAttributeBroker(attributeRepo);
            attributeBroker.loadPolicyInformationPointLibrary(new TestUtil.TestPip());

            val context        = new CompilationContext(functionBroker, attributeBroker);
            val sapl           = PARSER.parse(policySet);
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val evalContext = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            return switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };
        } catch (SaplCompilerException | InitializationException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that a policy set evaluates to a specific decision.
     *
     * @param policySet
     * the policy set source code
     * @param expectedDecision
     * the expected decision
     */
    public static void assertDecision(String policySet, Decision expectedDecision) {
        assertDecision(evaluatePolicySet(policySet), expectedDecision);
    }

    /**
     * Asserts that a result value contains a specific decision.
     *
     * @param result
     * the result value from policy evaluation
     * @param expectedDecision
     * the expected decision
     */
    public static void assertDecision(Value result, Decision expectedDecision) {
        assertInstanceOf(ObjectValue.class, result);
        val decisionField = ((ObjectValue) result).get("decision");
        assertInstanceOf(TextValue.class, decisionField);
        assertEquals(expectedDecision.toString(), ((TextValue) decisionField).value());
    }

    /**
     * Asserts that a policy set evaluates to a specific decision with a specific
     * resource value.
     *
     * @param policySet
     * the policy set source code
     * @param expectedDecision
     * the expected decision
     * @param expectedResource
     * the expected resource value
     */
    public static void assertDecisionWithResource(String policySet, Decision expectedDecision, Value expectedResource) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        assertResource(result, expectedResource);
    }

    /**
     * Asserts that a result contains a specific resource value.
     *
     * @param result
     * the result value from policy evaluation
     * @param expectedResource
     * the expected resource value
     */
    public static void assertResource(Value result, Value expectedResource) {
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertEquals(expectedResource, resourceField);
    }

    /**
     * Asserts that a result contains specific obligations.
     *
     * @param result
     * the result value from policy evaluation
     * @param expectedObligationTypes
     * list of expected obligation type strings
     */
    public static void assertObligations(Value result, List<String> expectedObligationTypes) {
        assertInstanceOf(ObjectValue.class, result);
        val obligationsField = ((ObjectValue) result).get("obligations");
        assertInstanceOf(ArrayValue.class, obligationsField);
        val obligations = (ArrayValue) obligationsField;
        assertEquals(expectedObligationTypes.size(), obligations.size());

        for (int i = 0; i < expectedObligationTypes.size(); i++) {
            val obligation = obligations.get(i);
            assertInstanceOf(ObjectValue.class, obligation);
            val typeField = ((ObjectValue) obligation).get("type");
            assertInstanceOf(TextValue.class, typeField);
            assertEquals(expectedObligationTypes.get(i), ((TextValue) typeField).value());
        }
    }

    /**
     * Asserts that a result contains specific advice.
     *
     * @param result
     * the result value from policy evaluation
     * @param expectedAdviceTypes
     * list of expected advice type strings
     */
    public static void assertAdvice(Value result, List<String> expectedAdviceTypes) {
        assertInstanceOf(ObjectValue.class, result);
        val adviceField = ((ObjectValue) result).get("advice");
        assertInstanceOf(ArrayValue.class, adviceField);
        val advice = (ArrayValue) adviceField;
        assertEquals(expectedAdviceTypes.size(), advice.size());

        for (int i = 0; i < expectedAdviceTypes.size(); i++) {
            val adviceItem = advice.get(i);
            assertInstanceOf(ObjectValue.class, adviceItem);
            val typeField = ((ObjectValue) adviceItem).get("type");
            assertInstanceOf(TextValue.class, typeField);
            assertEquals(expectedAdviceTypes.get(i), ((TextValue) typeField).value());
        }
    }

    /**
     * Asserts that a result contains a transformed resource with a specific field
     * value.
     *
     * @param result
     * the result value from policy evaluation
     * @param fieldName
     * the name of the field in the resource object
     * @param expectedValue
     * the expected value for that field
     */
    public static void assertResourceField(Value result, String fieldName, Value expectedValue) {
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertInstanceOf(ObjectValue.class, resourceField);
        val fieldValue = ((ObjectValue) resourceField).get(fieldName);
        assertEquals(expectedValue, fieldValue);
    }

    /**
     * Asserts that a resource field contains a boolean value.
     *
     * @param result
     * the result value from policy evaluation
     * @param fieldName
     * the name of the boolean field
     * @param expectedBoolean
     * the expected boolean value
     */
    public static void assertResourceBoolean(Value result, String fieldName, boolean expectedBoolean) {
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertInstanceOf(ObjectValue.class, resourceField);
        val fieldValue = ((ObjectValue) resourceField).get(fieldName);
        assertInstanceOf(BooleanValue.class, fieldValue);
        assertEquals(expectedBoolean, ((BooleanValue) fieldValue).value());
    }

    /**
     * Asserts that a resource field contains a text value.
     *
     * @param result
     * the result value from policy evaluation
     * @param fieldName
     * the name of the text field
     * @param expectedText
     * the expected text value
     */
    public static void assertResourceText(Value result, String fieldName, String expectedText) {
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertInstanceOf(ObjectValue.class, resourceField);
        val fieldValue = ((ObjectValue) resourceField).get(fieldName);
        assertInstanceOf(TextValue.class, fieldValue);
        assertEquals(expectedText, ((TextValue) fieldValue).value());
    }
}
